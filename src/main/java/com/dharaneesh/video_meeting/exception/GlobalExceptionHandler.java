package com.dharaneesh.video_meeting.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    // HTTP Exception Handlers
    @ExceptionHandler(CustomException.class)
    public String handleCustomException(CustomException exception, RedirectAttributes redirectAttributes) {
        log.warn("Custom exception: {} -> {}", exception.getMessage(), exception.getRedirectUrl());
        redirectAttributes.addFlashAttribute("error", exception.getMessage());
        return "redirect:" + exception.getRedirectUrl();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgument(IllegalArgumentException exception, RedirectAttributes redirectAttributes) {
        log.warn("Validation error: {}", exception.getMessage());
        redirectAttributes.addFlashAttribute("error", exception.getMessage());
        return "redirect:/";
    }

    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception exception, RedirectAttributes redirectAttributes) {
        log.error("Unexpected error occurred", exception);
        redirectAttributes.addFlashAttribute("error", "Something went wrong. Please try again.");
        return "redirect:/";
    }

    // WebSocket Exception Handlers
    @MessageExceptionHandler(WebSocketException.class)
    @SendToUser("/queue/errors")
    public Map<String, Object> handleWebSocketException(WebSocketException exception) {
        log.warn("WebSocket error [{}]: {}", exception.getErrorType(), exception.getMessage());
        
        return Map.of(
                "type", "ERROR",
                "errorType", exception.getErrorType(),
                "message", exception.getMessage(),
                "timestamp", LocalDateTime.now().toString()
        );
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser("/queue/errors")
    public Map<String, Object> handleWebSocketValidationError(IllegalArgumentException exception) {
        log.warn("WebSocket validation error: {}", exception.getMessage());
        
        return Map.of(
                "type", "ERROR",
                "errorType", "VALIDATION_ERROR",
                "message", exception.getMessage(),
                "timestamp", LocalDateTime.now().toString()
        );
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public Map<String, Object> handleWebSocketGenericException(Exception exception) {
        log.error("Unexpected WebSocket error", exception);
        
        return Map.of(
                "type", "ERROR",
                "errorType", "INTERNAL_ERROR",
                "message", "An unexpected error occurred",
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
