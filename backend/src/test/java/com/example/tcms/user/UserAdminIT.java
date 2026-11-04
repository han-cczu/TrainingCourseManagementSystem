package com.example.tcms.user;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.example.tcms.support.AbstractIntegrationTest;
import com.example.tcms.support.TestDataHelper;

/** 管理员用户管理（技术设计 §4.2、§7.2）。 */
class UserAdminIT extends AbstractIntegrationTest {

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUpAdmin() {
        admin = data.createAdmin("boss");
        adminToken = data.bearer(admin);
    }

    private ResultActions createUser(String username, String role) throws Exception {
        return mvc.perform(post("/api/users").header(AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "Passw0rd1", "realName": "R-%s", "department": "HR", "role": "%s"}
                        """.formatted(username, username, role)));
    }

    private ResultActions setEnabled(long id, boolean enabled) throws Exception {
        return mvc.perform(patch("/api/users/{id}/enabled", id).header(AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": %s}".formatted(enabled)));
    }

    @Test
    void createEmployeeAndAdmin() throws Exception {
        createUser("emp1", "EMPLOYEE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
        createUser("admin2", "ADMIN")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // 新建的管理员可以登录并访问管理接口
        String token = login("admin2", "Passw0rd1");
        mvc.perform(get("/api/users").header(AUTHORIZATION, token)).andExpect(status().isOk());
    }

    @Test
    void createRejectsDuplicateAndInvalidInput() throws Exception {
        createUser("emp1", "EMPLOYEE").andExpect(status().isCreated());
        createUser("EMP1", "EMPLOYEE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        createUser("emp2", "ROOT")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void disableAndEnableUser() throws Exception {
        User emp = data.createEmployee("emp1");

        setEnabled(emp.getId(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "emp1", "password": "%s"}
                        """.formatted(TestDataHelper.PASSWORD)))
                .andExpect(status().isUnauthorized());

        setEnabled(emp.getId(), true).andExpect(jsonPath("$.enabled").value(true));
        login("emp1", TestDataHelper.PASSWORD);
    }

    @Test
    void adminCannotDisableSelf() throws Exception {
        setEnabled(admin.getId(), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DISABLE_SELF"));
    }

    @Test
    void resetPassword() throws Exception {
        User emp = data.createEmployee("emp1");
        mvc.perform(put("/api/users/{id}/password", emp.getId()).header(AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"Reset1234\"}"))
                .andExpect(status().isNoContent());
        login("emp1", "Reset1234");
    }

    @Test
    void operationsOnMissingUserReturn404() throws Exception {
        setEnabled(999_999, false)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void listSupportsKeywordAndPagination() throws Exception {
        data.createEmployee("alice1");
        data.createEmployee("alice2");
        data.createEmployee("bob1");

        mvc.perform(get("/api/users").param("keyword", "alice").param("page", "2").param("size", "1")
                        .header(AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.records.length()").value(1));

        mvc.perform(get("/api/users").param("size", "101").header(AUTHORIZATION, adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
