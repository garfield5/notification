package hu.example.notifications;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class NotificationRelay {
    private final NotificationStore store;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transaction;
    private final String topic;

    NotificationRelay(NotificationStore store, KafkaTemplate<String, String> kafka,
                      TransactionTemplate transaction, @Value("${notifications.topic}") String topic) {
        this.store = store;
        this.kafka = kafka;
        this.transaction = transaction;
        this.topic = topic;
    }

    @Scheduled(fixedDelay = 250)
    void publish() {
        for (int count = 0; count < 50; count++) {
            if (!Boolean.TRUE.equals(transaction.execute(status -> publishNext()))) {
                return;
            }
        }
    }

    private boolean publishNext() {
        var pending = store.claimForPublication();
        if (pending.isEmpty()) {
            return false;
        }
        var notification = pending.get();
        var key = notification.id().toString();
        try {
            kafka.send(topic, key, Integer.toString(notification.attempts() + 1))
                    .get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publication interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka publication failed", e);
        }
        store.markPublished(notification.id());
        return true;
    }
}
