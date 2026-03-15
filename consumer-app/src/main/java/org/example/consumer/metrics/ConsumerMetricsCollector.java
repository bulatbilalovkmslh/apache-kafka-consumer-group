package org.example.consumer.metrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ConsumerMetricsCollector {

    private final String instanceId;

    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final ConcurrentHashMap<Integer, AtomicLong> messagesPerPartition = new ConcurrentHashMap<>();
    private final AtomicLong firstMessageTimestamp = new AtomicLong(0);
    private final AtomicLong lastMessageTimestamp = new AtomicLong(0);
    private final AtomicInteger rebalanceCount = new AtomicInteger(0);
    private final CopyOnWriteArrayList<RebalanceEvent> rebalanceEvents = new CopyOnWriteArrayList<>();
    private final Set<String> threadsUsed = ConcurrentHashMap.newKeySet();
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    private final AtomicLong minLatencyMs = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxLatencyMs = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);
    private volatile List<String> currentAssignedPartitions = List.of();

    public ConsumerMetricsCollector(@Value("${consumer.instance-id}") String instanceId) {
        this.instanceId = instanceId;
    }

    public void recordMessage(int partition, long kafkaRecordTimestamp) {
        long now = System.currentTimeMillis();
        messagesReceived.incrementAndGet();
        messagesPerPartition.computeIfAbsent(partition, k -> new AtomicLong(0)).incrementAndGet();
        firstMessageTimestamp.compareAndSet(0, now);
        lastMessageTimestamp.set(now);
        threadsUsed.add(Thread.currentThread().getName());

        long latency = now - kafkaRecordTimestamp;
        if (latency >= 0) {
            totalLatencyMs.addAndGet(latency);
            minLatencyMs.updateAndGet(current -> Math.min(current, latency));
            maxLatencyMs.updateAndGet(current -> Math.max(current, latency));
        }
    }

    public void recordRebalance(RebalanceEvent event) {
        rebalanceCount.incrementAndGet();
        rebalanceEvents.add(event);
    }

    public void updateAssignedPartitions(List<String> partitions) {
        this.currentAssignedPartitions = List.copyOf(partitions);
    }

    public void recordError() {
        errors.incrementAndGet();
    }

    public ConsumerMetricsSnapshot snapshot() {
        long received = messagesReceived.get();
        long firstTs = firstMessageTimestamp.get();
        long lastTs = lastMessageTimestamp.get();
        long durationMs = (firstTs > 0 && lastTs > firstTs) ? lastTs - firstTs : 0;
        double throughput = durationMs > 0 ? (received * 1000.0 / durationMs) : 0;
        double avgLatency = received > 0 ? (totalLatencyMs.get() * 1.0 / received) : 0;
        long minLat = minLatencyMs.get();
        if (minLat == Long.MAX_VALUE) {
            minLat = 0;
        }

        Map<Integer, Long> partitionMap = new TreeMap<>();
        messagesPerPartition.forEach((k, v) -> partitionMap.put(k, v.get()));

        return new ConsumerMetricsSnapshot(
                instanceId,
                received,
                partitionMap,
                currentAssignedPartitions,
                rebalanceCount.get(),
                List.copyOf(rebalanceEvents),
                Set.copyOf(threadsUsed),
                firstTs > 0 ? Instant.ofEpochMilli(firstTs).toString() : null,
                lastTs > 0 ? Instant.ofEpochMilli(lastTs).toString() : null,
                durationMs,
                Math.round(throughput * 100.0) / 100.0,
                Math.round(avgLatency * 100.0) / 100.0,
                minLat,
                maxLatencyMs.get(),
                errors.get()
        );
    }

    public void reset() {
        messagesReceived.set(0);
        messagesPerPartition.clear();
        firstMessageTimestamp.set(0);
        lastMessageTimestamp.set(0);
        rebalanceCount.set(0);
        rebalanceEvents.clear();
        threadsUsed.clear();
        totalLatencyMs.set(0);
        minLatencyMs.set(Long.MAX_VALUE);
        maxLatencyMs.set(0);
        errors.set(0);
    }
}
