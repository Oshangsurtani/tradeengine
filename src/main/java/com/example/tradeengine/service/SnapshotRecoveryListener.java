package com.example.tradeengine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * SnapshotRecoveryListener listens for the application ready event and
 * triggers restoration from snapshots if available.  This allows
 * faster startup by loading a recent snapshot and merging new events
 * instead of replaying the entire event log.  If no snapshots are
 * present, no action is taken.
 */
@Component
public class SnapshotRecoveryListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotRecoveryListener.class);
    private final SnapshotService snapshotService;

    @Autowired
    public SnapshotRecoveryListener(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            snapshotService.restoreAllLatestSnapshots();
        } catch (Exception e) {
            LOGGER.error("Error during snapshot recovery: {}", e.getMessage());
        }
    }
}