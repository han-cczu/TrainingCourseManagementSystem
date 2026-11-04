package com.example.tcms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TcmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TcmsApplication.class, args);
    }
}
