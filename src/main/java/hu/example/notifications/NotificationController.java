package hu.example.notifications;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notifications")
class NotificationController {
    private final NotificationStore store;

    NotificationController(NotificationStore store) {
        this.store = store;
    }

    @PostMapping
    ResponseEntity<StatusResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody CreateRequest request) {
        var notification = store.create(key, request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/notifications/" + notification.id()))
                .body(StatusResponse.from(notification));
    }

    @GetMapping("/{id}")
    StatusResponse get(@PathVariable UUID id) {
        return store.find(id).map(StatusResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    enum Channel { EMAIL, SMS, PUSH }
    enum Status { PENDING, SENT, FAILED }

    record CreateRequest(
            @NotNull Channel channel,
            @NotBlank @Size(max = 320) String recipient,
            @NotBlank @Size(max = 2000) String message) {}

    record StatusResponse(UUID id, Channel channel, Status status, int attempts,
                          String lastError, OffsetDateTime createdAt) {
        static StatusResponse from(NotificationStore.Notification notification) {
            return new StatusResponse(notification.id(), notification.channel(), notification.status(),
                    notification.attempts(), notification.lastError(), notification.createdAt());
        }
    }
}
