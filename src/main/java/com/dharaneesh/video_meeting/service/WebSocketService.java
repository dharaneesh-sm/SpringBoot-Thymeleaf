package com.dharaneesh.video_meeting.service;

import com.dharaneesh.video_meeting.dto.DtoMapper;
import com.dharaneesh.video_meeting.dto.ParticipantDTO;
import com.dharaneesh.video_meeting.entity.Meeting;
import com.dharaneesh.video_meeting.entity.Participant;
import com.dharaneesh.video_meeting.exception.WebSocketException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketService {

    private final MeetingService meetingService;
    private final ParticipantService participantService;

    public Map<String, Object> processParticipantJoin(String meetingCode, String participantName, String sessionId) {
        Optional<Meeting> meetingOpt = meetingService.getMeetingByCode(meetingCode);
        
        if (meetingOpt.isEmpty()) {
            throw new WebSocketException("Meeting not found", "MEETING_NOT_FOUND");
        }

        Meeting meeting = meetingOpt.get();
        boolean isHost = meeting.getCreatedBy().equals(participantName);

        Participant participant = participantService.getOrAddParticipant(meeting, participantName, sessionId);
        participant.setIsHost(isHost);

        List<Participant> participants = participantService.getActiveParticipants(meetingCode);
        List<ParticipantDTO> participantList = participants.stream()
                .map(p -> DtoMapper.toParticipantDTO(p))
                .collect(Collectors.toList());

        log.info("Participant {} joined successfully. Total participants: {}", participantName, participants.size());

        return Map.of(
                "type", "PARTICIPANT_JOINED",
                "participant", DtoMapper.toParticipantDTO(participant),
                "participants", participantList,
                "participantCount", participants.size(),
                "timestamp", LocalDateTime.now().toString()
        );
    }

    public Map<String, Object> processParticipantLeave(String meetingCode, String sessionId) {
        log.info("Participant with session {} leaving meeting {}", sessionId, meetingCode);

        participantService.removeParticipant(sessionId);

        List<Participant> participants = participantService.getActiveParticipants(meetingCode);
        List<ParticipantDTO> participantList = participants.stream()
                .map(p -> DtoMapper.toParticipantDTO(p))
                .collect(Collectors.toList());

        return Map.of(
                "type", "PARTICIPANT_LEFT",
                "sessionId", sessionId,
                "participants", participantList,
                "participantCount", participants.size(),
                "timestamp", LocalDateTime.now().toString()
        );
    }

    public Map<String, Object> processMediaStateChange(String meetingCode, String sessionId, String participantName, 
                                                      Boolean isMuted, Boolean videoEnabled) {
        participantService.updateParticipantMediaState(sessionId, isMuted, videoEnabled);

        log.debug("Media state updated for {} in meeting {}: muted={}, video={}", 
                 participantName, meetingCode, isMuted, videoEnabled);

        return Map.of(
                "type", "MEDIA_STATE_CHANGED",
                "sessionId", sessionId,
                "participantName", participantName,
                "isMuted", isMuted,
                "videoEnabled", videoEnabled,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    public Map<String, Object> processChatMessage(String meetingCode, String message, String senderName) {
        if (message == null || message.trim().isEmpty()) {
            throw new WebSocketException("Message cannot be empty", "INVALID_MESSAGE");
        }

        log.debug("Chat message in meeting {}: {} - {}", meetingCode, senderName, message);

        return Map.of(
                "type", "CHAT_MESSAGE",
                "message", message.trim(),
                "senderName", senderName,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    public Map<String, Object> processWebRTCSignal(String meetingCode, String sessionId, Map<String, Object> signalData) {

        String toSessionId = (String) signalData.get("targetSessionId");
        String type = (String) signalData.get("type");
        Object data = signalData.get("data");

        if (type == null || type.trim().isEmpty()) {
            throw new WebSocketException("Signal type is required", "INVALID_SIGNAL");
        }

        log.debug("WebRTC signal {} from {} to {} in meeting {}", type, sessionId, toSessionId, meetingCode);

        return Map.of(
                "type", type,
                "data", data,
                "fromSessionId", sessionId,
                "targetSessionId", toSessionId != null ? toSessionId : "",
                "timestamp", LocalDateTime.now().toString()
        );
    }
}