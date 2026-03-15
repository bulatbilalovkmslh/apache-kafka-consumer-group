package org.example.consumer.metrics;

import java.util.List;

public record RebalanceEvent(
        String timestamp,
        long durationMs,
        List<String> revokedPartitions,
        List<String> assignedPartitions
) {
}
