package com.dharaneesh.video_meeting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GoConnectApplication {

	public static void main(String[] args) {
		SpringApplication.run(GoConnectApplication.class, args);

        System.out.println("\n" +
                "=================================================\n" +
                "   Video Meeting Application Started Successfully!\n" +
                "   \n" +
                "   Application URL: http://localhost:8080\n" +
                "   \n" +
                "   Ready to host video meetings! \n" +
                "=================================================\n"
        );
	}
}
