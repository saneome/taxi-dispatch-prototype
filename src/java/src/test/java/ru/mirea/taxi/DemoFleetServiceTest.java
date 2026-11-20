package ru.mirea.taxi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ru.mirea.taxi.dto.RouteDto;
import ru.mirea.taxi.model.Driver;
import ru.mirea.taxi.repository.DriverRepository;
import ru.mirea.taxi.repository.VehicleRepository;
import ru.mirea.taxi.service.*;

import java.util.ArrayList;
import java.util.List;

class DemoFleetServiceTest {
    private Driver driver(long id, String status, double lat, double lon) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setStatus(status);
        driver.setCurrentLat(lat);
        driver.setCurrentLon(lon);
        return driver;
    }

    @ParameterizedTest
    @CsvSource({"56.122,40.315", "56.196,40.535", "52.286,104.281", "55.0,60.0"})
    void createsStableLocalFleetOnRoadsAnywhere(double lat, double lon) {
        var repository = mock(DriverRepository.class);
        var routing = mock(OsrmRoutingService.class);
        List<Driver> available = new ArrayList<>();
        for (int i = 0; i < 10; i++) available.add(driver(i + 1, "AVAILABLE", 55.75, 37.61));
        when(repository.findByStatus("AVAILABLE")).thenReturn(available);
        var geometry =
                List.of(
                        List.of(lat, lon),
                        List.of(lat + .003, lon + .003),
                        List.of(lat + .006, lon + .006));
        when(routing.calculateRoute(anyList(), eq(false)))
                .thenReturn(new RouteDto(2000, 300, geometry, "Mapbox Directions"));
        var fleet = new DemoFleetService(repository, routing);
        fleet.ensureLocalFleet(lat, lon, 3);
        for (Driver d : available) {
            assertTrue(geometry.contains(List.of(d.getCurrentLat(), d.getCurrentLon())));
            assertTrue(DemoFleetService.isNear(d, lat, lon, 3));
        }
        fleet.ensureLocalFleet(lat, lon, 3);
        verify(routing, times(1)).calculateRoute(anyList(), eq(false));
        verify(repository, times(1)).saveAll(anyList());
    }

    @Test
    void occupiedCarsAreNotRelocatedAndMissingRoadDoesNotInventCars() {
        var repository = mock(DriverRepository.class);
        var routing = mock(OsrmRoutingService.class);
        when(repository.findByStatus("AVAILABLE")).thenReturn(List.of());
        var fleet = new DemoFleetService(repository, routing);
        fleet.ensureLocalFleet(56.12, 40.31, 3);
        verifyNoInteractions(routing);
        Driver free = driver(1, "AVAILABLE", 55.75, 37.61);
        when(repository.findByStatus("AVAILABLE")).thenReturn(List.of(free));
        when(routing.calculateRoute(anyList(), eq(false)))
                .thenThrow(
                        new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                                "No road"));
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> fleet.ensureLocalFleet(56.12, 40.31, 3));
        assertEquals(55.75, free.getCurrentLat());
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void selectedMoscowOrBusyDriverCannotServeVladimir() {
        var drivers = mock(DriverRepository.class);
        var vehicles = mock(VehicleRepository.class);
        var routing = mock(OsrmRoutingService.class);
        var matching =
                new MatchingService(
                        drivers,
                        vehicles,
                        mock(FgisVerificationService.class),
                        routing,
                        new PlateService(),
                        mock(DemoFleetService.class),
                        10);
        assertFalse(
                matching.canServePickup(
                        driver(1, "AVAILABLE", 55.75, 37.61), 56.12, 40.31, "ECONOMY"));
        assertFalse(
                matching.canServePickup(driver(2, "BUSY", 56.12, 40.31), 56.12, 40.31, "ECONOMY"));
        verifyNoInteractions(routing, vehicles);
    }
}
