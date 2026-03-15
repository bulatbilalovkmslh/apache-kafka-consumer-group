package org.example.consumer.listener;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.consumer.metrics.ConsumerMetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MessageListener {

    private static final Logger log = LoggerFactory.getLogger(MessageListener.class);

    private final ConsumerMetricsCollector metricsCollector;
    private final String instanceId;

    public MessageListener(ConsumerMetricsCollector metricsCollector,
                           @Value("${consumer.instance-id}") String instanceId) {
        this.metricsCollector = metricsCollector;
        this.instanceId = instanceId;
    }

    @KafkaListener(topics = "group-demo-topic", groupId = "demo-consumer-group")
    public void listen(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        metricsCollector.recordMessage(record.partition(), record.timestamp());

        log.info("[{}] Received: partition={}, offset={}, key={}, timestamp={}, thread={}, payload={}",
                instanceId,
                record.partition(),
                record.offset(),
                record.key(),
                Instant.ofEpochMilli(record.timestamp()),
                Thread.currentThread().getName(),
                record.value());

        acknowledgment.acknowledge();
    }
}
