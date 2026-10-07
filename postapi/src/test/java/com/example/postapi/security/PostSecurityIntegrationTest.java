package com.example.postapi.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.net.HttpCookie;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.postapi.model.Post;
import com.example.postapi.model.UserAccount;
import com.example.postapi.repository.PostRepository;
import com.example.postapi.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class PostSecurityIntegrationTest {

    private static final String JWT_TEST_KEY = randomKey();
    private static final String AES_TEST_KEY = randomKey();
    private static final String TEST_DATABASE = "postsapi_security_tests";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenService jwtTokenService;

    private UserAccount owner;
    private UserAccount otherUser;
    private UserAccount administrator;

    @DynamicPropertySource
    static void testConfiguration(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> "mongodb://localhost:27017/" + TEST_DATABASE);
        registry.add("app.jwt.secret-base64", () -> JWT_TEST_KEY);
        registry.add("app.crypto.key-base64", () -> AES_TEST_KEY);
    }

    @BeforeEach
    void setUp() {
        String runId = UUID.randomUUID().toString();
        owner = saveUser("owner-" + runId, Set.of("USER"));
        otherUser = saveUser("other-" + runId, Set.of("USER"));
        administrator = saveUser("admin-" + runId, Set.of("ADMIN"));
    }

    @Test
    void protectsReadsAndReturnsApiResponseForMissingAndInvalidTokens() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isUnauthorized())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Authentication required"));

        mockMvc.perform(get("/api/posts")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));

        mockMvc.perform(get("/api/posts")
                        .header("Authorization", bearer(owner, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        mockMvc.perform(get("/api/posts"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void derivesNewPostOwnerFromTokenAndRejectsOtherUsersWrites() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(owner, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"owned post\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ownerId").value(owner.getId()))
                .andReturn();
        String postId = jsonValue(created, "data.id");

        mockMvc.perform(put("/api/posts/" + postId)
                        .header("Authorization", bearer(otherUser, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"unauthorized edit\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"));

        mockMvc.perform(put("/api/posts/" + postId)
                        .header("Authorization", bearer(administrator, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"admin edit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("admin edit"));
    }

    @Test
    void permitsOnlyAdminsToModifyLegacyPostsAndRejectsRoleEscalation() throws Exception {
        Post legacy = postRepository.save(new Post("legacy ownerless post"));

        mockMvc.perform(put("/api/posts/" + legacy.getId())
                        .header("Authorization", bearer(owner, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"user edit\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/posts/" + legacy.getId())
                        .header("Authorization", bearer(administrator, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"admin edit\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"escalation-test\",\"password\":\"a-long-safe-password\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

            @Test
            void rotatesRefreshCookiesDetectsReuseAndRevokesOnLogout() throws Exception {
                MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + owner.getUsername()
                            + "\",\"password\":\"incorrect-password\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid username or password"))
                    .andReturn();
                MvcResult unknownUsername = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"missing-" + UUID.randomUUID()
                            + "\",\"password\":\"incorrect-password\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid username or password"))
                    .andReturn();
                org.junit.jupiter.api.Assertions.assertEquals(wrongPassword.getResponse().getContentAsString(),
                    unknownUsername.getResponse().getContentAsString());

            MvcResult login = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + owner.getUsername()
                        + "\",\"password\":\"safe-test-password-123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Secure")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Path=/api/auth")))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
            String firstCookie = cookieValue(login);
            String loginJson = login.getResponse().getContentAsString();
            org.junit.jupiter.api.Assertions.assertFalse(loginJson.contains("refreshToken"));

            MvcResult refresh = mockMvc.perform(post("/api/auth/refresh")
                    .cookie(new MockCookie("POST_REFRESH", firstCookie))
                    .header("Origin", "http://localhost:8080")
                    .header("X-CSRF-Protection", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
            String rotatedCookie = cookieValue(refresh);
            org.junit.jupiter.api.Assertions.assertNotEquals(firstCookie, rotatedCookie);

            mockMvc.perform(post("/api/auth/refresh")
                    .cookie(new MockCookie("POST_REFRESH", firstCookie))
                    .header("Origin", "http://localhost:8080")
                    .header("X-CSRF-Protection", "1"))
                .andExpect(status().isUnauthorized());

            mockMvc.perform(post("/api/auth/refresh")
                    .cookie(new MockCookie("POST_REFRESH", rotatedCookie))
                    .header("Origin", "http://localhost:8080")
                    .header("X-CSRF-Protection", "1"))
                .andExpect(status().isUnauthorized());

            MvcResult secondLogin = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + owner.getUsername()
                        + "\",\"password\":\"safe-test-password-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
            String logoutCookie = cookieValue(secondLogin);

            mockMvc.perform(post("/api/auth/logout")
                    .cookie(new MockCookie("POST_REFRESH", logoutCookie))
                    .header("Origin", "http://localhost:8080")
                    .header("X-CSRF-Protection", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

            mockMvc.perform(post("/api/auth/refresh")
                    .cookie(new MockCookie("POST_REFRESH", logoutCookie))
                    .header("Origin", "http://localhost:8080")
                    .header("X-CSRF-Protection", "1"))
                .andExpect(status().isUnauthorized());
            }

            @Test
            void blocksCookieRefreshWithoutCsrfOriginAndHeader() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                    .cookie(new MockCookie("POST_REFRESH", "opaque-placeholder")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("CSRF validation failed"));
            }

            @Test
            void credentialApiReturnsMetadataOnlyAndEnforcesOwnerAndAdminAccess() throws Exception {
            String secret = "distinct-private-client-secret";
            MvcResult stored = mockMvc.perform(post("/api/credentials/oauth")
                    .header("Authorization", bearer(owner, "USER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"provider\":\"sample-provider\",\"clientId\":\"public-client\","
                        + "\"clientSecret\":\"" + secret + "\","
                        + "\"accessToken\":\"private-access\",\"refreshToken\":\"private-refresh\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.provider").value("sample-provider"))
                .andReturn();
            String responseBody = stored.getResponse().getContentAsString();
            org.junit.jupiter.api.Assertions.assertFalse(responseBody.contains(secret));
            org.junit.jupiter.api.Assertions.assertFalse(responseBody.contains("private-access"));
            org.junit.jupiter.api.Assertions.assertFalse(responseBody.contains("private-refresh"));
            String credentialId = jsonValue(stored, "data.id");

            mockMvc.perform(get("/api/credentials/oauth")
                    .header("Authorization", bearer(owner, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].clientId").value("public-client"))
                .andExpect(jsonPath("$.data[0].clientSecret").doesNotExist());

            mockMvc.perform(delete("/api/credentials/oauth/" + credentialId)
                    .header("Authorization", bearer(otherUser, "USER")))
                .andExpect(status().isForbidden());

            mockMvc.perform(delete("/api/credentials/oauth/" + credentialId)
                    .header("Authorization", bearer(administrator, "ADMIN")))
                .andExpect(status().isOk());
            }

            @Test
            void administrativeUserManagementIsRestrictedToAdmins() throws Exception {
            mockMvc.perform(get("/api/admin/users")
                    .header("Authorization", bearer(owner, "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"));

            mockMvc.perform(get("/api/admin/users")
                    .header("Authorization", bearer(administrator, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
            }

    @Test
    void concurrentRefreshesCannotBothKeepTheTokenFamilyActive() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + owner.getUsername()
                                + "\",\"password\":\"safe-test-password-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String originalCookie = cookieValue(login);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(() -> refreshAfter(start, originalCookie));
            Future<MvcResult> second = executor.submit(() -> refreshAfter(start, originalCookie));
            start.countDown();
            MvcResult firstResult = first.get();
            MvcResult secondResult = second.get();

            Set<Integer> statuses = new HashSet<>(Set.of(firstResult.getResponse().getStatus(),
                    secondResult.getResponse().getStatus()));
            org.junit.jupiter.api.Assertions.assertEquals(Set.of(200, 401), statuses);
            MvcResult successful = firstResult.getResponse().getStatus() == 200 ? firstResult : secondResult;
            String winnerCookie = cookieValue(successful);
            mockMvc.perform(post("/api/auth/refresh")
                            .cookie(new MockCookie("POST_REFRESH", winnerCookie))
                            .header("Origin", "http://localhost:8080")
                            .header("X-CSRF-Protection", "1"))
                    .andExpect(status().isUnauthorized());
        } finally {
            executor.shutdownNow();
        }
    }

    private MvcResult refreshAfter(CountDownLatch start, String refreshCookie) throws Exception {
        start.await();
        return mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new MockCookie("POST_REFRESH", refreshCookie))
                        .header("Origin", "http://localhost:8080")
                        .header("X-CSRF-Protection", "1"))
                .andReturn();
    }

    private UserAccount saveUser(String username, Set<String> roles) {
        return userRepository.save(new UserAccount(username, passwordEncoder.encode("safe-test-password-123"), roles));
    }

    private String bearer(UserAccount user, String role) {
        return "Bearer " + jwtTokenService.issue(new AuthenticatedUser(user.getId(), user.getUsername(), Set.of(role)));
    }

    private String jsonValue(MvcResult result, String path) throws Exception {
        tools.jackson.databind.JsonNode node = new tools.jackson.databind.json.JsonMapper().readTree(
                result.getResponse().getContentAsString());
        for (String segment : path.split("[.]")) {
            node = node.get(segment);
        }
        return node.asString();
    }

    private String cookieValue(MvcResult result) {
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertNotNull(setCookie);
        return HttpCookie.parse(setCookie).get(0).getValue();
    }

    private static String randomKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}