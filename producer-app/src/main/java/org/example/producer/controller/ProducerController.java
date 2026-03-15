package org.example.producer.controller;

import org.example.producer.service.ProducerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProducerController {

    private final ProducerService producerService;

    public ProducerController(ProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping("/produce")
    public ResponseEntity<ProduceResponse> produce(
            @RequestParam(defaultValue = "1000") int count) {
        int dispatched = producerService.sendMessages(count);
        return ResponseEntity.ok(new ProduceResponse(dispatched, "Messages dispatched"));
    }

    public record ProduceResponse(int messageCount, String status) {
    }
}
