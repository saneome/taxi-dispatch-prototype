package ru.mirea.taxi.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ru.mirea.taxi.model.Driver;
import ru.mirea.taxi.repository.DriverRepository;

import java.util.ArrayList;
import java.util.List;

/** Places the educational fleet on real roads near the current search area. */
@Service
public class DemoFleetService {
    private final DriverRepository drivers;
    private final OsrmRoutingService routing;

    public DemoFleetService(DriverRepository drivers, OsrmRoutingService routing) {
        this.drivers = drivers;
        this.routing = routing;
    }

    public synchronized void ensureLocalFleet(double lat, double lon, double radiusKm) {
        validateSearch(lat, lon, radiusKm);
        List<Driver> available = drivers.findByStatus("AVAILABLE");
        int target = Math.min(8, available.size());
        long localCount = available.stream().filter(d -> isNear(d, lat, lon, radiusKm)).count();
        if (target == 0 || localCount >= target) return;

        // One Directions call supplies road geometry for the whole demo fleet.
        // There is no separate snapping request and no list of supported cities.
        double offset = Math.min(1.2, radiusKm * 0.4) / 111.32;
        double lonOffset = offset / Math.max(0.1, Math.cos(Math.toRadians(lat)));
        var road =
                routing.calculateRoute(
                        List.of(
                                List.of(lat, lon),
                                List.of(
                                        Math.min(89.9, lat + offset),
                                        normalizeLon(lon + lonOffset)),
                                List.of(
                                        Math.max(-89.9, lat - offset),
                                        normalizeLon(lon - lonOffset))),
                        false);
        var positions =
                road.getCoordinates().stream()
                        .filter(p -> distanceKm(lat, lon, p.get(0), p.get(1)) <= radiusKm)
                        .distinct()
                        .toList();
        if (positions.isEmpty()) return;

        List<Driver> relocated = new ArrayList<>();
        var distant = available.stream().filter(d -> !isNear(d, lat, lon, radiusKm)).toList();
        for (int i = 0; i < distant.size(); i++) {
            Driver driver = distant.get(i);
            var point =
                    positions.get(
                            Math.min(
                                    positions.size() - 1,
                                    (int) ((i + 0.5) * positions.size() / distant.size())));
            driver.setCurrentLat(point.get(0));
            driver.setCurrentLon(point.get(1));
            relocated.add(driver);
        }
        drivers.saveAll(relocated);
    }

    public static void validateSearch(double lat, double lon, double radiusKm) {
        if (!Double.isFinite(lat)
                || !Double.isFinite(lon)
                || Math.abs(lat) > 90
                || Math.abs(lon) > 180
                || !Double.isFinite(radiusKm)
                || radiusKm < 1
                || radiusKm > 10)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid search coordinates or radius (1-10 km)");
    }

    public static boolean isNear(Driver driver, double lat, double lon, double radiusKm) {
        return driver.getCurrentLat() != null
                && driver.getCurrentLon() != null
                && distanceKm(lat, lon, driver.getCurrentLat(), driver.getCurrentLon()) <= radiusKm;
    }

    private static double normalizeLon(double lon) {
        return ((lon + 540) % 360) - 180;
    }

    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double a =
                Math.pow(Math.sin(Math.toRadians(lat2 - lat1) / 2), 2)
                        + Math.cos(Math.toRadians(lat1))
                                * Math.cos(Math.toRadians(lat2))
                                * Math.pow(Math.sin(Math.toRadians(lon2 - lon1) / 2), 2);
        return 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
    }
}
