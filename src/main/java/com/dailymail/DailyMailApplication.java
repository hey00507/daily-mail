package com.dailymail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DailyMailApplication {

	public static void main(String[] args) {
		SpringApplication.run(DailyMailApplication.class, args);
	}
}
