package com.stockapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class StockSignalBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(StockSignalBackendApplication.class, args);
	}

}
