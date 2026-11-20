package ru.mirea.taxi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.mirea.taxi.model.Driver;
import ru.mirea.taxi.model.DriverVerification;
import ru.mirea.taxi.repository.DriverVerificationRepository;
import ru.mirea.taxi.repository.VehicleRepository;
import ru.mirea.taxi.service.FgisVerificationService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class FgisVerificationServiceTest {

    @Test
    @DisplayName("Проверка допуска водителя по ФЗ № 580-ФЗ и полису ОСГОП")
    void testVerifyDriverSuccess() {
        DriverVerificationRepository verRepo = Mockito.mock(DriverVerificationRepository.class);
        VehicleRepository vehRepo = Mockito.mock(VehicleRepository.class);
        FgisVerificationService service = new FgisVerificationService(verRepo, vehRepo);

        Driver driver = new Driver();
        driver.setId(10L);

        DriverVerification validVer = new DriverVerification();
        validVer.setFgisStatus("VALID");
        validVer.setOsgopStatus("VALID");
        validVer.setCriminalRecordCheck("CLEAN");

        when(verRepo.findByDriverId(10L)).thenReturn(Optional.of(validVer));

        boolean isAllowed = service.verifyDriverForOrder(driver);
        assertTrue(isAllowed, "Водитель с валидным статусом ФГИС и ОСГОП должен быть допущен");
    }

    @Test
    @DisplayName("Отклонение водителя при отсутствии действующего полиса ОСГОП")
    void testVerifyDriverRejectionNoOsgop() {
        DriverVerificationRepository verRepo = Mockito.mock(DriverVerificationRepository.class);
        VehicleRepository vehRepo = Mockito.mock(VehicleRepository.class);
        FgisVerificationService service = new FgisVerificationService(verRepo, vehRepo);

        Driver driver = new Driver();
        driver.setId(11L);

        DriverVerification rejectedVer = new DriverVerification();
        rejectedVer.setFgisStatus("VALID");
        rejectedVer.setOsgopStatus("EXPIRED"); // Полис просрочен
        rejectedVer.setCriminalRecordCheck("CLEAN");

        when(verRepo.findByDriverId(11L)).thenReturn(Optional.of(rejectedVer));

        boolean isAllowed = service.verifyDriverForOrder(driver);
        assertFalse(isAllowed, "Водитель с просроченным ОСГОП должен быть заблокирован по ФЗ-580");
    }
}
