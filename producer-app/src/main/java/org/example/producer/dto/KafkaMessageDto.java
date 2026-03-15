package org.example.producer.dto;

public record KafkaMessageDto(String messageId, long timestamp) {
}
