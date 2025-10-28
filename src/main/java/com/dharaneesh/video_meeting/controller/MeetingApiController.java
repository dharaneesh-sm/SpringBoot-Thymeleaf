package com.dharaneesh.video_meeting.controller;

import com.dharaneesh.video_meeting.service.MeetingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Slf4j
public class MeetingApiController {

    private final MeetingService meetingService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/{meetingCode}/end")
    public ResponseEntity<?> endMeeting(@PathVariable String meetingCode,
                                        @RequestParam String username) {
        try {
            String code = meetingCode.trim().toUpperCase();
            String user = username.trim();

            boolean ended = meetingService.endMeeting(code, user);
            if (!ended) {
                log.warn("End meeting denied or not found: code={}, by={}", code, user);
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Only the host can end the meeting or meeting not found"
                ));
            }

            // Broadcast control event to all participants
            Map<String, Object> payload = Map.of(
                    "type", "MEETING_ENDED",
                    "endedBy", user,
                    "timestamp", LocalDateTime.now().toString()
            );
            messagingTemplate.convertAndSend("/topic/meeting/" + code + "/control", payload);

            log.info("Meeting {} ended successfully by {}", code, user);
            return ResponseEntity.ok(Map.of("status", "ended"));
        } catch (Exception e) {
            log.error("Error ending meeting {}", meetingCode, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to end meeting"
            ));
        }
    }
}


