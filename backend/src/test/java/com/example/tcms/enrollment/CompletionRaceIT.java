package com.example.tcms.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.tcms.common.BizException;
import com.example.tcms.course.CourseMapper;
import com.example.tcms.support.AbstractIntegrationTest;

/** 完成操作与本人操作交错时，不得覆盖终态或使报名计数失真。 */
class CompletionRaceIT extends AbstractIntegrationTest {

    @Autowired private EnrollmentService service;
    @Autowired private CourseMapper courseMapper;
    @Autowired private EnrollmentMapper enrollmentMapper;
    @Autowired private PlatformTransactionManager transactionManager;

    @ParameterizedTest(name = "管理员读记录后，本人先{0}（true=取消，false=完成）")
    @ValueSource(booleans = {false, true})
    void adminMustRecheckLatestEnrollmentAfterAcquiringCourseLock(boolean cancel) throws Exception {
        var admin = data.createAdmin("race_admin");
        var employee = data.createEmployee("race_employee");
        var start = clock.now().plusSeconds(1);
        long courseId = data.insertCourse("并发完成回归", "讲师", start, start.plusHours(1), 1, admin);
        long enrollmentId = service.enroll(courseId, data.currentUser(employee)).id();
        if (!cancel) clock.setNow(start);

        var beforeLock = new CountDownLatch(1);
        var continueAdmin = new CountDownLatch(1);
        // 只控制线程交错；所有查询、行锁和写入仍由真实 Mapper 在 Testcontainers MySQL 中执行。
        // 不替换 Spring Bean，不更改共享上下文。手工构造的 Service 由 TransactionTemplate 开启事务。
        CourseMapper gatedMapper = mock(CourseMapper.class, delegatesTo(courseMapper));
        doAnswer(invocation -> {
            beforeLock.countDown();
            if (!continueAdmin.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("等待本人操作超时");
            return courseMapper.selectByIdForUpdate(courseId);
        }).when(gatedMapper).selectByIdForUpdate(courseId);
        var adminService = new EnrollmentService(gatedMapper, enrollmentMapper, clock);
        var transaction = new TransactionTemplate(transactionManager);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var adminResult = pool.submit(() -> {
                try {
                    transaction.executeWithoutResult(ignored ->
                            adminService.completeByAdmin(enrollmentId, data.currentUser(admin)));
                    return "SUCCESS";
                } catch (BizException ex) {
                    return ex.getErrorCode().name();
                }
            });
            assertThat(beforeLock.await(10, TimeUnit.SECONDS)).as("管理员已读记录、尚未锁课程").isTrue();
            if (cancel) {
                service.cancel(courseId, data.currentUser(employee));
                clock.setNow(start); // 取消在开课前提交；管理员随后在开课时继续
            } else {
                service.completeSelf(courseId, data.currentUser(employee));
            }
            continueAdmin.countDown();
            String outcome = adminResult.get(15, TimeUnit.SECONDS);
            var stored = enrollmentMapper.selectById(enrollmentId);
            assertAll(
                    () -> assertThat(outcome).isEqualTo("INVALID_STATUS_TRANSITION"),
                    () -> assertThat(stored.getStatus()).isEqualTo(
                            cancel ? EnrollmentStatus.CANCELLED : EnrollmentStatus.COMPLETED),
                    () -> assertThat(stored.getCompletedBy()).isEqualTo(cancel ? null : employee.getId()),
                    () -> assertThat(data.enrolledCount(courseId)).isEqualTo(data.seatRows(courseId)));
        } finally {
            continueAdmin.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).as("并发任务已结束").isTrue();
        }
    }
}
