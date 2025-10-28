package com.dharaneesh.video_meeting.controller;

import com.dharaneesh.video_meeting.model.Meeting;
import com.dharaneesh.video_meeting.model.Participant;
import com.dharaneesh.video_meeting.service.MeetingService;
import com.dharaneesh.video_meeting.service.ParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MeetingWebController {

    private final MeetingService meetingService;
    private final ParticipantService participantService;

    @GetMapping("/")
    public String home(Model model) {
        log.debug("Rendering home page");
        return "index";
    }

    @GetMapping("/create-meeting")
    public String createMeetingPage() {
        log.debug("Rendering create meeting page");
        return "create-meeting";
    }

    @PostMapping("/create-meeting")
    public String handleCreateMeeting(@RequestParam String username,
                                      @RequestParam(required = false) String meetingTitle,
                                      RedirectAttributes redirectAttributes,
                                      Model model) {
        try {
            log.info("Creating meeting via web form: user={}, title={}", username, meetingTitle);

            // Validate input
            if (username == null || username.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Username is required");
                return "redirect:/create-meeting";
            }

            // Validate username length
            String cleanUsername = username.trim();
            if (cleanUsername.length() < 2 || cleanUsername.length() > 50) {
                redirectAttributes.addFlashAttribute("error", "Username must be between 2 and 50 characters");
                return "redirect:/create-meeting";
            }

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

    /** Handle joining meeting from web form **/
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

    /** Meeting room page - the main video conferencing interface **/
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

            // Add meeting metadata for JavaScript
            model.addAttribute("meetingData", createMeetingJSData(meeting, cleanUsername, isHost, participants.size()));

            log.info("Meeting room rendered successfully for user {} in meeting {}", cleanUsername, cleanMeetingCode);

            return "meeting-room";

        } catch (Exception e) {
            log.error("Error accessing meeting room: {} for user: {}", code, username, e);
            redirectAttributes.addFlashAttribute("error", "Error accessing meeting room");
            return "redirect:/";
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

    /** Create meeting data for JavaScript */
    private String createMeetingJSData(Meeting meeting, String username, boolean isHost, int participantCount) {
        try {
            // Create a JSON-like string for JavaScript
            StringBuilder jsData = new StringBuilder();
            jsData.append("{");
            jsData.append("\"meetingCode\":\"").append(meeting.getMeetingCode()).append("\",");
            jsData.append("\"username\":\"").append(escapeJavaScript(username)).append("\",");
            jsData.append("\"isHost\":").append(isHost).append(",");
            jsData.append("\"participantCount\":").append(participantCount).append(",");
            jsData.append("\"createdBy\":\"").append(escapeJavaScript(meeting.getCreatedBy())).append("\"");
            if (meeting.getMeetingTitle() != null) {
                jsData.append(",\"meetingTitle\":\"").append(escapeJavaScript(meeting.getMeetingTitle())).append("\"");
            }
            jsData.append("}");

            return jsData.toString();
        }
        catch (Exception e) {
            log.warn("Error creating JS data for meeting: {}", meeting.getMeetingCode(), e);
            // Return minimal safe data
            return String.format("{\"meetingCode\":\"%s\",\"username\":\"%s\",\"isHost\":%b}",
                    meeting.getMeetingCode(), escapeJavaScript(username), isHost);
        }
    }

    /** Escape JavaScript strings to prevent XSS */
    private String escapeJavaScript(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}