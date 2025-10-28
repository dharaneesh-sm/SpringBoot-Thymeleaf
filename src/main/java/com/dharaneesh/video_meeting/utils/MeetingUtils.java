package com.dharaneesh.video_meeting.utils;

import java.security.SecureRandom;
import java.util.regex.Pattern;

public class MeetingUtils {

    private static final String ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom random = new SecureRandom();
    private static final Pattern MEETING_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{6,10}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\.\\s]{2,50}$");

    public static String generateMeetingCode(int length) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append(ALLOWED_CHARS.charAt(random.nextInt(ALLOWED_CHARS.length())));
        }
        return code.toString();
    }

    public static boolean isValidMeetingCode(String code) {
        return code != null && MEETING_CODE_PATTERN.matcher(code.toUpperCase()).matches();
    }

    public static boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    public static String cleanMeetingCode(String code) {
        if (code == null) return null;
        return code.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    public static String cleanUsername(String username) {
        if (username == null) return null;
        return username.trim().replaceAll("\\s+", " ");
    }

    public static String generateSessionId() {
        return "session_" + System.currentTimeMillis() + "_" + random.nextInt(10000);
    }
}
