package com.QueueUp.Backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean dbUp = false;
        String dbMessage;

        try (Connection connection = dataSource.getConnection()) {
            dbUp = connection.isValid(2);
            dbMessage = dbUp ? "Database connection ok." : "Database connection invalid.";
        } catch (Exception ex) {
            dbMessage = "Database connection failed.";
        }

        boolean overallUp = dbUp;

        Map<String, Object> dbCheck = new LinkedHashMap<>();
        dbCheck.put("status", dbUp ? "ok" : "down");
        dbCheck.put("message", dbMessage);

        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("database", dbCheck);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", overallUp ? "ok" : "down");
        body.put("message", overallUp ? "Operational" : "Database unavailable.");
        body.put("timestamp", Instant.now().toString());
        body.put("checks", checks);

        return ResponseEntity.status(overallUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }
}
