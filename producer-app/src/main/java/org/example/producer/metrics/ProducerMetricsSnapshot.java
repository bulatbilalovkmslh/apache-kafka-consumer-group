package org.example.producer.metrics;

import java.util.Map;

public record ProducerMetricsSnapshot(
        long messagesSent,
        long sendErrors,
        Map<Integer, Long> messagesPerPartition,
        String sendStartTime,
        String sendEndTime,
        long sendDurationMs,
        double throughputMsgPerSec
) {
}
