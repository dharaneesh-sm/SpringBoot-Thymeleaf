package com.dharaneesh.video_meeting.controller;

import com.dharaneesh.video_meeting.entity.User;
import com.dharaneesh.video_meeting.service.AuthService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/signin")
    public String signinPage(HttpSession session) {
        // Redirect to home if already authenticated
        if (authService.isAuthenticated(session))
            return "redirect:/";

        return "signin";
    }

    @PostMapping("/signin")
    public String handleSignin(@RequestParam String username, @RequestParam String password,
                               HttpSession session, RedirectAttributes redirectAttributes) {

        User user = authService.authenticateUser(username, password);
        authService.setUserSession(session, user);

        redirectAttributes.addFlashAttribute(
                "success",
                "Welcome back, " + user.getDisplayName() + "!"
        );

        return "redirect:/";
    }

    @GetMapping("/signup")
    public String signupPage(HttpSession session) {
        // Redirect to home if already authenticated
        if (authService.isAuthenticated(session))
            return "redirect:/";

        return "signup";
    }

    @PostMapping("/signup")
    public String handleSignup(@RequestParam String username, @RequestParam String email,
                               @RequestParam String password, @RequestParam String confirmPassword,
                               @RequestParam(required = false) String displayName,
                               HttpSession session, RedirectAttributes redirectAttributes) {

        User user = authService.registerUser(username, email, password, confirmPassword, displayName);
        authService.setUserSession(session, user);

        redirectAttributes.addFlashAttribute(
                "success",
                "Account created successfully! Welcome, " + user.getDisplayName()
        );

        return "redirect:/";
    }

    @GetMapping("/signout")
    public String signout(HttpSession session, RedirectAttributes redirectAttributes) {

        String username = (String) session.getAttribute("username");
        session.invalidate();

        redirectAttributes.addFlashAttribute(
                "success",
                "You have been signed out successfully");

        return "redirect:/signin";
    }

    @GetMapping("/profile")
    public String profilePage(Model model, HttpSession session) {

        User user = authService.getCurrentUser(session);
        model.addAttribute("user", user);
        return "profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@RequestParam String displayName, @RequestParam String email,
                               HttpSession session, RedirectAttributes redirectAttributes) {

        String username = (String) session.getAttribute("username");
        User updatedUser = authService.updateUserProfile(username, displayName, email);
        authService.updateSessionData(session, updatedUser);

        redirectAttributes.addFlashAttribute("success", "Profile updated successfully!");
        return "redirect:/profile";
    }

    @PostMapping("/profile/change-password")
    public String changePassword(@RequestParam String currentPassword, @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 HttpSession session, RedirectAttributes redirectAttributes) {

        String username = (String) session.getAttribute("username");
        authService.changeUserPassword(username, currentPassword, newPassword, confirmPassword);

        redirectAttributes.addFlashAttribute("success", "Password changed successfully!");
        return "redirect:/profile";
    }
}