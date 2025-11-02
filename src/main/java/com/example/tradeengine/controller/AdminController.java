package com.example.tradeengine.controller;

import com.example.tradeengine.service.EventReplayService;
import com.example.tradeengine.service.SnapshotService;
import com.example.tradeengine.model.OrderBookSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative endpoints.  Currently exposes a replay operation
 * that rebuilds system state from the event log.  This endpoint
 * should be protected by the API key filter to prevent
 * unauthorized use.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {
    private final EventReplayService replayService;
    private final SnapshotService snapshotService;
    @Autowired
    public AdminController(EventReplayService replayService, SnapshotService snapshotService) {
        this.replayService = replayService;
        this.snapshotService = snapshotService;
    }
    /**
     * Trigger a full replay of events.  Returns a simple message once
     * complete.  Note: this will drop existing orders and trades and
     * rebuild them from the append‑only log.
     */
    @PostMapping("/replay")
    public ResponseEntity<String> replay() {
        replayService.replay();
        return ResponseEntity.ok("Replay complete");
    }

    /**
     * Create a snapshot of the order book.  If an instrument is
     * specified as a query parameter (?instrument=BTC-USD), only that
     * instrument is snapshotted.  Otherwise all instruments are
     * snapshotted.  Returns a message with snapshot details.
     */
    @PostMapping("/snapshot")
    public ResponseEntity<String> snapshot(@RequestParam(value = "instrument", required = false) String instrument) {
        if (instrument != null && !instrument.isEmpty()) {
            OrderBookSnapshot snap = snapshotService.createSnapshot(instrument);
            return ResponseEntity.ok(snap == null ? "No orders to snapshot for " + instrument : "Snapshot created for " + instrument + " at " + snap.getTimestamp());
        } else {
            snapshotService.createSnapshotsForAllInstruments();
            return ResponseEntity.ok("Snapshots created for all instruments");
        }
    }

    /**
     * Restore the order book state from the latest snapshot and merge
     * subsequent events.  If an instrument is specified, only that
     * instrument is restored; otherwise all instruments with
     * snapshots are restored.
     */
    @PostMapping("/restore")
    public ResponseEntity<String> restore(@RequestParam(value = "instrument", required = false) String instrument) {
        if (instrument != null && !instrument.isEmpty()) {
            snapshotService.restoreLatestSnapshot(instrument);
            return ResponseEntity.ok("Restored " + instrument + " from latest snapshot and merged events");
        } else {
            snapshotService.restoreAllLatestSnapshots();
            return ResponseEntity.ok("Restored all instruments from latest snapshots and merged events");
        }
    }
}