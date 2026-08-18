package com.stockapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class StockSignalBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
