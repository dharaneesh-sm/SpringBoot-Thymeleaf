package com.dharaneesh.video_meeting.repository;

import com.dharaneesh.video_meeting.model.Meeting;
import com.dharaneesh.video_meeting.model.MeetingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    // Find meeting by code (existing method)
    Optional<Meeting> findByMeetingCode(String meetingCode);

    // Find active meetings by creator
    List<Meeting> findByCreatedByAndStatus(String createdBy, MeetingStatus status);

    // Find meetings created within a time range
    List<Meeting> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // Removed capacity constraint: return active meetings by status
    @Query("SELECT m FROM Meeting m WHERE m.status = :status")
    List<Meeting> findAvailableMeetings(@Param("status") MeetingStatus status);

    // Find expired meetings (created more than 24 hours ago and still active)
    @Query("SELECT m FROM Meeting m WHERE m.status = 'ACTIVE' AND m.createdAt < :expireTime")
    List<Meeting> findExpiredMeetings(@Param("expireTime") LocalDateTime expireTime);
}
