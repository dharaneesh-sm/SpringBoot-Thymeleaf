package com.dharaneesh.video_meeting.exception;

import lombok.Getter;

@Getter
public class WebSocketException extends RuntimeException {
    
    private final String errorType;
    
    public WebSocketException(String message, String errorType) {
        super(message);
        this.errorType = errorType;
    }
    
    public WebSocketException(String message) {
        super(message);
        this.errorType = "GENERAL_ERROR";
    }
}