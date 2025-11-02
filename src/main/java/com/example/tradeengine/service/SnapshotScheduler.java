package com.example.tradeengine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SnapshotScheduler periodically triggers snapshot creation for all
 * instruments.  The interval is configured via
 * `snapshot.interval.millis` in application.yml.  Scheduling can be
 * disabled by setting `snapshot.enabled=false`.
 */
@Component
@ConditionalOnProperty(prefix = "snapshot", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SnapshotScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotScheduler.class);
    private final SnapshotService snapshotService;

    @Autowired
    public SnapshotScheduler(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @Scheduled(fixedDelayString = "${snapshot.interval.millis:300000}")
    public void runSnapshotJob() {
        try {
            snapshotService.createSnapshotsForAllInstruments();
        } catch (Exception e) {
            LOGGER.error("Snapshot scheduler encountered an exception: {}", e.getMessage());
        }
    }
}