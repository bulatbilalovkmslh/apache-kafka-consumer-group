package org.example.consumer.metrics;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ConsumerMetricsSnapshot(
        String instanceId,
        long messagesReceived,
        Map<Integer, Long> messagesPerPartition,
        List<String> assignedPartitions,
        int rebalanceCount,
        List<RebalanceEvent> rebalanceEvents,
        Set<String> threadsUsed,
        String firstMessageTime,
        String lastMessageTime,
        long processingDurationMs,
        double throughputMsgPerSec,
        double avgLatencyMs,
        long minLatencyMs,
        long maxLatencyMs,
        long errors
) {
}
