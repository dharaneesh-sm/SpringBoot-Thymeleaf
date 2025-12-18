package com.dharaneesh.video_meeting.controller;

import com.dharaneesh.video_meeting.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketService webSocketService;

    // 1. Handle WebRTC Signaling
    @MessageMapping("/meeting/{meetingCode}/webrtc-signal")
    public void handleWebRTCSignaling(@DestinationVariable String meetingCode,
                                      @Payload Map<String, Object> signalData, @Header("simpSessionId") String sessionId) {

        Map<String, Object> forwardMessage = webSocketService.processWebRTCSignal(meetingCode, sessionId, signalData);

        // Manual Broadcasting No @SendTo, Broadcast to all participants in the meeting
        messagingTemplate.convertAndSend("/topic/meeting/" + meetingCode + "/webrtc-signal", forwardMessage);

        log.debug("Broadcast {} signal to meeting {}", forwardMessage.get("type"), meetingCode);
    }

    // 2. Handle Participant Join
    @MessageMapping("/meeting/{meetingCode}/join") //Maps WebSocket messages to handler methods
    @SendTo("/topic/meeting/{meetingCode}/participants") //Server broadcasts message to specified topic
    public Map<String, Object> handleParticipantJoin(@DestinationVariable String meetingCode,
                                                     @Payload Map<String, String> joinData, // Message Body
                                                     @Header("simpSessionId") String sessionId) {
                                                    //A unique ID given by Spring to each WebSocket connection

        String participantName = joinData.get("participantName");
        return webSocketService.processParticipantJoin(meetingCode, participantName, sessionId);
    }

    // 2. Handle Participant Leave
    @MessageMapping("/meeting/{meetingCode}/leave")
    @SendTo("/topic/meeting/{meetingCode}/participants")
    public Map<String, Object> handleParticipantLeave(@DestinationVariable String meetingCode,
                                                      @Header("simpSessionId") String sessionId) {

        return webSocketService.processParticipantLeave(meetingCode, sessionId);
    }

    // 3. Handle Media State
    @MessageMapping("/meeting/{meetingCode}/media-state")
    @SendTo("/topic/meeting/{meetingCode}/media-state")
    public Map<String, Object> handleMediaStateChange(@DestinationVariable String meetingCode,
                                                      @Payload Map<String, Object> mediaData,
                                                      @Header("simpSessionId") String sessionId) {

        Boolean isMuted = (Boolean) mediaData.get("isMuted");
        Boolean videoEnabled = (Boolean) mediaData.get("videoEnabled");
        String participantName = (String) mediaData.get("participantName");

        return webSocketService.processMediaStateChange(meetingCode, sessionId, participantName, isMuted, videoEnabled);
    }

    // 4. Handle Chat Messages
    @MessageMapping("/meeting/{meetingCode}/chat")
    @SendTo("/topic/meeting/{meetingCode}/chat")
    public Map<String, Object> handleChatMessage(@DestinationVariable String meetingCode,
                                                 @Payload Map<String, String> chatData, Principal principal) {

        String message = chatData.get("message");
        String senderName = chatData.get("senderName");

        return webSocketService.processChatMessage(meetingCode, message, senderName);
    }
}
