package com.example.postapi;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;

import java.security.SecureRandom;
import java.util.Base64;

@SpringBootTest
class PostapiApplicationTests {

	private static final String JWT_TEST_KEY = randomKey();
	private static final String AES_TEST_KEY = randomKey();

	@DynamicPropertySource
	static void testConfiguration(DynamicPropertyRegistry registry) {
		registry.add("spring.data.mongodb.uri", () -> "mongodb://localhost:27017/postsapi_experiment7_tests");
		registry.add("app.jwt.secret-base64", () -> JWT_TEST_KEY);
		registry.add("app.crypto.key-base64", () -> AES_TEST_KEY);
	}

	private static String randomKey() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return Base64.getEncoder().encodeToString(key);
	}

	@Test
	void contextLoads() {
	}

}
