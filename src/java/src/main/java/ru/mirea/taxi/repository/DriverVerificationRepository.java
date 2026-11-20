package ru.mirea.taxi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.mirea.taxi.model.DriverVerification;
import java.util.Optional;

@Repository
public interface DriverVerificationRepository extends JpaRepository<DriverVerification, Long> {
    Optional<DriverVerification> findByDriverId(Long driverId);
}
