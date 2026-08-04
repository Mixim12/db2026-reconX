package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.dto.TradeResponse;
import com.dbtraining.reconx.dto.TradeMapper;
import com.dbtraining.reconx.repository.TradeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/v1/trades")
@Tag(name = "trades stream", description = "Server-Sent Events for trades")
@SecurityRequirement(name = "bearerAuth")
public class TradeStreamController {

    private static final Logger log = LoggerFactory.getLogger(TradeStreamController.class);
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final TradeRepository tradeRepository;
    private final TradeMapper mapper;

    public TradeStreamController(TradeRepository tradeRepository, TradeMapper mapper) {
        this.tradeRepository = tradeRepository;
        this.mapper = mapper;
    }

    @GetMapping(value = "/stream", produces = "text/event-stream")
    @Operation(summary = "Subscribe to live trade events")
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L); // 1 hour timeout
        this.emitters.add(emitter);

        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            this.emitters.remove(emitter);
        });
        emitter.onError((e) -> {
            emitter.complete();
            this.emitters.remove(emitter);
        });

        try {
            emitter.send(SseEmitter.event().name("init").data("connected"));
        } catch (IOException e) {
            emitter.completeWithError(e);
            this.emitters.remove(emitter);
        }

        return emitter;
    }

    @EventListener
    @Async
    public void handleTradeEvent(TradeEvent event) {
        if (emitters.isEmpty()) {
            return;
        }

        tradeRepository.findByTradeRef(event.tradeRef()).ifPresent(trade -> {
            TradeResponse response = mapper.toResponse(trade);
            List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

            this.emitters.forEach(emitter -> {
                try {
                    emitter.send(SseEmitter.event()
                            .data(response));
                } catch (IOException e) {
                    deadEmitters.add(emitter);
                }
            });

            this.emitters.removeAll(deadEmitters);
        });
    }
}
