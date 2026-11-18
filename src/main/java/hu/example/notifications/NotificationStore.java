package hu.example.notifications;

import static hu.example.notifications.NotificationController.*;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
class NotificationStore {
    private final JdbcClient jdbc;

    NotificationStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Notification create(String key, CreateRequest request) {
        jdbc.sql("""
                INSERT INTO notification (id, idempotency_key, channel, recipient, message)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (idempotency_key) DO NOTHING
                """)
                .params(UUID.randomUUID(), key, request.channel().name(), request.recipient(), request.message())
                .update();
        var existing = jdbc.sql("SELECT * FROM notification WHERE idempotency_key = ?")
                .param(key).query(Notification.class).single();
        if (existing.channel() != request.channel() || !existing.recipient().equals(request.recipient())
                || !existing.message().equals(request.message())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used");
        }
        return existing;
    }

    Optional<Notification> find(UUID id) {
        return jdbc.sql("SELECT * FROM notification WHERE id = ?")
                .param(id).query(Notification.class).optional();
    }

    Optional<Notification> claimForPublication() {
        return jdbc.sql("""
                SELECT * FROM notification
                WHERE status = 'PENDING' AND NOT published AND next_attempt_at <= now()
                ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED
                """).query(Notification.class).optional();
    }

    Optional<Notification> lockForDelivery(UUID id) {
        return jdbc.sql("SELECT * FROM notification WHERE id = ? FOR UPDATE")
                .param(id).query(Notification.class).optional();
    }

    void markPublished(UUID id) {
        jdbc.sql("UPDATE notification SET published = true WHERE id = ?").param(id).update();
    }

    void recordDelivery(UUID id, Status status, int attempts, int retryDelay, String error) {
        jdbc.sql("""
                UPDATE notification SET status = ?, attempts = ?, published = false,
                    next_attempt_at = now() + make_interval(secs => ?), last_error = ?
                WHERE id = ?
                """).param(1, status.name()).param(2, attempts).param(3, retryDelay)
                .param(4, error, java.sql.Types.VARCHAR).param(5, id).update();
    }

    record Notification(UUID id, Channel channel, String recipient, String message, Status status,
                        int attempts, String lastError, OffsetDateTime createdAt) {}
}
