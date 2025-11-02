package com.example.tradeengine.controller;

import com.example.tradeengine.service.StreamService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class StreamController {
    private final StreamService streamService;
    public StreamController(StreamService streamService) {
        this.streamService = streamService;
    }
    @GetMapping("/stream")
    public SseEmitter stream() {
        return streamService.addEmitter();
    }
}