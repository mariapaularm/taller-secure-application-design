package com.securespring;

import java.util.Collections;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SecureSpring {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SecureSpring.class);
        app.setDefaultProperties(Collections.singletonMap("server.port", Integer.toString(getPort())));
        app.run(args);
    }

    static int getPort() {
        if (System.getenv("PORT") != null) {
            return Integer.parseInt(System.getenv("PORT"));
        }
        return 5000;
    }
}