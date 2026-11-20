package ru.mirea.taxi;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import ru.mirea.taxi.service.OsrmRoutingService;

import java.util.*;
import java.util.concurrent.*;

@Timeout(10)
class OsrmRoutingServiceTest {
    OsrmRoutingService service;
    MockRestServiceServer server;

    void setup(int limit) {
        service = new OsrmRoutingService("pk.test-secret", 100, "", 0, limit);
        server =
                MockRestServiceServer.bindTo(
                                (RestTemplate)
                                        ReflectionTestUtils.getField(service, "restTemplate"))
                        .build();
    }

    @AfterEach
    void close() {
        if (service != null) service.close();
    }

    String response(int n) {
        StringJoiner points = new StringJoiner(",");
        for (int i = 0; i < n; i++) points.add("[104.3," + (52.2 + i * .00001) + "]");
        return "{\"code\":\"Ok\",\"routes\":[{\"geometry\":{\"coordinates\":["
                + points
                + "]},"
                + "\"legs\":[{\"distance\":9950.5,\"duration\":120.5}]}],"
                + "\"waypoints\":[{\"location\":[104.3,52.2]},{\"location\":[104.3,52.21]}]}";
    }

    @Test
    void fullGeometryUnitsWaypointsAndCache() {
        setup(2500);
        server.expect(
                        requestTo(
                                "https://api.mapbox.com/directions/v5/mapbox/driving/104.3,52.2;104.4,52.3?overview=full&geometries=geojson&steps=false&alternatives=false&radiuses=2000;2000&access_token=pk.test-secret"))
                .andRespond(withSuccess(response(350), MediaType.APPLICATION_JSON));
        var route = service.calculateRoute(52.2, 104.3, 52.3, 104.4);
        assertEquals(350, route.getCoordinates().size());
        assertEquals(List.of(52.2, 104.3), route.getCoordinates().get(0));
        assertEquals(9.9505, route.getLegs().get(0).distanceKm());
        assertEquals(120.5 / 60, route.getLegs().get(0).durationMinutes());
        assertEquals(List.of(52.21, 104.3), route.getWaypoints().get(1));
        assertSame(route, service.calculateRoute(52.2, 104.3, 52.3, 104.4));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void authenticationFailureStopsFurtherRequestsAndHidesToken(int status) {
        setup(2500);
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status)));
        var error =
                assertThrows(
                        ResponseStatusException.class, () -> service.calculateRoute(1, 2, 3, 4));
        assertFalse(error.toString().contains("pk.test-secret"));
        assertNull(error.getCause());
        assertThrows(ResponseStatusException.class, () -> service.calculateRoute(5, 6, 7, 8));
        server.verify();
    }

    @Test
    void rateLimitHonorsRetryAfter() {
        setup(2500);
        server.expect(anything())
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "120"));
        long before = System.currentTimeMillis();
        assertThrows(ResponseStatusException.class, () -> service.calculateRoute(1, 2, 3, 4));
        assertTrue((long) ReflectionTestUtils.getField(service, "retryAt") >= before + 120000);
        assertThrows(ResponseStatusException.class, () -> service.calculateRoute(5, 6, 7, 8));
        server.verify();
    }

    @Test
    void networkFailurePausesWithoutInventingRoad() {
        setup(2500);
        server.expect(anything()).andRespond(withException(new java.io.IOException("network")));
        long before = System.currentTimeMillis();
        assertThrows(ResponseStatusException.class, () -> service.calculateRoute(1, 2, 3, 4));
        assertTrue((long) ReflectionTestUtils.getField(service, "retryAt") >= before + 30000);
        assertThrows(ResponseStatusException.class, () -> service.calculateRoute(5, 6, 7, 8));
        server.verify();
    }

    @Test
    void noRoadIsAnExplicitError() {
        setup(2500);
        server.expect(anything())
                .andRespond(
                        withSuccess(
                                "{\"code\":\"NoRoute\",\"routes\":[]}",
                                MediaType.APPLICATION_JSON));
        var error =
                assertThrows(
                        ResponseStatusException.class, () -> service.calculateRoute(1, 2, 3, 4));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatusCode());
        server.verify();
    }

    @Test
    void dailyLimitStillAllowsCachedRoutes() {
        setup(1);
        server.expect(anything()).andRespond(withSuccess(response(2), MediaType.APPLICATION_JSON));
        var route = service.calculateRoute(1, 2, 3, 4);
        assertSame(route, service.calculateRoute(1, 2, 3, 4));
        assertEquals(
                HttpStatus.TOO_MANY_REQUESTS,
                assertThrows(
                                ResponseStatusException.class,
                                () -> service.calculateRoute(5, 6, 7, 8))
                        .getStatusCode());
        server.verify();
    }

    @Test
    void concurrentIdenticalRequestsShareOneCall() throws Exception {
        setup(2500);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        server.expect(anything())
                .andRespond(
                        request -> {
                            entered.countDown();
                            try {
                                assertTrue(release.await(3, TimeUnit.SECONDS));
                            } catch (InterruptedException e) {
                                throw new java.io.IOException(e);
                            }
                            return withSuccess(response(2), MediaType.APPLICATION_JSON)
                                    .createResponse(request);
                        });
        var first = CompletableFuture.supplyAsync(() -> service.calculateRoute(1, 2, 3, 4));
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        var second = CompletableFuture.supplyAsync(() -> service.calculateRoute(1, 2, 3, 4));
        release.countDown();
        assertSame(first.get(3, TimeUnit.SECONDS), second.get(3, TimeUnit.SECONDS));
        server.verify();
    }

    @Test
    void intermediateStopsHaveMatchingRadiiAndLegs() {
        setup(2500);
        server.expect(
                        requestTo(
                                org.hamcrest.Matchers.containsString(
                                        "104.3,52.2;104.4,52.3;104.5,52.4?")))
                .andExpect(
                        requestTo(org.hamcrest.Matchers.containsString("radiuses=2000;2000;2000")))
                .andRespond(
                        withSuccess(
                                response(2)
                                        .replace(
                                                "{\"distance\":9950.5,\"duration\":120.5}",
                                                "{\"distance\":1000,\"duration\":60},{\"distance\":2000,\"duration\":120}"),
                                MediaType.APPLICATION_JSON));
        var route =
                service.calculateRoute(
                        List.of(List.of(52.2, 104.3), List.of(52.3, 104.4), List.of(52.4, 104.5)),
                        false);
        assertEquals(2, route.getLegs().size());
        assertEquals(3000, route.getDistanceMeters());
        server.verify();
    }

    @Test
    void expiredCacheFetchesAgainAndUtcDayResetsLimit() throws Exception {
        setup(1);
        server.expect(anything()).andRespond(withSuccess(response(2), MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(response(2), MediaType.APPLICATION_JSON));
        var first = service.calculateRoute(1, 2, 3, 4);
        @SuppressWarnings("unchecked")
        var cache = (Map<String, Object>) ReflectionTestUtils.getField(service, "cache");
        var constructor =
                Class.forName("ru.mirea.taxi.service.OsrmRoutingService$Cached")
                        .getDeclaredConstructor(ru.mirea.taxi.dto.RouteDto.class, long.class);
        constructor.setAccessible(true);
        synchronized (service) {
            cache.put(
                    "2.0,1.0;4.0,3.0",
                    constructor.newInstance(first, System.currentTimeMillis() - 1));
        }
        ReflectionTestUtils.setField(
                service, "day", java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1));
        assertNotSame(first, service.calculateRoute(1, 2, 3, 4));
        server.verify();
    }

    @Test
    void foregroundOvertakesQueuedBackgroundAndRequestsAreSpaced() throws Exception {
        setup(2500);
        ReflectionTestUtils.setField(service, "intervalMs", 100L);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        List<Long> times = new CopyOnWriteArrayList<>();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("2.0,1.0;4.0,3.0?")))
                .andRespond(
                        request -> {
                            times.add(System.nanoTime());
                            entered.countDown();
                            try {
                                assertTrue(release.await(3, TimeUnit.SECONDS));
                            } catch (InterruptedException e) {
                                throw new java.io.IOException(e);
                            }
                            return withSuccess(response(2), MediaType.APPLICATION_JSON)
                                    .createResponse(request);
                        });
        server.expect(requestTo(org.hamcrest.Matchers.containsString("10.0,9.0;12.0,11.0?")))
                .andRespond(
                        request -> {
                            times.add(System.nanoTime());
                            return withSuccess(response(2), MediaType.APPLICATION_JSON)
                                    .createResponse(request);
                        });
        server.expect(requestTo(org.hamcrest.Matchers.containsString("6.0,5.0;8.0,7.0?")))
                .andRespond(
                        request -> {
                            times.add(System.nanoTime());
                            return withSuccess(response(2), MediaType.APPLICATION_JSON)
                                    .createResponse(request);
                        });
        var first = CompletableFuture.supplyAsync(() -> service.calculateRoute(1, 2, 3, 4));
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        var background =
                CompletableFuture.supplyAsync(
                        () ->
                                service.calculateRoute(
                                        List.of(List.of(5., 6.), List.of(7., 8.)), true));
        var foreground = CompletableFuture.supplyAsync(() -> service.calculateRoute(9, 10, 11, 12));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            synchronized (service) {
                if (((Map<?, ?>) ReflectionTestUtils.getField(service, "pending")).size() == 3)
                    break;
            }
            Thread.yield();
        }
        release.countDown();
        first.get(3, TimeUnit.SECONDS);
        foreground.get(3, TimeUnit.SECONDS);
        background.get(3, TimeUnit.SECONDS);
        assertTrue(times.get(2) - times.get(1) >= TimeUnit.MILLISECONDS.toNanos(85));
        server.verify();
    }
}
