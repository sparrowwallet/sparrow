package com.sparrowwallet.sparrow;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

public class BlockSummary implements Comparable<BlockSummary> {
    private final Integer height;
    private final Date timestamp;
    private final Double medianFee;
    private final Integer transactionCount;
    private final Integer weight;

    public BlockSummary(Integer height, Date timestamp) {
        this(height, timestamp, null, null, null);
    }

    public BlockSummary(Integer height, Date timestamp, Double medianFee, Integer transactionCount, Integer weight) {
        this.height = height;
        this.timestamp = timestamp;
        this.medianFee = medianFee;
        this.transactionCount = transactionCount;
        this.weight = weight;
    }

    public Integer getHeight() {
        return height;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public Optional<Double> getMedianFee() {
        return medianFee == null ? Optional.empty() : Optional.of(medianFee);
    }

    public Optional<Integer> getTransactionCount() {
        return transactionCount == null ? Optional.empty() : Optional.of(transactionCount);
    }

    public Optional<Integer> getWeight() {
        return weight == null ? Optional.empty() : Optional.of(weight);
    }

    private static long calculateElapsedSeconds(long timestampUtc) {
        Instant timestampInstant = Instant.ofEpochMilli(timestampUtc);
        Instant nowInstant = Instant.now();
        return ChronoUnit.SECONDS.between(timestampInstant, nowInstant);
    }

    public String getElapsed() {
        long elapsed = calculateElapsedSeconds(getTimestamp().getTime());
        if(elapsed < 0) {
            return "now";
        } else if(elapsed < 60) {
            return elapsed + "s";
        } else if(elapsed < 3600) {
            return elapsed / 60 + "m";
        } else if(elapsed < 86400) {
            return elapsed / 3600 + "h";
        } else {
            return elapsed / 86400 + "d";
        }
    }

    public String toString() {
        return getElapsed() + ":" + getMedianFee();
    }

    @Override
    public int compareTo(BlockSummary o) {
        return o.height - height;
    }
}
