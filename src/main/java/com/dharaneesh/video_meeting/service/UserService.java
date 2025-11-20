package com.dharaneesh.video_meeting.service;

import com.dharaneesh.video_meeting.model.User;
import com.dharaneesh.video_meeting.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User createUser(String username, String email, String password, String displayName) {
        log.info("Creating new user: {}", username);

        // Comprehensive validation in service layer
        validateUserInput(username, email, password);

        // Clean and prepare data
        String cleanUsername = username.trim();
        String cleanEmail = email.trim().toLowerCase();
        String cleanDisplayName = displayName != null ? displayName.trim() : cleanUsername;

        // Business logic validations
        validateUsernameRules(cleanUsername);
        validateEmailRules(cleanEmail);
        validatePasswordRules(password);
        checkUserUniqueness(cleanUsername, cleanEmail);

        // Create new user
        User user = new User();
        user.setUsername(cleanUsername);
        user.setEmail(cleanEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setDisplayName(cleanDisplayName);
        user.setCreatedAt(LocalDateTime.now());
        user.setIsActive(true);
        user.setRole("ROLE_USER");

        User savedUser = userRepository.save(user);
        log.info("User created successfully: {}", savedUser.getUsername());

        return savedUser;
    }

    // Validation helper methods
    private void validateUserInput(String username, String email, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
    }

    private void validateUsernameRules(String username) {
        if (username.length() < 3 || username.length() > 50) {
            throw new IllegalArgumentException("Username must be between 3 and 50 characters");
        }
    }

    private void validateEmailRules(String email) {
        if (!email.contains("@") || !email.contains(".") || email.length() < 5) {
            throw new IllegalArgumentException("Please enter a valid email address");
        }
    }

    private void validatePasswordRules(String password) {
        if (password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
    }

    private void checkUserUniqueness(String username, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username.trim());
    }

    public User updateUser(String username, String displayName, String email) {
        Optional<User> userOpt = findByUsername(username);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        User user = userOpt.get();

        // Update display name if provided
        if (displayName != null && !displayName.trim().isEmpty()) {
            user.setDisplayName(displayName.trim());
        }

        // Update email if provided and different
        if (email != null && !email.trim().isEmpty() && !email.equals(user.getEmail())) {
            if (userRepository.existsByEmail(email.trim().toLowerCase())) {
                throw new IllegalArgumentException("Email already exists");
            }
            user.setEmail(email.trim().toLowerCase());
        }

        User updatedUser = userRepository.save(user);
        log.info("User updated successfully: {}", updatedUser.getUsername());

        return updatedUser;
    }

    public boolean validateUserCredentials(String username, String password) {
        try {
            Optional<User> userOpt = findByUsername(username);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                return user.getIsActive() && passwordEncoder.matches(password, user.getPassword());
            }
            return false;
        } catch (Exception e) {
            log.error("Error validating user credentials for: {}", username, e);
            return false;
        }
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        try {
            Optional<User> userOpt = findByUsername(username);
            if (userOpt.isPresent()) {
                User user = userOpt.get();

                // Verify old password
                if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                    return false;
                }

                // Update password
                user.setPassword(passwordEncoder.encode(newPassword));
                userRepository.save(user);

                log.info("Password changed successfully for user: {}", username);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Error changing password for user: {}", username, e);
            return false;
        }
    }
}
