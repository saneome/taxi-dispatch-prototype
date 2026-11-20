package ru.mirea.taxi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

import ru.mirea.taxi.dto.RouteDto;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Historical name retained for compatibility; all road requests go to Mapbox. */
@Service
public class OsrmRoutingService {
    private static final Logger log = LoggerFactory.getLogger(OsrmRoutingService.class);
    private final String token, yandexGeocoderApiKey;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final long intervalMs;
    private final int dailyLimit;
    private final Map<String, Job> pending = new HashMap<>();

    private record Cached(RouteDto route, long expires) {}

    private final LinkedHashMap<String, Cached> cache = new LinkedHashMap<>(16, .75f, true);
    private final PriorityBlockingQueue<Job> queue = new PriorityBlockingQueue<>();
    private final Thread worker;
    private volatile boolean stopped;
    private boolean unauthorized;
    private long sequence, nextRequestAt, retryAt;
    private LocalDate day = LocalDate.now(ZoneOffset.UTC);
    private int count;

    private static class Job implements Comparable<Job> {
        final String key;
        final long order;
        boolean background;
        final CompletableFuture<RouteDto> future = new CompletableFuture<>();

        Job(String key, boolean background, long order) {
            this.key = key;
            this.background = background;
            this.order = order;
        }

        public int compareTo(Job j) {
            int p = Boolean.compare(background, j.background);
            return p != 0 ? p : Long.compare(order, j.order);
        }
    }

    public OsrmRoutingService(
            @Value("${mapbox.access-token:}") String token,
            @Value("${mapbox.timeout-ms:10000}") int timeout,
            @Value("${yandex.geocoder.api-key:}") String geocoder,
            @Value("${mapbox.interval-ms:250}") long interval,
            @Value("${mapbox.daily-request-limit:2500}") int limit) {
        this.token = token == null ? "" : token.trim();
        yandexGeocoderApiKey = geocoder == null ? "" : geocoder.trim();
        intervalMs = Math.max(0, interval);
        dailyLimit = Math.max(0, limit);
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        restTemplate = new RestTemplate(factory);
        worker = new Thread(this::runQueue, "mapbox-directions");
        worker.setDaemon(true);
        worker.start();
    }

    public RouteDto calculateRoute(double lat1, double lon1, double lat2, double lon2) {
        return calculateRoute(List.of(List.of(lat1, lon1), List.of(lat2, lon2)), false);
    }

    /** Application coordinates are [latitude, longitude], including intermediate stops. */
    public RouteDto calculateRoute(List<List<Double>> points, boolean background) {
        if (points == null || points.size() < 2 || points.size() > 25)
            throw error(HttpStatus.BAD_REQUEST, "Expected 2-25 route points");
        var key = new StringJoiner(";");
        for (var p : points) {
            if (p == null
                    || p.size() != 2
                    || p.get(0) == null
                    || p.get(1) == null
                    || !Double.isFinite(p.get(0))
                    || !Double.isFinite(p.get(1))
                    || Math.abs(p.get(0)) > 90
                    || Math.abs(p.get(1)) > 180)
                throw error(HttpStatus.BAD_REQUEST, "Invalid coordinates");
            key.add(p.get(1) + "," + p.get(0));
        }
        Job job;
        synchronized (this) {
            var hit = cache.get(key.toString());
            if (hit != null && hit.expires > System.currentTimeMillis()) return hit.route;
            cache.remove(key.toString());
            checkAvailable();
            job = pending.get(key.toString());
            if (job == null) {
                job = new Job(key.toString(), background, sequence++);
                pending.put(job.key, job);
                queue.add(job);
            } else if (!background && job.background && queue.remove(job)) {
                job.background = false;
                queue.add(job);
            }
        }
        try {
            return job.future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Route request interrupted");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ResponseStatusException r) throw r;
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Road routing unavailable");
        }
    }

    private synchronized void checkAvailable() {
        var today = LocalDate.now(ZoneOffset.UTC);
        if (!today.equals(day)) {
            day = today;
            count = 0;
        }
        if (stopped || token.isBlank() || unauthorized)
            throw error(
                    HttpStatus.SERVICE_UNAVAILABLE, "Configure Mapbox token and restart server");
        if (count >= dailyLimit)
            throw error(HttpStatus.TOO_MANY_REQUESTS, "Daily route limit reached");
        if (System.currentTimeMillis() < retryAt)
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Road routing paused; retry later");
    }

    private void runQueue() {
        while (!stopped)
            try {
                long delay = nextRequestAt - System.currentTimeMillis();
                if (delay > 0) Thread.sleep(delay);
                Job job = queue.take();
                try {
                    synchronized (this) {
                        checkAvailable();
                        count++;
                        nextRequestAt = System.currentTimeMillis() + intervalMs;
                    }
                    RouteDto route = request(job.key);
                    synchronized (this) {
                        cache.put(
                                job.key, new Cached(route, System.currentTimeMillis() + 86400000));
                        while (cache.size() > 500) cache.remove(cache.keySet().iterator().next());
                    }
                    synchronized (this) {
                        pending.remove(job.key);
                    }
                    job.future.complete(route);
                } catch (Exception e) {
                    synchronized (this) {
                        pending.remove(job.key);
                    }
                    job.future.completeExceptionally(
                            e instanceof ResponseStatusException
                                    ? e
                                    : error(
                                            HttpStatus.SERVICE_UNAVAILABLE,
                                            "Road routing unavailable"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
    }

    private RouteDto request(String coordinates) {
        String radiuses =
                String.join(";", Collections.nCopies(coordinates.split(";").length, "2000"));
        URI uri =
                URI.create(
                        "https://api.mapbox.com/directions/v5/mapbox/driving/"
                                + coordinates
                                + "?overview=full&geometries=geojson&steps=false&alternatives=false&radiuses="
                                + radiuses
                                + "&access_token="
                                + URLEncoder.encode(token, StandardCharsets.UTF_8));
        try {
            JsonNode root = objectMapper.readTree(restTemplate.getForObject(uri, String.class));
            if (!"Ok".equals(root.path("code").asText()) || root.path("routes").isEmpty())
                throw error(HttpStatus.UNPROCESSABLE_ENTITY, "No road route found");
            JsonNode route = root.path("routes").get(0);
            List<List<Double>> geometry = new ArrayList<>(), waypoints = new ArrayList<>();
            for (var p : route.path("geometry").path("coordinates"))
                geometry.add(List.of(p.get(1).asDouble(), p.get(0).asDouble()));
            if (geometry.size() < 2)
                throw error(HttpStatus.UNPROCESSABLE_ENTITY, "No road geometry");
            List<RouteDto.Leg> legs = new ArrayList<>();
            double meters = 0, seconds = 0;
            for (var leg : route.path("legs")) {
                double d = leg.path("distance").asDouble(), t = leg.path("duration").asDouble();
                meters += d;
                seconds += t;
                legs.add(new RouteDto.Leg(d / 1000, t / 60));
            }
            if (legs.isEmpty()) throw error(HttpStatus.BAD_GATEWAY, "Incomplete Mapbox response");
            for (var wp : root.path("waypoints")) {
                var p = wp.path("location");
                waypoints.add(List.of(p.get(1).asDouble(), p.get(0).asDouble()));
            }
            var result =
                    new RouteDto(
                            (int) Math.round(meters),
                            (int) Math.round(seconds),
                            List.copyOf(geometry),
                            "Mapbox Directions");
            result.setLegs(List.copyOf(legs));
            result.setWaypoints(List.copyOf(waypoints));
            return result;
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            synchronized (this) {
                if (status == 401 || status == 403) unauthorized = true;
                else if (status == 429) retryAt = retryAfter(e.getResponseHeaders());
                else if (status >= 500) retryAt = System.currentTimeMillis() + 30000;
            }
            // Provider exceptions contain the secret URL: never log or attach them.
            throw error(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    status == 401 || status == 403
                            ? "Mapbox token rejected; fix token and restart"
                            : "Mapbox route unavailable (HTTP " + status + ")");
        } catch (ResourceAccessException e) {
            synchronized (this) {
                retryAt = System.currentTimeMillis() + 30000;
            }
            throw error(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Mapbox network failure; paused for 30 seconds");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw error(HttpStatus.BAD_GATEWAY, "Invalid Mapbox response");
        }
    }

    private long retryAfter(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst("Retry-After");
        try {
            return System.currentTimeMillis() + Math.max(1, Long.parseLong(value)) * 1000;
        } catch (Exception e) {
            try {
                return Math.max(
                        System.currentTimeMillis() + 1000,
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                                .toInstant()
                                .toEpochMilli());
            } catch (Exception ignored) {
                return System.currentTimeMillis() + 30000;
            }
        }
    }

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    @PreDestroy
    public synchronized void close() {
        stopped = true;
        worker.interrupt();
        pending.values()
                .forEach(
                        j ->
                                j.future.completeExceptionally(
                                        error(HttpStatus.SERVICE_UNAVAILABLE, "Server stopping")));
        pending.clear();
        queue.clear();
    }

    public String reverseGeocode(double lat, double lon) {
        if (!yandexGeocoderApiKey.isBlank()) {
            String yandexUrl =
                    String.format(
                            Locale.US,
                            "https://geocode-maps.yandex.ru/1.x/?apikey=%s&geocode=%.6f,%.6f&format=json&lang=ru_RU&results=1",
                            yandexGeocoderApiKey,
                            lon,
                            lat);
            try {
                ResponseEntity<String> response =
                        restTemplate.getForEntity(yandexUrl, String.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode featureMembers =
                            root.path("response").path("GeoObjectCollection").path("featureMember");
                    if (featureMembers.isArray() && featureMembers.size() > 0) {
                        String text =
                                featureMembers
                                        .get(0)
                                        .path("GeoObject")
                                        .path("metaDataProperty")
                                        .path("GeocoderMetaData")
                                        .path("text")
                                        .asText("");
                        if (!text.isBlank()) {
                            return text;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Yandex reverse geocode failed: {}", e.getMessage());
            }
        }

        String url =
                String.format(
                        Locale.US,
                        "https://nominatim.openstreetmap.org/reverse?lat=%.6f&lon=%.6f&format=json&accept-language=ru",
                        lat,
                        lon);
        try {
            org.springframework.http.HttpHeaders headers =
                    new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "TaxiMireaApp/1.0");
            org.springframework.http.HttpEntity<Void> entity =
                    new org.springframework.http.HttpEntity<>(headers);
            ResponseEntity<String> response =
                    restTemplate.exchange(
                            url, org.springframework.http.HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode addr = root.path("address");
                String city =
                        addr.has("city")
                                ? addr.path("city").asText()
                                : addr.has("town")
                                        ? addr.path("town").asText()
                                        : addr.has("village")
                                                ? addr.path("village").asText()
                                                : addr.has("state")
                                                        ? addr.path("state").asText()
                                                        : "";
                String road = addr.path("road").asText("");
                String house = addr.path("house_number").asText("");
                if (!city.isEmpty() && !road.isEmpty()) {
                    return city + ", " + road + (house.isEmpty() ? "" : ", " + house);
                }
                String display = root.path("display_name").asText("");
                if (!display.isEmpty()) {
                    String[] parts = display.split(",");
                    if (parts.length >= 3) {
                        return parts[0].trim() + ", " + parts[1].trim() + ", " + parts[2].trim();
                    }
                    return display;
                }
            }
        } catch (Exception e) {
            log.warn("Reverse geocode failed: {}", e.getMessage());
        }
        return String.format(Locale.US, "Точка (%.4f, %.4f)", lat, lon);
    }

    /** Прямой поиск адресов/мест по строке запроса */
    public List<Map<String, Object>> searchPlaces(String query) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (query == null || query.trim().length() < 2) {
            return results;
        }
        String cleanQuery = query.trim();

        if (!yandexGeocoderApiKey.isBlank()) {
            try {
                java.net.URI yandexUri =
                        java.net.URI.create(
                                String.format(
                                        Locale.US,
                                        "https://geocode-maps.yandex.ru/1.x/?apikey=%s&geocode=%s&format=json&lang=ru_RU&results=5",
                                        yandexGeocoderApiKey,
                                        java.net.URLEncoder.encode(
                                                cleanQuery,
                                                java.nio.charset.StandardCharsets.UTF_8)));
                ResponseEntity<String> response =
                        restTemplate.getForEntity(yandexUri, String.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode featureMembers =
                            root.path("response").path("GeoObjectCollection").path("featureMember");
                    if (featureMembers.isArray()) {
                        for (JsonNode item : featureMembers) {
                            JsonNode geoObj = item.path("GeoObject");
                            String name = geoObj.path("name").asText("");
                            String description = geoObj.path("description").asText("");
                            String pos = geoObj.path("Point").path("pos").asText("");
                            if (!pos.isBlank()) {
                                String[] parts = pos.split("\\s+");
                                if (parts.length == 2) {
                                    double lon = Double.parseDouble(parts[0]);
                                    double lat = Double.parseDouble(parts[1]);
                                    Map<String, Object> map = new HashMap<>();
                                    map.put("name", name);
                                    map.put("description", description);
                                    map.put("lat", lat);
                                    map.put("lon", lon);
                                    map.put(
                                            "fullAddress",
                                            description.isEmpty()
                                                    ? name
                                                    : description + ", " + name);
                                    results.add(map);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Yandex place search failed: {}", e.getMessage());
            }
        }

        if (results.isEmpty()) {
            try {
                java.net.URI nomUri =
                        java.net.URI.create(
                                String.format(
                                        Locale.US,
                                        "https://nominatim.openstreetmap.org/search?q=%s&format=json&limit=5&accept-language=ru&countrycodes=ru",
                                        java.net.URLEncoder.encode(
                                                cleanQuery,
                                                java.nio.charset.StandardCharsets.UTF_8)));
                org.springframework.http.HttpHeaders headers =
                        new org.springframework.http.HttpHeaders();
                headers.set("User-Agent", "TaxiMireaApp/1.0");
                org.springframework.http.HttpEntity<Void> entity =
                        new org.springframework.http.HttpEntity<>(headers);
                ResponseEntity<String> response =
                        restTemplate.exchange(
                                nomUri,
                                org.springframework.http.HttpMethod.GET,
                                entity,
                                String.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    if (root.isArray()) {
                        for (JsonNode item : root) {
                            String displayName = item.path("display_name").asText("");
                            double lat = Double.parseDouble(item.path("lat").asText("0"));
                            double lon = Double.parseDouble(item.path("lon").asText("0"));
                            if (lat != 0 && lon != 0) {
                                Map<String, Object> map = new HashMap<>();
                                String[] parts = displayName.split(",", 2);
                                map.put("name", parts[0].trim());
                                map.put("description", parts.length > 1 ? parts[1].trim() : "");
                                map.put("lat", lat);
                                map.put("lon", lon);
                                map.put("fullAddress", displayName);
                                results.add(map);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Nominatim place search failed: {}", e.getMessage());
            }
        }

        return results;
    }
}
