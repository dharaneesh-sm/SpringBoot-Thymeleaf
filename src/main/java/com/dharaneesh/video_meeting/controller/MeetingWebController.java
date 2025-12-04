package com.dharaneesh.video_meeting.controller;

import com.dharaneesh.video_meeting.model.Meeting;
import com.dharaneesh.video_meeting.model.Participant;
import com.dharaneesh.video_meeting.service.MeetingService;
import com.dharaneesh.video_meeting.service.ParticipantService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MeetingWebController {

    private final MeetingService meetingService;
    private final ParticipantService participantService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/")
    public String home(Model model, HttpSession session) {
        log.debug("Rendering home page");
        
        // Check if user is authenticated
        Boolean authenticated = (Boolean) session.getAttribute("authenticated");
        if (authenticated != null && authenticated) {
            model.addAttribute("authenticated", true);
            model.addAttribute("username", session.getAttribute("username"));
            model.addAttribute("displayName", session.getAttribute("displayName"));
        }
        else {
            model.addAttribute("authenticated", false);
        }
        
        return "index";
    }

    @GetMapping("/create-meeting")
    public String createMeetingPage(HttpSession session, RedirectAttributes redirectAttributes) {
        log.debug("Rendering create meeting page");
        
        // Require authentication for creating meetings
        Boolean authenticated = (Boolean) session.getAttribute("authenticated");
        if (authenticated == null || !authenticated) {
            redirectAttributes.addFlashAttribute("error", "Please sign in to create a meeting");
            return "redirect:/signin";
        }
        
        return "create-meeting";
    }

    @PostMapping("/create-meeting")
    public String handleCreateMeeting(@RequestParam(required = false) String username,
                                      @RequestParam(required = false) String meetingTitle,
                                      HttpSession session,
                                      RedirectAttributes redirectAttributes,
                                      Model model) {
        try {
            // Require authentication for creating meetings
            Boolean authenticated = (Boolean) session.getAttribute("authenticated");
            if (authenticated == null || !authenticated) {
                redirectAttributes.addFlashAttribute("error", "Please sign in to create a meeting");
                return "redirect:/signin";
            }

            // Use authenticated user's username
            String cleanUsername = (String) session.getAttribute("username");
            log.info("Creating meeting via web form: user={}, title={}", cleanUsername, meetingTitle);

            // Create meeting
            Meeting meeting = meetingService.createMeeting(cleanUsername, meetingTitle);

            log.info("Meeting created successfully: {} by {}", meeting.getMeetingCode(), cleanUsername);

            // Add success message
            redirectAttributes.addFlashAttribute("success",
                    "Meeting created successfully! Code: " + meeting.getMeetingCode());

            // Redirect to the meeting room
            return "redirect:/meeting/" + meeting.getMeetingCode() + "?username=" + cleanUsername;

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for creating meeting: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/create-meeting";

        } catch (Exception e) {
            log.error("Error creating meeting via web form", e);
            redirectAttributes.addFlashAttribute("error", "Failed to create meeting. Please try again.");
            return "redirect:/create-meeting";
        }
    }

    @GetMapping("/join-meeting")
    public String joinMeetingPage(Model model,
                                  @RequestParam(required = false) String code,
                                  @RequestParam(required = false) String error) {
        log.debug("Rendering join meeting page");

        // Pre-fill meeting code if provided
        if (code != null && !code.trim().isEmpty()) {
            model.addAttribute("meetingCode", code.trim().toUpperCase());
        }

        // Add error message if provided
        if (error != null && !error.trim().isEmpty()) {
            model.addAttribute("error", error);
        }

        return "join-meeting";
    }

    @PostMapping("/join-meeting")
    public String handleJoinMeeting(@RequestParam String meetingCode,
                                    @RequestParam String username,
                                    RedirectAttributes redirectAttributes) {
        try {
            log.info("Joining meeting via web form: code={}, user={}", meetingCode, username);

            // Validate input
            if (meetingCode == null || meetingCode.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Meeting code is required");
                return "redirect:/join-meeting";
            }

            if (username == null || username.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Username is required");
                return "redirect:/join-meeting";
            }

            String cleanMeetingCode = meetingCode.trim().toUpperCase();
            String cleanUsername = username.trim();

            // Validate username length
            if (cleanUsername.length() < 2 || cleanUsername.length() > 50) {
                redirectAttributes.addFlashAttribute("error", "Username must be between 2 and 50 characters");
                redirectAttributes.addFlashAttribute("meetingCode", cleanMeetingCode);
                return "redirect:/join-meeting";
            }

            // Check if meeting exists and is joinable
            if (!meetingService.isMeetingJoinable(cleanMeetingCode)) {
                redirectAttributes.addFlashAttribute("error", "Meeting not found or not available");
                redirectAttributes.addFlashAttribute("meetingCode", cleanMeetingCode);
                return "redirect:/join-meeting";
            }

            log.info("User {} successfully validated for joining meeting {}", cleanUsername, cleanMeetingCode);

            // Redirect to meeting room
            return "redirect:/meeting/" + cleanMeetingCode + "?username=" + cleanUsername;

        } catch (Exception e) {
            log.error("Error joining meeting via web form", e);
            redirectAttributes.addFlashAttribute("error", "Failed to join meeting. Please try again.");
            if (meetingCode != null) {
                redirectAttributes.addFlashAttribute("meetingCode", meetingCode.trim().toUpperCase());
            }
            return "redirect:/join-meeting";
        }
    }

    @GetMapping("/meeting/{code}")
    public String meetingRoom(@PathVariable String code,
                              @RequestParam String username,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        try {
            log.info("User {} accessing meeting room: {}", username, code);

            // Validate parameters
            if (username == null || username.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Username is required to join meeting");
                return "redirect:/join-meeting?code=" + code;
            }

            String cleanMeetingCode = code.trim().toUpperCase();
            String cleanUsername = username.trim();

            // Validate username length
            if (cleanUsername.length() < 2 || cleanUsername.length() > 50) {
                redirectAttributes.addFlashAttribute("error", "Invalid username length");
                return "redirect:/join-meeting?code=" + cleanMeetingCode;
            }

            // Get meeting details
            Optional<Meeting> meetingOpt = meetingService.getMeetingByCode(cleanMeetingCode);

            if (meetingOpt.isEmpty()) {
                log.warn("Meeting not found: {}", cleanMeetingCode);
                redirectAttributes.addFlashAttribute("error", "Meeting not found");
                return "redirect:/join-meeting";
            }

            Meeting meeting = meetingOpt.get();

            // Check if meeting is joinable
            if (!meetingService.isMeetingJoinable(cleanMeetingCode)) {
                log.warn("Meeting not joinable: {}", cleanMeetingCode);
                redirectAttributes.addFlashAttribute("error", "Meeting is not available");
                return "redirect:/join-meeting";
            }

            // Get current participants
            List<Participant> participants = participantService.getActiveParticipants(cleanMeetingCode);

            // Determine if user is host
            boolean isHost = meeting.getCreatedBy().equals(cleanUsername);

            // Use username as display name (keep it simple)
            String displayName = cleanUsername;

            // Add data to model for Thymeleaf template
            model.addAttribute("pageTitle", "Meeting: " + cleanMeetingCode);
            model.addAttribute("meetingCode", meeting.getMeetingCode());
            model.addAttribute("meetingTitle", meeting.getMeetingTitle() != null ? meeting.getMeetingTitle() : "Video Meeting");
            model.addAttribute("username", cleanUsername);
            model.addAttribute("displayName", displayName);
            model.addAttribute("isHost", isHost);
            model.addAttribute("participantCount", participants.size());
            model.addAttribute("createdBy", meeting.getCreatedBy());
            model.addAttribute("createdAt", meeting.getCreatedAt());

            log.info("Meeting room rendered successfully for user {} in meeting {}", cleanUsername, cleanMeetingCode);

            return "meeting-room";

        } catch (Exception e) {
            log.error("Error accessing meeting room: {} for user: {}", code, username, e);
            redirectAttributes.addFlashAttribute("error", "Error accessing meeting room");
            return "redirect:/";
        }
    }

    // API endpoint for ending meetings
    @PostMapping("/api/meetings/{meetingCode}/end")
    @ResponseBody
    public ResponseEntity<?> endMeeting(@PathVariable String meetingCode,
                                        @RequestParam String username) {
        try {
            String code = meetingCode.trim().toUpperCase();
            String user = username.trim();

            boolean ended = meetingService.endMeeting(code, user);
            if (!ended) {
                log.warn("End meeting denied or not found: code={}, by={}", code, user);
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Only the host can end the meeting or meeting not found"
                ));
            }

            // Broadcast control event to all participants
            Map<String, Object> payload = Map.of(
                    "type", "MEETING_ENDED",
                    "endedBy", user,
                    "timestamp", LocalDateTime.now().toString()
            );
            messagingTemplate.convertAndSend("/topic/meeting/" + code + "/control", payload);

            log.info("Meeting {} ended successfully by {}", code, user);
            return ResponseEntity.ok(Map.of("status", "ended"));
        } catch (Exception e) {
            log.error("Error ending meeting {}", meetingCode, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to end meeting"
            ));
        }
    }

    /** Handle meeting not found errors */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgument(IllegalArgumentException e, RedirectAttributes redirectAttributes) {
        log.warn("Invalid argument in web controller: {}", e.getMessage());
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        return "redirect:/";
    }

    /** Handle general errors */
    @ExceptionHandler(Exception.class)
    public String handleGenericError(Exception e, RedirectAttributes redirectAttributes) {
        log.error("Unexpected error in web controller", e);
        redirectAttributes.addFlashAttribute("error", "An unexpected error occurred. Please try again.");
        return "redirect:/";
    }
}