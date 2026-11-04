package com.example.tcms.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.tcms.TestcontainersConfig;
import com.jayway.jsonpath.JsonPath;

/**
 * 集成测试基类：真实 MySQL（Testcontainers）+ MockMvc + 可调时钟。
 * <p>
 * 所有子类共享同一个 Spring 上下文和 MySQL 容器（配置相同即被缓存复用）；每个测试前清库并重置时钟。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestClockConfig.class, TestDataHelper.class})
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected TestDataHelper data;

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void resetState() {
        clock.reset();
        data.truncateAll();
    }

    /** 通过登录接口获取 token（走完整登录流程，而不是直接签发） */
    protected String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return "Bearer " + JsonPath.read(body, "$.token");
    }
}
