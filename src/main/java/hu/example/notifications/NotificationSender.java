package hu.example.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

interface NotificationSender {
    void send(NotificationStore.Notification notification, int attempt);

    final class DeliveryException extends RuntimeException {
        private final boolean retryable;

        DeliveryException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        boolean retryable() {
            return retryable;
        }
    }
}

@Component
class SimulatedNotificationSender implements NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(SimulatedNotificationSender.class);

    @Override
    public void send(NotificationStore.Notification notification, int attempt) {
        switch (notification.recipient()) {
            case "demo:retry" -> {
                if (attempt == 1) {
                    throw new DeliveryException("Simulated temporary failure", true);
                }
            }
            case "demo:fail" -> throw new DeliveryException("Simulated temporary failure", true);
            case "demo:reject" -> throw new DeliveryException("Simulated permanent failure", false);
            default -> { }
        }
        log.info("Simulated {} delivery, id={}, attempt={}", notification.channel(), notification.id(), attempt);
    }
}
