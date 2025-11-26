package com.dharaneesh.video_meeting.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "participants")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** In the `Participant` table, add a column called `meeting_id`,
     Use it as a foreign key referencing the primary key of `Meeting` */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false) //Foreign key column in Participant table
    private Meeting meeting;

    @Column(name = "participant_name", nullable = false, length = 100)
    private String participantName;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "is_host")
    private Boolean isHost = false;

    @Column(name = "is_muted")
    private Boolean isMuted = false;

    @Column(name = "video_enabled")
    private Boolean videoEnabled = true;

    @Column(name = "session_id", unique = true)
    private String sessionId; // For WebSocket session tracking

    // Helper method to check if participant is currently in meeting
    public boolean isActive() {
        return leftAt == null;
    }
}
