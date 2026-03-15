package org.example.producer.controller;

import org.example.producer.metrics.ProducerMetricsCollector;
import org.example.producer.metrics.ProducerMetricsSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/metrics")
public class MetricsController {

    private final ProducerMetricsCollector metricsCollector;

    public MetricsController(ProducerMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    @GetMapping
    public ResponseEntity<ProducerMetricsSnapshot> getMetrics() {
        return ResponseEntity.ok(metricsCollector.snapshot());
    }

    @DeleteMapping
    public ResponseEntity<Void> resetMetrics() {
        metricsCollector.reset();
        return ResponseEntity.noContent().build();
    }
}
