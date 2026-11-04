package com.example.tcms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.example.tcms.support.AbstractIntegrationTest;
import com.example.tcms.support.TestDataHelper;

/** 按公开的 8–64 字符规则验证多字节密码；不能因编码长度抛出 500。 */
class PasswordBoundaryIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    // 26 个字符，含字母和数字；UTF-8 为 74 字节。
    private static final String PASSWORD = "密".repeat(24) + "A1";

    @ParameterizedTest(name = "多字节密码：{0}")
    @ValueSource(strings = {"register", "create", "change", "reset"})
    void validMultibytePasswordWorksAcrossAllPasswordEntryPoints(String operation) throws Exception {
        var admin = data.createAdmin("password_admin");
        var employee = data.createEmployee("password_employee");
        String username = "password_new";
        switch (operation) {
            case "register" -> mvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"%s","password":"%s","realName":"回归"}
                                    """.formatted(username, PASSWORD)))
                    .andExpect(status().isCreated());
            case "create" -> mvc.perform(post("/api/users").header(AUTHORIZATION, data.bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"%s","password":"%s","realName":"回归","role":"EMPLOYEE"}
                                    """.formatted(username, PASSWORD)))
                    .andExpect(status().isCreated());
            case "change" -> {
                username = employee.getUsername();
                mvc.perform(put("/api/auth/password").header(AUTHORIZATION, data.bearer(employee))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"oldPassword":"%s","newPassword":"%s"}
                                        """.formatted(TestDataHelper.PASSWORD, PASSWORD)))
                        .andExpect(status().isNoContent());
            }
            case "reset" -> {
                username = employee.getUsername();
                mvc.perform(put("/api/users/{id}/password", employee.getId())
                                .header(AUTHORIZATION, data.bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"newPassword":"%s"}
                                        """.formatted(PASSWORD)))
                        .andExpect(status().isNoContent());
            }
            default -> throw new IllegalArgumentException(operation);
        }
        login(username, PASSWORD);
        // 最后一个字符在第 72 字节之后，仍必须参与密码验证，不能截断后比较。
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, "密".repeat(24) + "A2")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void maximumLengthMultibytePasswordRoundTripsWithinExistingColumn() throws Exception {
        String password = "密".repeat(62) + "A1"; // 64 字符，188 字节
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"max_password","password":"%s","realName":"上限"}
                                """.formatted(password)))
                .andExpect(status().isCreated());
        login("max_password", password);
        String hash = jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE username = ?",
                String.class, "max_password");
        assertThat(hash).startsWith("{pbkdf2-sha256-v1}").hasSizeLessThanOrEqualTo(100);
    }

    @Test
    void legacyBcryptAccountCanLoginAndChangeToMultibytePassword() throws Exception {
        var employee = data.createEmployee("legacy_password");
        jdbc.update("UPDATE sys_user SET password_hash = ? WHERE id = ?",
                new BCryptPasswordEncoder().encode(TestDataHelper.PASSWORD), employee.getId());
        String token = login(employee.getUsername(), TestDataHelper.PASSWORD);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"legacy_password","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/auth/password").header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"oldPassword":"%s","newPassword":"%s"}
                                """.formatted(TestDataHelper.PASSWORD, PASSWORD)))
                .andExpect(status().isNoContent());
        login(employee.getUsername(), PASSWORD);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"legacy_password","password":"%s"}
                                """.formatted(TestDataHelper.PASSWORD)))
                .andExpect(status().isUnauthorized());
    }
}
