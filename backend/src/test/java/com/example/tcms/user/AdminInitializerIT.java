package com.example.tcms.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.tcms.support.AbstractIntegrationTest;

/** 初始管理员（技术设计 §4.2）：空库时创建，重复运行不重复创建。 */
class AdminInitializerIT extends AbstractIntegrationTest {

    @Autowired
    private AdminInitializer adminInitializer;

    @Test
    void createsAdminOnceWhenNoneExists() throws Exception {
        assertThat(data.count("SELECT COUNT(*) FROM sys_user WHERE role = 'ADMIN'")).isZero();

        adminInitializer.run(null);
        adminInitializer.run(null);

        assertThat(data.count("SELECT COUNT(*) FROM sys_user WHERE role = 'ADMIN'")).isEqualTo(1);
        // application.yml 中的本地开发默认账号
        login("admin", "Admin12345");
    }

    @Test
    void skipsWhenAnotherAdminExists() throws Exception {
        data.createAdmin("boss");
        adminInitializer.run(null);
        assertThat(data.count("SELECT COUNT(*) FROM sys_user WHERE role = 'ADMIN'")).isEqualTo(1);
    }
}
