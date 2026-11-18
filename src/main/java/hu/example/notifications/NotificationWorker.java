package hu.example.notifications;

import static hu.example.notifications.NotificationController.Status.*;

import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.backoff.FixedBackOff;

@Component
class NotificationWorker {
    private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);
    private static final int MAX_ATTEMPTS = 3;
    private final NotificationStore store;
    private final NotificationSender sender;

    NotificationWorker(NotificationStore store, NotificationSender sender) {
        this.store = store;
        this.sender = sender;
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000, FixedBackOff.UNLIMITED_ATTEMPTS));
    }

    @KafkaListener(topics = "${notifications.topic}")
    @Transactional
    public void deliver(ConsumerRecord<String, String> event) {
        UUID id;
        int attempt;
        try {
            id = UUID.fromString(event.key());
            attempt = Integer.parseInt(event.value());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Ignoring invalid dispatch event, partition={}, offset={}", event.partition(), event.offset());
            return;
        }

        var notification = store.lockForDelivery(id).orElse(null);
        if (notification == null || notification.status() != PENDING || attempt != notification.attempts() + 1) {
            return;
        }

        try {
            sender.send(notification, attempt);
        } catch (NotificationSender.DeliveryException e) {
            boolean terminal = !e.retryable() || attempt >= MAX_ATTEMPTS;
            store.recordDelivery(id, terminal ? FAILED : PENDING, attempt,
                    terminal ? 0 : attempt * 2, e.getMessage());
            return;
        }
        store.recordDelivery(id, SENT, attempt, 0, null);
    }
}
