package com.dharaneesh.video_meeting.controller;

import com.dharaneesh.video_meeting.model.Meeting;
import com.dharaneesh.video_meeting.model.Participant;
import com.dharaneesh.video_meeting.service.MeetingService;
import com.dharaneesh.video_meeting.service.ParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.io.Serializable;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final MeetingService meetingService;
    private final ParticipantService participantService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/meeting/{meetingCode}/join") //Maps WebSocket messages to handler methods
    @SendTo("/topic/meeting/{meetingCode}/participants") //Server broadcasts message to specified topic
    public Map<String, Object> handleParticipantJoin(@DestinationVariable String meetingCode,
                                                     @Payload Map<String, String> joinData,
                                                     @Header("simpSessionId") String sessionId) {

        try {
            String participantName = joinData.get("participantName");

            log.info("Participant {} (session: {}) joining meeting {}", participantName, sessionId, meetingCode);

            Optional<Meeting> meetingOpt = meetingService.getMeetingByCode(meetingCode);
            if (meetingOpt.isPresent()) {
                Meeting meeting = meetingOpt.get();

                boolean isHost = meeting.getCreatedBy().equals(participantName);

                // Add participant if not active already (idempotent join)
                Participant participant = participantService.getOrAddParticipant(meeting, participantName, sessionId);
                participant.setIsHost(isHost);

                // Get updated participant list
                List<Participant> participants = participantService.getActiveParticipants(meetingCode);
                List<Map<String, ? extends Serializable>> participantList = participants.stream()
                        .map(p -> Map.of(
                                "name", p.getParticipantName(),
                                "isHost", p.getIsHost(),
                                "isMuted", p.getIsMuted(),
                                "videoEnabled", p.getVideoEnabled(),
                                "sessionId", p.getSessionId()
                        ))
                        .collect(Collectors.toList());

                log.info("Participant {} joined successfully. Total participants: {}",
                        participantName, participants.size());

                return Map.of(
                        "type", "PARTICIPANT_JOINED",
                        "participant", Map.of(
                                "name", participant.getParticipantName(),
                                "isHost", isHost,
                                "sessionId", participant.getSessionId()
                        ),
                        "participants", participantList,
                        "participantCount", participants.size(),
                        "timestamp", LocalDateTime.now().toString()
                );
            }

        } catch (Exception e) {
            log.error("Error handling participant join", e);
        }

        return Map.of("type", "ERROR", "message", "Failed to join meeting");
    }

    @MessageMapping("/meeting/{meetingCode}/leave")
    @SendTo("/topic/meeting/{meetingCode}/participants")
    public Map<String, Object> handleParticipantLeave(@DestinationVariable String meetingCode,
                                                      @Header("simpSessionId") String sessionId) {

        try {
            log.info("Participant with session {} leaving meeting {}", sessionId, meetingCode);

            participantService.removeParticipant(sessionId);

            List<Participant> participants = participantService.getActiveParticipants(meetingCode);
            List<Map<String, ? extends Serializable>> participantList = participants.stream()
                    .map(p -> Map.of(
                            "name", p.getParticipantName(),
                            "isHost", p.getIsHost(),
                            "isMuted", p.getIsMuted(),
                            "videoEnabled", p.getVideoEnabled(),
                            "sessionId", p.getSessionId()
                    ))
                    .collect(Collectors.toList());

            return Map.of(
                    "type", "PARTICIPANT_LEFT",
                    "sessionId", sessionId,
                    "participants", participantList,
                    "participantCount", participants.size(),
                    "timestamp", LocalDateTime.now().toString()
            );

        }
        catch (Exception e) {
            log.error("Error handling participant leave", e);
        }

        return Map.of("type", "ERROR", "message", "Failed to leave meeting");
    }

    @MessageMapping("/meeting/{meetingCode}/webrtc-signal")
    public void handleWebRTCSignaling(@DestinationVariable String meetingCode,
                                      @Payload Map<String, Object> signalData, @Header("simpSessionId") String sessionId) {

        try {
            String fromSessionId = sessionId;
            String toSessionId = (String) signalData.get("targetSessionId");
            String type = (String) signalData.get("type");
            Object data = signalData.get("data");

            log.debug("WebRTC signal {} from {} to {} in meeting {}", type, fromSessionId, toSessionId, meetingCode);

            // Prepare the message to forward
            Map<String, Object> forwardMessage = Map.of(
                    "type", type,
                    "data", data,
                    "fromSessionId", fromSessionId,
                    "targetSessionId", toSessionId != null ? toSessionId : "",
                    "timestamp", LocalDateTime.now().toString()
            );

            // Broadcast to all participants in the meeting
            messagingTemplate.convertAndSend("/topic/meeting/" + meetingCode + "/webrtc-signal", forwardMessage);
            
            log.debug("Broadcast {} signal to meeting {}", type, meetingCode);

        }
        catch (Exception e) {
            log.error("Error handling WebRTC signaling", e);
        }
    }

    @MessageMapping("/meeting/{meetingCode}/chat")
    @SendTo("/topic/meeting/{meetingCode}/chat")
    public Map<String, Object> handleChatMessage(@DestinationVariable String meetingCode,
                                                 @Payload Map<String, String> chatData, Principal principal) {

        try {
            String message = chatData.get("message");
            String senderName = chatData.get("senderName");

            log.debug("Chat message in meeting {}: {} - {}", meetingCode, senderName, message);

            return Map.of(
                    "type", "CHAT_MESSAGE",
                    "message", message,
                    "senderName", senderName,
                    "timestamp", LocalDateTime.now().toString()
            );

        }
        catch (Exception e) {
            log.error("Error handling chat message", e);
        }

        return Map.of("type", "ERROR", "message", "Failed to send message");
    }

    @MessageMapping("/meeting/{meetingCode}/media-state")
    @SendTo("/topic/meeting/{meetingCode}/media-state")
    public Map<String, Object> handleMediaStateChange(@DestinationVariable String meetingCode,
                                                      @Payload Map<String, Object> mediaData,
                                                      @Header("simpSessionId") String sessionId) {

        try {
            Boolean isMuted = (Boolean) mediaData.get("isMuted");
            Boolean videoEnabled = (Boolean) mediaData.get("videoEnabled");
            String participantName = (String) mediaData.get("participantName");

            // Update participant state in database
            participantService.updateParticipantMediaState(sessionId, isMuted, videoEnabled);

            log.debug("Media state updated for {} in meeting {}: muted={}, video={}", participantName, meetingCode, isMuted, videoEnabled);

            return Map.of(
                    "type", "MEDIA_STATE_CHANGED",
                    "sessionId", sessionId,
                    "participantName", participantName,
                    "isMuted", isMuted,
                    "videoEnabled", videoEnabled,
                    "timestamp", LocalDateTime.now().toString()
            );

        }
        catch (Exception e) {
            log.error("Error handling media state change", e);
        }

        return Map.of("type", "ERROR", "message", "Failed to update media state");
    }
}
