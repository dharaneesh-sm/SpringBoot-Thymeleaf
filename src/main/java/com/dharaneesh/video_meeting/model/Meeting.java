package com.dharaneesh.video_meeting.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.HashSet;

@Entity
@Table(name = "meetings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_code", unique = true, nullable = false, length = 10)
    private String meetingCode;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "meeting_title", length = 200)
    private String meetingTitle; // Optional title for the meeting

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "ended_at")
    private LocalDateTime endedAt; // When meeting was ended

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MeetingStatus status = MeetingStatus.ACTIVE;

    // maxParticipants removed – unlimited participants

    // One-to-many relationship with participants
    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Participant> participants = new HashSet<>();

    // Helper method to check if meeting is active
    public boolean isActive() {
        return status == MeetingStatus.ACTIVE && endedAt == null;
    }

    // Helper method to get participant count
    public int getParticipantCount() {
        return participants.size();
    }
}

