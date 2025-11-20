package com.dharaneesh.video_meeting.repository;

import com.dharaneesh.video_meeting.model.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    // Find meeting by code (existing method)
    Optional<Meeting> findByMeetingCode(String meetingCode);

}
