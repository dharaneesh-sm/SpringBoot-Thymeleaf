package com.dharaneesh.video_meeting.controller;

import com.dharaneesh.video_meeting.model.MessageModel;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    @MessageMapping("/chat.sendMessage") // client sends here
    @SendTo("/topic/messages") // server broadcasts here
    public MessageModel sendMessage(MessageModel message) {
        // Here we can also add timestamps, filter, or save to DB
        return message;
    }

}
