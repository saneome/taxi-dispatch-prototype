package ru.mirea.taxi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.mirea.taxi.model.Tariff;

@Repository
public interface TariffRepository extends JpaRepository<Tariff, String> {
}
