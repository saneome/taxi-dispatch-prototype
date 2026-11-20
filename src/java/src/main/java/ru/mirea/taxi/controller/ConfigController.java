package ru.mirea.taxi.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
@CrossOrigin(origins = "*")
public class ConfigController {

    private final String yandexMapsApiKey;

    public ConfigController(@Value("${yandex.maps.api-key:}") String yandexMapsApiKey) {
        this.yandexMapsApiKey = yandexMapsApiKey;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "yandexMapsApiKey", yandexMapsApiKey != null ? yandexMapsApiKey.trim() : ""
        ));
    }
}
