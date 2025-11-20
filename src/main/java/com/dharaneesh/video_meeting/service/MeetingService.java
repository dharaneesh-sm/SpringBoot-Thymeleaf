package com.dharaneesh.video_meeting.service;

import com.dharaneesh.video_meeting.model.Meeting;
import com.dharaneesh.video_meeting.model.MeetingStatus;
import com.dharaneesh.video_meeting.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.Optional;

@Service
@RequiredArgsConstructor // Lombok annotation for constructor injection
@Slf4j // Logging support
@Transactional // Default transaction behavior
public class MeetingService {

    private final MeetingRepository meetingRepository;

    public Meeting createMeeting(String username, String meetingTitle) {
        log.info("Creating new meeting for user: {}", username);

        String code = generateUniqueMeetingCode();
        Meeting meeting = new Meeting();
        meeting.setMeetingCode(code);
        meeting.setCreatedBy(username);
        meeting.setMeetingTitle(meetingTitle);
        meeting.setStatus(MeetingStatus.ACTIVE);

        Meeting savedMeeting = meetingRepository.save(meeting);
        log.info("Meeting created successfully with code: {}", code);

        return savedMeeting;
    }

    @Transactional(readOnly = true)
    public Optional<Meeting> getMeetingByCode(String code) {
        log.debug("Looking for meeting with code: {}", code);
        return meetingRepository.findByMeetingCode(code.toUpperCase());
    }

    public boolean isMeetingJoinable(String code) {
        Optional<Meeting> meeting = getMeetingByCode(code);
        if (meeting.isPresent()) {
            Meeting m = meeting.get();
            return m.isActive();
        }
        return false;
    }

    public boolean endMeeting(String meetingCode, String username) {
        Optional<Meeting> meetingOpt = getMeetingByCode(meetingCode);
        if (meetingOpt.isPresent()) {
            Meeting meeting = meetingOpt.get();

            // Only creator can end the meeting
            if (meeting.getCreatedBy().equals(username)) {
                meeting.setStatus(MeetingStatus.ENDED);
                meeting.setEndedAt(LocalDateTime.now());
                meetingRepository.save(meeting);

                log.info("Meeting {} ended by creator: {}", meetingCode, username);
                return true;
            }
        }
        return false;
    }

    private String generateUniqueMeetingCode() {
        String code;
        int attempts = 0;
        int maxAttempts = 10;

        do {
            // Generate 8-character alphanumeric code
            code = RandomStringUtils.randomAlphanumeric(8).toUpperCase();
            attempts++;

            if (attempts >= maxAttempts) {
                // If too many attempts, increase code length
                code = RandomStringUtils.randomAlphanumeric(10).toUpperCase();
                break;
            }
        } while (meetingRepository.findByMeetingCode(code).isPresent());

        log.debug("Generated unique meeting code: {} in {} attempts", code, attempts);
        return code;
    }

}
