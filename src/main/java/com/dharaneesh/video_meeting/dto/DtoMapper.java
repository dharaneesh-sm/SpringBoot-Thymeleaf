package com.dharaneesh.video_meeting.dto;

import com.dharaneesh.video_meeting.entity.Meeting;
import com.dharaneesh.video_meeting.entity.Participant;
import com.dharaneesh.video_meeting.entity.User;

import java.util.Optional;

public class DtoMapper {

    private DtoMapper() {}

    public static UserDTO toUserDTO(User user) {
        return new UserDTO(
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail()
        );
    }

    public static MeetingDTO toMeetingDTO(Meeting meeting) {
        return new MeetingDTO(
                meeting.getMeetingCode(),
                meeting.getMeetingTitle(),
                meeting.getCreatedBy(),
                meeting.getCreatedAt(),
                meeting.getEndedAt(),
                meeting.isActive()
        );
    }

    public static ParticipantDTO toParticipantDTO(Participant participant) {
        return new ParticipantDTO(
                participant.getParticipantName(),
                participant.getIsHost(),
                participant.getIsMuted(),
                participant.getVideoEnabled(),
                participant.getSessionId()
        );
    }
}
