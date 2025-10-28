package com.dharaneesh.video_meeting.service;

import com.dharaneesh.video_meeting.model.User;
import com.dharaneesh.video_meeting.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing user operations
 * Implements UserDetailsService for Spring Security integration
 *
 * Note: This is a basic implementation. In a real application,
 * you might want to add more sophisticated user management features.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // You'll need to configure this in SecurityConfig

    /**
     * Create a new user account
     * This method handles user registration
     */
    public User createUser(String username, String email, String password, String displayName) {
        log.info("Creating new user: {}", username);

        // Validate input
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }

        // Check if user already exists
        if (userRepository.existsByUsername(username.trim())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(email.trim())) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Create new user
        User user = new User();
        user.setUsername(username.trim());
        user.setEmail(email.trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(password)); // Encrypt password
        user.setDisplayName(displayName != null ? displayName.trim() : username.trim());
        user.setCreatedAt(LocalDateTime.now());
        user.setIsActive(true);
        user.setRole("ROLE_USER");

        User savedUser = userRepository.save(user);
        log.info("User created successfully: {}", savedUser.getUsername());

        return savedUser;
    }

    /**
     * Create a guest user (for anonymous meeting participation)
     * This allows users to join meetings without full registration
     */
    public User createGuestUser(String displayName) {
        log.info("Creating guest user: {}", displayName);

        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Display name cannot be empty for guest user");
        }

        // Generate unique username for guest
        String guestUsername = "guest_" + System.currentTimeMillis();

        // Ensure uniqueness
        while (userRepository.existsByUsername(guestUsername)) {
            guestUsername = "guest_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
        }

        User guestUser = new User();
        guestUser.setUsername(guestUsername);
        guestUser.setEmail(guestUsername + "@guest.local"); // Dummy email for guests
        guestUser.setPassword(passwordEncoder.encode("guest_password")); // Dummy password
        guestUser.setDisplayName(displayName.trim());
        guestUser.setCreatedAt(LocalDateTime.now());
        guestUser.setIsActive(true);
        guestUser.setRole("ROLE_GUEST");

        User savedUser = userRepository.save(guestUser);
        log.info("Guest user created successfully: {}", savedUser.getDisplayName());

        return savedUser;
    }

    /**
     * Find user by username
     */
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username.trim());
    }

    /**
     * Find user by email
     */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    /**
     * Check if username exists
     */
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return userRepository.existsByUsername(username.trim());
    }

    /**
     * Check if email exists
     */
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return userRepository.existsByEmail(email.trim().toLowerCase());
    }

    /**
     * Update user profile
     */
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

    /**
     * Activate or deactivate user
     */
    public void setUserActive(String username, boolean active) {
        Optional<User> userOpt = findByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setIsActive(active);
            userRepository.save(user);
            log.info("User {} set to active: {}", username, active);
        }
    }

    /**
     * Get all active users
     */
    @Transactional(readOnly = true)
    public List<User> getAllActiveUsers() {
        return userRepository.findAll().stream()
                .filter(User::getIsActive)
                .toList();
    }

    /**
     * Delete user (soft delete by deactivating)
     */
    public void deleteUser(String username) {
        setUserActive(username, false);
        log.info("User soft-deleted: {}", username);
    }

    /**
     * Spring Security UserDetailsService implementation
     * This method is called during authentication
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (!user.getIsActive()) {
            throw new UsernameNotFoundException("User is inactive: " + username);
        }

        log.debug("User loaded successfully: {}", username);
        return user;
    }

    /**
     * Validate user credentials (for custom authentication)
     */
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

    /**
     * Change user password
     */
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

    /**
     * Get user display name for meeting purposes
     * Falls back to username if display name is not set
     */
    @Transactional(readOnly = true)
    public String getUserDisplayName(String username) {
        Optional<User> userOpt = findByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            return user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()
                    ? user.getDisplayName()
                    : user.getUsername();
        }
        return username; // Fallback for guest users or non-registered users
    }
}
