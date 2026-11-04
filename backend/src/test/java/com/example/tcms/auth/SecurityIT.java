package com.example.tcms.auth;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.example.tcms.support.AbstractIntegrationTest;
import com.example.tcms.user.User;

/** 认证与授权边界（技术设计 §4.4、§10）。 */
class SecurityIT extends AbstractIntegrationTest {

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tamperedTokenIsUnauthorized() throws Exception {
        String bearer = data.bearer(data.createEmployee("alice"));
        int i = bearer.length() - 5;
        char replaced = bearer.charAt(i) == 'A' ? 'B' : 'A';
        String tampered = bearer.substring(0, i) + replaced + bearer.substring(i + 1);

        mvc.perform(get("/api/auth/me").header(AUTHORIZATION, tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        String bearer = data.bearer(data.createEmployee("alice"));
        mvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer)).andExpect(status().isOk());

        clock.advance(Duration.ofHours(9));
        mvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void disabledUserTokenIsRejectedImmediately() throws Exception {
        User user = data.createEmployee("alice");
        String bearer = data.bearer(user);
        mvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer)).andExpect(status().isOk());

        data.disable(user);
        mvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCannotAccessAdminApi() throws Exception {
        String bearer = data.bearer(data.createEmployee("alice"));
        mvc.perform(get("/api/users").header(AUTHORIZATION, bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanAccessAdminApi() throws Exception {
        String bearer = data.bearer(data.createAdmin("boss"));
        mvc.perform(get("/api/users").header(AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
