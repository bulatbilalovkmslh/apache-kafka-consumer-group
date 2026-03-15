package org.example.consumer.controller;

import org.example.consumer.metrics.ConsumerMetricsCollector;
import org.example.consumer.metrics.ConsumerMetricsSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/metrics")
public class MetricsController {

    private final ConsumerMetricsCollector metricsCollector;

    public MetricsController(ConsumerMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    @GetMapping
    public ResponseEntity<ConsumerMetricsSnapshot> getMetrics() {
        return ResponseEntity.ok(metricsCollector.snapshot());
    }

    @DeleteMapping
    public ResponseEntity<Void> resetMetrics() {
        metricsCollector.reset();
        return ResponseEntity.noContent().build();
    }
}
