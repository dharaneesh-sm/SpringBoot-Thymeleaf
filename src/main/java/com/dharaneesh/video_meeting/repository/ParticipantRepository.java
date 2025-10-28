package com.dharaneesh.video_meeting.repository;

import com.dharaneesh.video_meeting.model.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Participant entity
 */
@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    // Find active participants in a meeting
    @Query("SELECT p FROM Participant p WHERE p.meeting.meetingCode = :meetingCode AND p.leftAt IS NULL")
    List<Participant> findActiveParticipantsByMeetingCode(@Param("meetingCode") String meetingCode);

    // Find participant by session ID
    Optional<Participant> findBySessionId(String sessionId);

    // Count active participants in a meeting
    @Query("SELECT COUNT(p) FROM Participant p WHERE p.meeting.meetingCode = :meetingCode AND p.leftAt IS NULL")
    long countActiveParticipantsByMeetingCode(@Param("meetingCode") String meetingCode);
}
