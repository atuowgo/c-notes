package com.cnotes.social;

import com.cnotes.social.dto.NotificationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;

    @GetMapping
    public List<NotificationDto> list() {
        return notifications.list();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notifications.unreadCount());
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markAllRead() {
        notifications.markAllRead();
        return ResponseEntity.noContent().build();
    }
}
