package com.example.demo.service;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.example.demo.common.exception.BadRequestException;
@Service
public class NotificationStreamService {
    private final ConcurrentHashMap<Integer, Set<SseEmitter>> streams = new ConcurrentHashMap<>();
    public SseEmitter subscribe(Integer userId) {
        SseEmitter emitter = new SseEmitter(120000L);
        streams.compute(userId, (id, existing) -> {
            Set<SseEmitter> clients = existing == null ? ConcurrentHashMap.newKeySet() : existing;
            if (clients.size() >= 5) throw new BadRequestException("Đã mở quá nhiều kết nối thông báo");
            clients.add(emitter); return clients;
        });
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> { remove(userId, emitter); emitter.complete(); });
        emitter.onError(error -> remove(userId, emitter));
        send(userId, emitter, "ready", java.util.Map.of("connected", true));
        return emitter;
    }
    public void closeUser(Integer userId) {
        Set<SseEmitter> clients = streams.remove(userId);
        if (clients != null) clients.forEach(SseEmitter::complete);
    }
    private void remove(Integer userId, SseEmitter emitter) {
        streams.computeIfPresent(userId, (id, clients) -> { clients.remove(emitter); return clients.isEmpty() ? null : clients; });
    }
    private void send(Integer userId, SseEmitter emitter, String event, Object data) {
        try { emitter.send(SseEmitter.event().name(event).data(data)); }
        catch (IOException | IllegalStateException closed) { remove(userId, emitter); emitter.complete(); }
    }
    public void afterCommit(Integer userId, Object notification) {
        Runnable publish = () -> {
            Set<SseEmitter> clients = streams.get(userId);
            if (clients != null) clients.forEach(emitter -> send(userId, emitter, "notification", notification));
        };
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        else publish.run();
    }
    @Scheduled(fixedDelay=30000)
    public void heartbeat() {
        streams.forEach((id, clients) -> clients.forEach(emitter -> send(id, emitter, "heartbeat", java.util.Map.of("time", java.time.Instant.now().toString()))));
    }
}
