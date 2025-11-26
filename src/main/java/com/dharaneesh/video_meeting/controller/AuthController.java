package com.dharaneesh.video_meeting.controller;

import com.dharaneesh.video_meeting.model.User;
import com.dharaneesh.video_meeting.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;

    @GetMapping("/signin")
    public String signinPage(Model model, HttpSession session) {
        // Redirect to home if already authenticated
        if (isAuthenticated(session)) {
            return "redirect:/";
        }
        return "signin";
    }

    @PostMapping("/signin")
    public String handleSignin(@RequestParam String username, @RequestParam String password,
                               HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            log.info("Sign in attempt for user: {}", username);

            // Validate input
            if (username == null || username.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Username is required");
                return "redirect:/signin";
            }

            if (password == null || password.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Password is required");
                return "redirect:/signin";
            }

            String cleanUsername = username.trim();

            // Validate credentials
            if (userService.validateUserCredentials(cleanUsername, password)) {
                // Get user details
                User user = userService.findByUsername(cleanUsername).orElse(null);
                if (user != null && user.getIsActive()) {
                    // Set session attributes
                    session.setAttribute("authenticated", true);
                    session.setAttribute("username", user.getUsername());
                    session.setAttribute("displayName", user.getDisplayName());
                    session.setAttribute("email", user.getEmail());
                    session.setAttribute("role", user.getRole());

                    log.info("User {} signed in successfully", cleanUsername);
                    redirectAttributes.addFlashAttribute("success", "Welcome back, " + user.getDisplayName() + "!");
                    return "redirect:/";
                } else {
                    redirectAttributes.addFlashAttribute("error", "Account is inactive");
                    return "redirect:/signin";
                }
            } else {
                log.warn("Invalid credentials for user: {}", cleanUsername);
                redirectAttributes.addFlashAttribute("error", "Invalid username or password");
                return "redirect:/signin";
            }

        } catch (Exception e) {
            log.error("Error during sign in", e);
            redirectAttributes.addFlashAttribute("error", "Sign in failed. Please try again.");
            return "redirect:/signin";
        }
    }

    @GetMapping("/signup")
    public String signupPage(Model model, HttpSession session) {
        // Redirect to home if already authenticated
        if (isAuthenticated(session)) {
            return "redirect:/";
        }
        return "signup";
    }

    @PostMapping("/signup")
    public String handleSignup(@RequestParam String username, @RequestParam String email,
                               @RequestParam String password, @RequestParam String confirmPassword,
                               @RequestParam(required = false) String displayName,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        try {
            log.info("Sign up attempt for user: {}", username);

            if (!password.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "Passwords do not match");
                preserveFormData(redirectAttributes, username, email, displayName);
                return "redirect:/signup";
            }

            // Let service handle all other validations
            User user = userService.createUser(username, email, password, displayName);

            // Creates an Session
            session.setAttribute("authenticated", true);
            session.setAttribute("username", user.getUsername());
            session.setAttribute("displayName", user.getDisplayName());
            session.setAttribute("email", user.getEmail());
            session.setAttribute("role", user.getRole());

            log.info("User {} registered and signed in successfully", user.getUsername());
            redirectAttributes.addFlashAttribute("success",
                    "Account created successfully! Welcome, " + user.getDisplayName() + "!");
            return "redirect:/";

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input during sign up: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            preserveFormData(redirectAttributes, username, email, displayName);
            return "redirect:/signup";

        } catch (Exception e) {
            log.error("Error during sign up", e);
            redirectAttributes.addFlashAttribute("error", "Registration failed. Please try again.");
            return "redirect:/signup";
        }
    }

    // Helper method to preserve form data on validation errors
    private void preserveFormData(RedirectAttributes redirectAttributes,
                                  String username, String email, String displayName) {
        if (username != null)
            redirectAttributes.addFlashAttribute("username", username);
        if (email != null)
            redirectAttributes.addFlashAttribute("email", email);
        if (displayName != null)
            redirectAttributes.addFlashAttribute("displayName", displayName);
    }

    @GetMapping("/signout")
    public String signout(HttpSession session, RedirectAttributes redirectAttributes) {
        String username = (String) session.getAttribute("username");
        session.invalidate();
        log.info("User {} signed out", username);
        redirectAttributes.addFlashAttribute("success", "You have been signed out successfully");
        return "redirect:/signin";
    }

    @GetMapping("/profile")
    public String profilePage(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(session)) {
            redirectAttributes.addFlashAttribute("error", "Please sign in to access your profile");
            return "redirect:/signin";
        }

        String username = (String) session.getAttribute("username");
        User user = userService.findByUsername(username).orElse(null);
        
        if (user != null) {
            model.addAttribute("user", user);
            return "profile";
        } else {
            session.invalidate();
            redirectAttributes.addFlashAttribute("error", "User not found. Please sign in again.");
            return "redirect:/signin";
        }
    }

    @PostMapping("/profile/update")
    public String updateProfile(@RequestParam String displayName,
                               @RequestParam String email,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(session)) {
            redirectAttributes.addFlashAttribute("error", "Please sign in to update your profile");
            return "redirect:/signin";
        }

        try {
            String username = (String) session.getAttribute("username");
            User updatedUser = userService.updateUser(username, displayName, email);
            
            // Update session with new data
            session.setAttribute("displayName", updatedUser.getDisplayName());
            session.setAttribute("email", updatedUser.getEmail());
            
            redirectAttributes.addFlashAttribute("success", "Profile updated successfully!");
            return "redirect:/profile";
            
        } catch (Exception e) {
            log.error("Error updating profile", e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/profile";
        }
    }

    @PostMapping("/profile/change-password")
    public String changePassword(@RequestParam String currentPassword,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(session)) {
            redirectAttributes.addFlashAttribute("error", "Please sign in to change your password");
            return "redirect:/signin";
        }

        try {
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "New passwords do not match");
                return "redirect:/profile";
            }

            if (newPassword.length() < 6) {
                redirectAttributes.addFlashAttribute("error", "New password must be at least 6 characters long");
                return "redirect:/profile";
            }

            String username = (String) session.getAttribute("username");
            boolean success = userService.changePassword(username, currentPassword, newPassword);
            
            if (success) {
                redirectAttributes.addFlashAttribute("success", "Password changed successfully!");
            } else {
                redirectAttributes.addFlashAttribute("error", "Current password is incorrect");
            }
            
            return "redirect:/profile";
            
        } catch (Exception e) {
            log.error("Error changing password", e);
            redirectAttributes.addFlashAttribute("error", "Failed to change password. Please try again.");
            return "redirect:/profile";
        }
    }

    // Helper method to check authentication
    private boolean isAuthenticated(HttpSession session) {
        Boolean authenticated = (Boolean) session.getAttribute("authenticated");
        return authenticated != null && authenticated;
    }
}