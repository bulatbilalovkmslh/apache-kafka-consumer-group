package org.example.producer.metrics;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ProducerMetricsCollector {

    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong sendErrors = new AtomicLong(0);
    private final ConcurrentHashMap<Integer, AtomicLong> messagesPerPartition = new ConcurrentHashMap<>();
    private final AtomicLong sendStartTime = new AtomicLong(0);
    private final AtomicLong sendEndTime = new AtomicLong(0);

    public void recordSend(int partition) {
        long now = System.currentTimeMillis();
        messagesSent.incrementAndGet();
        messagesPerPartition.computeIfAbsent(partition, k -> new AtomicLong(0)).incrementAndGet();
        sendStartTime.compareAndSet(0, now);
        sendEndTime.set(now);
    }

    public void recordError() {
        sendErrors.incrementAndGet();
    }

    public ProducerMetricsSnapshot snapshot() {
        long sent = messagesSent.get();
        long start = sendStartTime.get();
        long end = sendEndTime.get();
        long durationMs = (start > 0 && end > start) ? end - start : 0;
        double throughput = durationMs > 0 ? (sent * 1000.0 / durationMs) : 0;

        Map<Integer, Long> partitionMap = new TreeMap<>();
        messagesPerPartition.forEach((k, v) -> partitionMap.put(k, v.get()));

        return new ProducerMetricsSnapshot(
                sent,
                sendErrors.get(),
                partitionMap,
                start > 0 ? Instant.ofEpochMilli(start).toString() : null,
                end > 0 ? Instant.ofEpochMilli(end).toString() : null,
                durationMs,
                Math.round(throughput * 100.0) / 100.0
        );
    }

    public void reset() {
        messagesSent.set(0);
        sendErrors.set(0);
        messagesPerPartition.clear();
        sendStartTime.set(0);
        sendEndTime.set(0);
    }
}
