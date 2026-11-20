package ru.mirea.taxi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mirea.taxi.dto.DriverDto;
import ru.mirea.taxi.model.Driver;
import ru.mirea.taxi.model.DriverVerification;
import ru.mirea.taxi.model.Vehicle;
import ru.mirea.taxi.repository.DriverRepository;
import ru.mirea.taxi.repository.VehicleRepository;
import ru.mirea.taxi.service.FgisVerificationService;
import ru.mirea.taxi.service.MatchingService;
import ru.mirea.taxi.service.PlateService;
import ru.mirea.taxi.service.DemoFleetService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/drivers")
@CrossOrigin(origins = "*")
public class DriverController {

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final FgisVerificationService fgisVerificationService;
    private final MatchingService matchingService;
    private final DemoFleetService demoFleetService;
    private final PlateService plateService;

    private final Map<Long, AssignedDriverPlate> activeDriverPlates = new ConcurrentHashMap<>();

    private static class AssignedDriverPlate {
        final String plate;
        final String regionKey;
        final double lat;
        final double lon;

        AssignedDriverPlate(String plate, String regionKey, double lat, double lon) {
            this.plate = plate;
            this.regionKey = regionKey;
            this.lat = lat;
            this.lon = lon;
        }
    }

    public DriverController(
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository,
            FgisVerificationService fgisVerificationService,
            MatchingService matchingService,
            DemoFleetService demoFleetService,
            PlateService plateService) {
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.fgisVerificationService = fgisVerificationService;
        this.matchingService = matchingService;
        this.demoFleetService = demoFleetService;
        this.plateService = plateService;
    }

    @GetMapping
    public ResponseEntity<List<DriverDto>> getAllDrivers(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(required = false, defaultValue = "3.0") double radiusKm) {
        if ((lat == null) != (lon == null)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Both coordinates are required");
        }
        if (lat != null) demoFleetService.ensureLocalFleet(lat, lon, radiusKm);
        List<Driver> drivers = driverRepository.findAll();

        // Если переданы координаты точки поиска (отправления)
        if (lat != null && lon != null) {
            // Подсчёт расстояния
            List<DriverWithDist> withDist = new ArrayList<>();
            for (Driver d : drivers) {
                if (d.getCurrentLat() != null && d.getCurrentLon() != null) {
                    double distKm = haversineKm(lat, lon, d.getCurrentLat(), d.getCurrentLon());
                    if (distKm <= radiusKm) withDist.add(new DriverWithDist(d, distKm));
                }
            }

            withDist.sort(Comparator.comparingDouble(a -> a.distKm));

            // Определяем макрорегион поиска такси для динамической генерации номеров
            String currentRegionKey = plateService.findNearestRegionKey(lat, lon);
            Set<String> usedPlatesInSession = new HashSet<>();
            for (AssignedDriverPlate ap : activeDriverPlates.values()) {
                usedPlatesInSession.add(ap.plate);
            }

            for (DriverWithDist e : withDist) {
                Long driverId = e.driver.getId();
                AssignedDriverPlate ap = activeDriverPlates.get(driverId);
                boolean needNew = (ap == null)
                        || !currentRegionKey.equalsIgnoreCase(ap.regionKey)
                        || haversineKm(lat, lon, ap.lat, ap.lon) > 40.0;
                if (needNew) {
                    String newPlate = plateService.generatePlate(currentRegionKey, usedPlatesInSession);
                    usedPlatesInSession.add(newPlate);
                    activeDriverPlates.put(driverId, new AssignedDriverPlate(newPlate, currentRegionKey, lat, lon));

                    Vehicle v = vehicleRepository.findByDriverId(driverId).orElse(null);
                    if (v != null) {
                        v.setLicensePlate(newPlate);
                        vehicleRepository.save(v);
                    }
                }
            }

            List<DriverDto> dtos = new ArrayList<>();
            for (DriverWithDist e : withDist) {
                DriverDto dto = toDto(e.driver);
                dto.setDistanceKm(Math.round(e.distKm * 100.0) / 100.0);
                dto.setDistanceMeters((int) Math.round(e.distKm * 1000.0));
                dtos.add(dto);
            }
            return ResponseEntity.ok(dtos);
        }

        // Если координаты не переданы — возвращаем всех
        List<DriverDto> dtos = new ArrayList<>();
        for (Driver d : drivers) {
            dtos.add(toDto(d));
        }
        return ResponseEntity.ok(dtos);
    }

    private DriverDto toDto(Driver d) {
        DriverDto dto = new DriverDto();
        dto.setId(d.getId());
        dto.setFullName(d.getFullName());
        dto.setPhone(d.getPhone());
        dto.setRating(d.getRating());
        dto.setStatus(d.getStatus());
        dto.setLat(d.getCurrentLat());
        dto.setLon(d.getCurrentLon());

        Vehicle v = vehicleRepository.findByDriverId(d.getId()).orElse(null);
        if (v != null) {
            dto.setCarMake(v.getMake());
            dto.setCarModel(v.getModel());

            AssignedDriverPlate ap = activeDriverPlates.get(d.getId());
            String plate = (ap != null) ? ap.plate : v.getLicensePlate();

            dto.setLicensePlate(plate);
            dto.setFormattedPlate(plateService.formatPlate(plate));
            dto.setRegionLabel(plateService.regionLabelOf(plate));
            dto.setColor(v.getColor());
            dto.setTariffCategory(v.getTariffCategory());
        }

        DriverVerification ver = fgisVerificationService.getVerificationDetails(d.getId());
        if (ver != null) {
            dto.setFgisStatus(ver.getFgisStatus());
            dto.setOsgopPolicyNumber(ver.getOsgopPolicyNumber());
            dto.setFgisRegistryNumber(d.getCarrier() != null ? d.getCarrier().getRegistryNumber() : "RU-77-VALID");
        }
        return dto;
    }

    private static class DriverWithDist {
        Driver driver;
        double distKm;
        DriverWithDist(Driver driver, double distKm) {
            this.driver = driver;
            this.distKm = distKm;
        }
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    @GetMapping("/candidates")
    public ResponseEntity<List<DriverDto>> getCandidates(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(required = false, defaultValue = "COMFORT") String tariff) {
        return ResponseEntity.ok(matchingService.findAndRankCandidates(lat, lon, tariff));
    }
}
