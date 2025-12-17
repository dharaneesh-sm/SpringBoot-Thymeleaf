package com.dharaneesh.video_meeting.service;

import com.dharaneesh.video_meeting.entity.User;
import com.dharaneesh.video_meeting.exception.CustomException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserService userService;

    public User authenticateUser(String username, String password) {

        if (username == null || username.trim().isEmpty()) {
            throw new CustomException("Username is required", "/signin");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new CustomException("Password is required", "/signin");
        }
        
        String cleanUsername = username.trim();
        
        if (!userService.validateUserCredentials(cleanUsername, password)) {
            throw new CustomException("Invalid username or password", "/signin");
        }

        return userService.findByUsername(cleanUsername)
                .orElseThrow(() -> new CustomException("User not found", "/signin"));
    }

    public User registerUser(String username, String email, String password, String confirmPassword, String displayName) {

        if (!password.equals(confirmPassword)) {
            throw new CustomException("Passwords do not match", "/signup");
        }

        return userService.createUser(username, email, password, displayName);
    }

    public User updateUserProfile(String username, String displayName, String email) {
        return userService.updateUser(username, displayName, email);
    }

    public void changeUserPassword(String username, String currentPassword, String newPassword, String confirmPassword) {

        if (!newPassword.equals(confirmPassword)) {
            throw new CustomException("New passwords do not match", "/profile");
        }
        if (newPassword.length() < 6) {
            throw new CustomException("New password must be at least 6 characters long", "/profile");
        }
        
        if (!userService.changePassword(username, currentPassword, newPassword)) {
            throw new CustomException("Current password is incorrect", "/profile");
        }
    }

    public User getCurrentUser(HttpSession session) {

        if (!isAuthenticated(session)) {
            throw new CustomException("Please sign in to access this page", "/signin");
        }
        
        String username = (String) session.getAttribute("username");
        return userService.findByUsername(username)
                .orElseThrow(() -> new CustomException("User session invalid", "/signin"));
    }

    public void setUserSession(HttpSession session, User user) {
        session.setAttribute("authenticated", true);
        session.setAttribute("username", user.getUsername());
        session.setAttribute("displayName", user.getDisplayName());
        session.setAttribute("email", user.getEmail());
        session.setAttribute("role", user.getRole());
    }

    public void updateSessionData(HttpSession session, User user) {
        session.setAttribute("displayName", user.getDisplayName());
        session.setAttribute("email", user.getEmail());
    }

    public boolean isAuthenticated(HttpSession session) {
        Boolean authenticated = (Boolean) session.getAttribute("authenticated");
        return authenticated != null && authenticated;
    }
}