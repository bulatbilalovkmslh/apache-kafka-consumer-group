package org.example.producer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.producer.dto.KafkaMessageDto;
import org.example.producer.metrics.ProducerMetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class ProducerService {

    private static final Logger log = LoggerFactory.getLogger(ProducerService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ProducerMetricsCollector metricsCollector;
    private final String topic;

    public ProducerService(KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper,
                           ProducerMetricsCollector metricsCollector,
                           @Value("${app.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metricsCollector = metricsCollector;
        this.topic = topic;
    }

    public int sendMessages(int count) {
        log.info("Dispatching batch of {} messages to topic '{}'", count, topic);

        List<CompletableFuture<?>> futures = new ArrayList<>(count);
        int buffered = 0;

        for (int i = 0; i < count; i++) {
            String messageId = UUID.randomUUID().toString();
            KafkaMessageDto message = new KafkaMessageDto(messageId, Instant.now().toEpochMilli());

            try {
                String payload = objectMapper.writeValueAsString(message);
                var future = kafkaTemplate.send(topic, messageId, payload)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                metricsCollector.recordError();
                                log.error("Failed to send message: id={}", message.messageId(), ex);
                            } else {
                                var metadata = result.getRecordMetadata();
                                metricsCollector.recordSend(metadata.partition());
                            }
                        });
                futures.add(future);
                buffered++;
            } catch (JsonProcessingException e) {
                metricsCollector.recordError();
                log.error("Failed to serialize message: id={}", messageId, e);
            }
        }

        kafkaTemplate.flush();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        log.info("Batch complete: {}/{} messages delivered to topic '{}'", buffered, count, topic);
        return buffered;
    }
}
