package com.dharaneesh.video_meeting.model;

import lombok.Getter;

/**
 * Enum for meeting status
 * This should be in a separate file or at the top of Meeting.java
 */
@Getter
public enum MeetingStatus {
    ACTIVE("Active"),
    ENDED("Ended"),
    SCHEDULED("Scheduled");

    private final String displayName;

    MeetingStatus(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
