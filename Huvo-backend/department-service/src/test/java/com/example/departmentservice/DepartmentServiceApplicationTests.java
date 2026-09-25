package com.example.departmentservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the full context against the in-memory H2 profile so the Jenkins
 * unit-test stage never needs a running MySQL or RabbitMQ - see
 * src/test/resources/application-test.properties.
 */
@ActiveProfiles("test")
@SpringBootTest
class DepartmentServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
