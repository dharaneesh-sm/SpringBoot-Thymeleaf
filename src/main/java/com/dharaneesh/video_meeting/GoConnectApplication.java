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
                "   🌐 Application URL: http://localhost:8080\n" +
                "   🗄️  H2 Console: http://localhost:8080/h2-console\n" +
                "   📊 Management: http://localhost:8080/actuator\n" +
                "   \n" +
                "   Ready to host video meetings! 🎥\n" +
                "=================================================\n"
        );
	}
}
