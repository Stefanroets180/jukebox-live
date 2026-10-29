package com.jukebox.live.controller;

import com.jukebox.live.dto.*;
import com.jukebox.live.entity.User;
import com.jukebox.live.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @GetMapping
    public ResponseEntity<List<QueueItemDto>> getQueue(
            @RequestParam UUID partyId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(queueService.getQueue(partyId));
    }

    @PostMapping("/add")
    public ResponseEntity<AddToQueueResponse> addToQueue(
            @RequestBody AddToQueueRequest request,
            @AuthenticationPrincipal User user,
            @RequestHeader("X-Party-ID") UUID partyId,
            @RequestHeader(value = "X-Forwarded-For", required = false) String clientIp) {
        return ResponseEntity.ok(queueService.addToQueue(
                partyId, user.getId(), request.getTrackId(), clientIp));
    }

    @DeleteMapping("/{queueId}/skip")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> skipQueueItem(
            @PathVariable UUID queueId,
            @AuthenticationPrincipal User user) {
        queueService.skipQueueItem(queueId, user.getId());
        return ResponseEntity.ok().build();
    }
}
