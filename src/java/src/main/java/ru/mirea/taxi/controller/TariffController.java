package ru.mirea.taxi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.taxi.model.Tariff;
import ru.mirea.taxi.repository.TariffRepository;

import java.util.List;

@RestController
@RequestMapping("/api/tariffs")
@CrossOrigin(origins = "*")
public class TariffController {

    private final TariffRepository tariffRepository;

    public TariffController(TariffRepository tariffRepository) {
        this.tariffRepository = tariffRepository;
    }

    @GetMapping
    public ResponseEntity<List<Tariff>> getAllTariffs() {
        return ResponseEntity.ok(tariffRepository.findAll());
    }
}
