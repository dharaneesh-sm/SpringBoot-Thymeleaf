package com.dharaneesh.video_meeting.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/** Instead of checking authentication and adding session details in every controller,
    I moved that logic into a single Interceptor. */

@Component
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    //It runs after the controller executes, but before the view is rendered
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, 
                          Object handler, ModelAndView modelAndView) throws Exception {

        /** Checks that there is a view being rendered.
            If the controller returned JSON (like REST), modelAndView might be null. */
        if (modelAndView != null) {
            HttpSession session = request.getSession(false); //(false = don’t create)
            
            if (session != null) {
                // Add session attributes to model for template access
                Boolean authenticated = (Boolean) session.getAttribute("authenticated");
                if (authenticated != null && authenticated) {
                    modelAndView.addObject("sessionAuthenticated", true);
                    modelAndView.addObject("sessionUsername", session.getAttribute("username"));
                    modelAndView.addObject("sessionDisplayName", session.getAttribute("displayName"));
                    modelAndView.addObject("sessionEmail", session.getAttribute("email"));
                    modelAndView.addObject("sessionRole", session.getAttribute("role"));
                }
                else {
                    modelAndView.addObject("sessionAuthenticated", false);
                }
            }
            else {
                modelAndView.addObject("sessionAuthenticated", false);
            }
        }
    }
}