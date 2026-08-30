package com.riskplatform.ruleconfig.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.ruleconfig.infrastructure.security.JwtService;
import com.riskplatform.ruleconfig.integration.support.IntegrationTestDataMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 运营治理 OU1 集成测试：用户启停、改角色、重置密码与安全约束。
 */
class UserGovernanceIntegrationTest extends AbstractMySqlIntegrationTest {

    private static final String MARKER = "ZZIT_USER_";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private IntegrationTestDataMapper testData;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String runId;

    @BeforeEach
    void setUp() {
        adminToken = jwtService.issue("it-admin", List.of("ADMIN"));
        runId = Long.toString(System.nanoTime());
        cleanupMarkerUsers();
    }

    @AfterEach
    void tearDown() {
        cleanupMarkerUsers();
    }

    @Test
    void adminCanEnableDisableUpdateRolesAndResetPassword() {
        String username = MARKER + runId;
        ResponseEntity<String> created = postAsAdmin("/api/v1/users", Map.of(
                "username", username,
                "password", "Passw0rd!",
                "roles", List.of("OPERATOR")));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long userId = parse(created).get("id").asLong();
        assertThat(parse(created).get("enabled").asBoolean()).isTrue();

        ResponseEntity<String> disabled = putAsAdmin(
                "/api/v1/users/" + userId + "/enabled", Map.of("enabled", false));
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parse(disabled).get("enabled").asBoolean()).isFalse();
        assertThat(testData.findUserEnabled(userId)).isEqualTo(0);

        ResponseEntity<String> enabled = putAsAdmin(
                "/api/v1/users/" + userId + "/enabled", Map.of("enabled", true));
        assertThat(enabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(testData.findUserEnabled(userId)).isEqualTo(1);

        ResponseEntity<String> roles = putAsAdmin(
                "/api/v1/users/" + userId + "/roles", Map.of("roles", List.of("AUDITOR")));
        assertThat(roles.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parse(roles).get("roles").get(0).asText()).isEqualTo("AUDITOR");

        ResponseEntity<String> reset = putAsAdmin(
                "/api/v1/users/" + userId + "/reset-password", Map.of("password", "NewPass99!"));
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(passwordMatches(username, "NewPass99!")).isTrue();
    }

    @Test
    void operatorTokenCannotManageUsers() {
        String operatorToken = jwtService.issue("it-operator", List.of("OPERATOR"));
        ResponseEntity<String> resp = exchange(
                operatorToken, "/api/v1/users", HttpMethod.POST,
                Map.of("username", MARKER + "OP_" + runId, "password", "x", "roles", List.of("OPERATOR")));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cannotDisableSelfWhenJwtMatchesTargetUser() {
        String username = MARKER + "SELF_" + runId;
        ResponseEntity<String> created = postAsAdmin("/api/v1/users", Map.of(
                "username", username,
                "password", "Passw0rd!",
                "roles", List.of("ADMIN")));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long userId = parse(created).get("id").asLong();

        String selfToken = jwtService.issue(username, List.of("ADMIN"));
        ResponseEntity<String> resp = exchange(
                selfToken, "/api/v1/users/" + userId + "/enabled", HttpMethod.PUT, Map.of("enabled", false));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).contains("不能禁用当前登录账号");
    }

    private boolean passwordMatches(String username, String rawPassword) {
        String hash = testData.findPasswordHash(username);
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        return hash != null && encoder.matches(rawPassword, hash);
    }

    private void cleanupMarkerUsers() {
        testData.deleteUsersByUsernamePattern(MARKER + "%");
    }

    private ResponseEntity<String> postAsAdmin(String path, Object body) {
        return exchange(adminToken, path, HttpMethod.POST, body);
    }

    private ResponseEntity<String> putAsAdmin(String path, Object body) {
        return exchange(adminToken, path, HttpMethod.PUT, body);
    }

    private ResponseEntity<String> exchange(String token, String path, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        HttpEntity<String> entity;
        try {
            String json = body == null ? null : objectMapper.writeValueAsString(body);
            entity = new HttpEntity<>(json, headers);
        } catch (Exception e) {
            throw new IllegalStateException("序列化请求体失败", e);
        }
        return restTemplate.exchange(url(path), method, entity, String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private JsonNode parse(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("解析响应体失败: " + response.getBody(), e);
        }
    }
}
