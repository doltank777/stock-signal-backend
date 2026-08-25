package com.stockapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class StockSignalBackendApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(
				StockSignalBackendApplication.class, args);
		closeAfterBatch(context);
	}

	static void closeAfterBatch(ConfigurableApplicationContext context) {
		if (context.getEnvironment().acceptsProfiles(
				Profiles.of("daily-price-load | daily-price-update | screening-run "
						+ "| schema-validate | daily-history-bootstrap"))) {
			context.close();
		}
	}

}
