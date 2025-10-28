package com.dharaneesh.video_meeting.service;

import com.dharaneesh.video_meeting.model.Meeting;
import com.dharaneesh.video_meeting.model.Participant;
import com.dharaneesh.video_meeting.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing meeting participants
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ParticipantService {

    private final ParticipantRepository participantRepository;

    /**
     * Adds a participant to a meeting
     */
    public Participant addParticipant(Meeting meeting, String participantName, String sessionId) {
        Participant participant = new Participant();
        participant.setMeeting(meeting);
        participant.setParticipantName(participantName);
        participant.setSessionId(sessionId);
        participant.setIsHost(meeting.getCreatedBy().equals(participantName));

        Participant saved = participantRepository.save(participant);
        log.info("Participant {} joined meeting {}", participantName, meeting.getMeetingCode());

        return saved;
    }

    /**
     * Returns existing active participant with this session or creates one
     */
    public Participant getOrAddParticipant(Meeting meeting, String participantName, String sessionId) {
        Optional<Participant> existing = participantRepository.findBySessionId(sessionId);
        if (existing.isPresent() && existing.get().getLeftAt() == null) {
            return existing.get();
        }
        return addParticipant(meeting, participantName, sessionId);
    }

    /**
     * Removes a participant from meeting
     */
    public void removeParticipant(String sessionId) {
        Optional<Participant> participant = participantRepository.findBySessionId(sessionId);
        if (participant.isPresent()) {
            Participant p = participant.get();
            p.setLeftAt(LocalDateTime.now());
            participantRepository.save(p);

            log.info("Participant {} left meeting {}",
                    p.getParticipantName(), p.getMeeting().getMeetingCode());
        }
    }

    /**
     * Gets active participants for a meeting
     */
    @Transactional(readOnly = true)
    public List<Participant> getActiveParticipants(String meetingCode) {
        return participantRepository.findActiveParticipantsByMeetingCode(meetingCode);
    }

    /**
     * Updates participant media state (mute/video)
     */
    public void updateParticipantMediaState(String sessionId, Boolean isMuted, Boolean videoEnabled) {
        Optional<Participant> participant = participantRepository.findBySessionId(sessionId);
        if (participant.isPresent()) {
            Participant p = participant.get();
            if (isMuted != null) p.setIsMuted(isMuted);
            if (videoEnabled != null) p.setVideoEnabled(videoEnabled);

            participantRepository.save(p);
            log.debug("Updated media state for participant: {}", p.getParticipantName());
        }
    }

    /**
     * Gets participant by session ID
     */
    @Transactional(readOnly = true)
    public Optional<Participant> getParticipantBySessionId(String sessionId) {
        return participantRepository.findBySessionId(sessionId);
    }
}
