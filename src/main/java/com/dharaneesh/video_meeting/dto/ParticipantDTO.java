package com.dharaneesh.video_meeting.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
public class ParticipantDTO {
    private String participantName;
    private Boolean isHost;
    private Boolean isMuted;
    private Boolean videoEnabled;
    private String sessionId;
}
