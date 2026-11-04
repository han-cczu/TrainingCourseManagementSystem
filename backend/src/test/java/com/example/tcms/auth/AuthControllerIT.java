package com.example.tcms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.example.tcms.support.AbstractIntegrationTest;
import com.example.tcms.support.TestDataHelper;
import com.example.tcms.user.User;
import com.jayway.jsonpath.JsonPath;

/** 注册、登录、当前用户、修改密码（技术设计 §4.3、§7.2）。 */
class AuthControllerIT extends AbstractIntegrationTest {

    private ResultActions register(String json) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions loginRaw(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "password": "%s"}
                """.formatted(username, password)));
    }

    @Test
    void registerThenLoginThenMe() throws Exception {
        register("""
                {"username": "zhangsan", "password": "Passw0rd1", "realName": "张三", "department": "研发部"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("zhangsan"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String body = loginRaw("zhangsan", "Passw0rd1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("zhangsan"))
                // 时钟固定在 2026-11-02 09:00，token 有效期 8 小时
                .andExpect(jsonPath("$.expiresAt").value(startsWith("2026-11-02T17:00")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = JsonPath.read(body, "$.token");

        String me = mvc.perform(get("/api/auth/me").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("zhangsan"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((String) JsonPath.read(me, "$.realName")).isEqualTo("张三");
    }

    @Test
    void registerIgnoresRoleField() throws Exception {
        register("""
                {"username": "sneaky", "password": "Passw0rd1", "realName": "S", "role": "ADMIN"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    void registerDuplicateUsernameIsCaseInsensitive() throws Exception {
        data.createEmployee("zhangsan");
        register("""
                {"username": "ZhangSan", "password": "Passw0rd1", "realName": "张三"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void registerValidatesUsernamePasswordAndName() throws Exception {
        register("""
                {"username": "ab", "password": "onlyletters", "realName": ""}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fields.username").exists())
                .andExpect(jsonPath("$.details.fields.password").exists())
                .andExpect(jsonPath("$.details.fields.realName").exists());
    }

    @Test
    void loginFailuresAllReturnBadCredentials() throws Exception {
        User user = data.createEmployee("lisi");

        loginRaw("lisi", "WrongPass1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        loginRaw("nobody", TestDataHelper.PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));

        // 停用后也只返回 BAD_CREDENTIALS，不暴露账号状态
        data.disable(user);
        loginRaw("lisi", TestDataHelper.PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void loginRequiresFields() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void changePassword() throws Exception {
        data.createEmployee("wangwu");
        String token = login("wangwu", TestDataHelper.PASSWORD);

        // 原密码错误返回 400 而不是 401，避免前端误判为登录失效
        mvc.perform(put("/api/auth/password").header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword": "WrongOld1", "newPassword": "NewPassw0rd"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fields.oldPassword").exists());

        mvc.perform(put("/api/auth/password").header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword": "%s", "newPassword": "weak"}
                                """.formatted(TestDataHelper.PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.newPassword").exists());

        mvc.perform(put("/api/auth/password").header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword": "%s", "newPassword": "NewPassw0rd"}
                                """.formatted(TestDataHelper.PASSWORD)))
                .andExpect(status().isNoContent());

        loginRaw("wangwu", TestDataHelper.PASSWORD).andExpect(status().isUnauthorized());
        loginRaw("wangwu", "NewPassw0rd").andExpect(status().isOk());
    }
}
