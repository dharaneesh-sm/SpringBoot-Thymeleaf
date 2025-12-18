package com.dharaneesh.video_meeting.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class MeetingDTO {

    private String meetingCode;
    private String meetingTitle;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime endedAt;
    private boolean active;
}
