package com.dharaneesh.video_meeting.model;

import lombok.Getter;

@Getter
public enum MeetingStatus {
    ACTIVE("Active"),
    ENDED("Ended");

    private final String displayName;

    MeetingStatus(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
