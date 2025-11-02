package com.example.tradeengine.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages a collection of SSE emitters and dispatches events to
 * subscribed clients.
 */
@Service
public class StreamService {
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    public SseEmitter addEmitter() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        return emitter;
    }
    public void sendEvent(Object event) {
        for (Iterator<SseEmitter> it = emitters.iterator(); it.hasNext();) {
            SseEmitter emitter = it.next();
            try {
                emitter.send(event);
            } catch (IOException e) {
                it.remove();
            }
        }
    }
}