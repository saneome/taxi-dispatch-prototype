package ru.mirea.taxi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import ru.mirea.taxi.controller.DriverController;
import ru.mirea.taxi.model.Driver;
import ru.mirea.taxi.repository.*;
import ru.mirea.taxi.service.*;

import java.util.List;

class DriverSearchTest {
    @Test
    void searchRadiusExcludesMoscowAndDistantSuburbs() {
        var repository = mock(DriverRepository.class);
        Driver local = new Driver();
        local.setId(1L);
        local.setCurrentLat(56.122);
        local.setCurrentLon(40.315);
        Driver moscow = new Driver();
        moscow.setId(2L);
        moscow.setCurrentLat(55.75);
        moscow.setCurrentLon(37.61);
        Driver bogolyubovo = new Driver();
        bogolyubovo.setId(3L);
        bogolyubovo.setCurrentLat(56.196);
        bogolyubovo.setCurrentLon(40.535);
        when(repository.findAll()).thenReturn(List.of(local, moscow, bogolyubovo));
        var controller =
                new DriverController(
                        repository,
                        mock(VehicleRepository.class),
                        mock(FgisVerificationService.class),
                        mock(MatchingService.class),
                        mock(DemoFleetService.class),
                        new PlateService());
        var result = controller.getAllDrivers(56.122, 40.315, 3).getBody();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(0, result.get(0).getDistanceMeters());
    }
}
