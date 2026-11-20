package ru.mirea.taxi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mirea.taxi.dto.PriceEstimateDto;
import ru.mirea.taxi.dto.RouteDto;
import ru.mirea.taxi.service.OsrmRoutingService;
import ru.mirea.taxi.service.PricingService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routes")
@CrossOrigin(origins = "*")
public class RoutingController {

    private final OsrmRoutingService routingService;
    private final PricingService pricingService;

    public RoutingController(OsrmRoutingService routingService, PricingService pricingService) {
        this.routingService = routingService;
        this.pricingService = pricingService;
    }

    @GetMapping("/geocode")
    public ResponseEntity<Map<String, String>> geocode(
            @RequestParam double lat,
            @RequestParam double lon) {
        String address = routingService.reverseGeocode(lat, lon);
        return ResponseEntity.ok(Map.of("address", address));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchPlaces(
            @RequestParam String query) {
        return ResponseEntity.ok(routingService.searchPlaces(query));
    }

    @GetMapping
    public ResponseEntity<RouteDto> getRoute(
            @RequestParam double lat1,
            @RequestParam double lon1,
            @RequestParam double lat2,
            @RequestParam double lon2,
            @RequestParam(defaultValue = "false") boolean background) {
        return ResponseEntity.ok(routingService.calculateRoute(
                List.of(List.of(lat1, lon1), List.of(lat2, lon2)), background));
    }

    @GetMapping("/estimate")
    public ResponseEntity<PriceEstimateDto> getEstimate(
            @RequestParam double lat1,
            @RequestParam double lon1,
            @RequestParam double lat2,
            @RequestParam double lon2,
            @RequestParam(defaultValue = "COMFORT") String tariff,
            @RequestParam(defaultValue = "1.0") double surge) {
        RouteDto route = routingService.calculateRoute(lat1, lon1, lat2, lon2);
        PriceEstimateDto estimate = pricingService.calculatePrice(tariff, route.getDistanceMeters(), route.getDurationSeconds(), surge);
        return ResponseEntity.ok(estimate);
    }

    @GetMapping("/estimate-price")
    public ResponseEntity<PriceEstimateDto> getEstimateByDistance(
            @RequestParam int distanceMeters,
            @RequestParam int durationSeconds,
            @RequestParam(defaultValue = "COMFORT") String tariff,
            @RequestParam(defaultValue = "1.0") double surge) {
        PriceEstimateDto estimate = pricingService.calculatePrice(tariff, distanceMeters, durationSeconds, surge);
        return ResponseEntity.ok(estimate);
    }
}
