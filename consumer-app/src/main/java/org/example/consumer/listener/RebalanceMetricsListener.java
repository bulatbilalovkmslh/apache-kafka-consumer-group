package org.example.consumer.listener;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.example.consumer.metrics.ConsumerMetricsCollector;
import org.example.consumer.metrics.RebalanceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public class RebalanceMetricsListener implements ConsumerRebalanceListener {

    private static final Logger log = LoggerFactory.getLogger(RebalanceMetricsListener.class);

    private final ConsumerMetricsCollector metricsCollector;
    private volatile long revokeTimestamp;
    private volatile List<String> revokedPartitions = List.of();

    public RebalanceMetricsListener(ConsumerMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        revokeTimestamp = System.currentTimeMillis();
        revokedPartitions = toPartitionNames(partitions);
        log.info("Partitions revoked: {}", revokedPartitions);
    }

    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        long now = System.currentTimeMillis();
        long duration = revokeTimestamp > 0 ? now - revokeTimestamp : 0;
        List<String> assigned = toPartitionNames(partitions);

        metricsCollector.recordRebalance(new RebalanceEvent(
                Instant.ofEpochMilli(now).toString(),
                duration,
                revokedPartitions,
                assigned
        ));
        metricsCollector.updateAssignedPartitions(assigned);

        log.info("Partitions assigned: {}, rebalance duration: {} ms", assigned, duration);

        revokeTimestamp = 0;
        revokedPartitions = List.of();
    }

    private List<String> toPartitionNames(Collection<TopicPartition> partitions) {
        return partitions.stream()
                .map(tp -> tp.topic() + "-" + tp.partition())
                .toList();
    }
}
