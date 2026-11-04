package com.example.tcms.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.tcms.common.BizException;
import com.example.tcms.support.AbstractIntegrationTest;
import com.example.tcms.user.User;

/**
 * 并发报名（技术设计 §6）：所有线程在同一时刻起跑，直接调用 Service 以最大化竞争。
 * 正确性只依赖 MySQL 的课程行锁 + 唯一索引，与 JVM 内的锁无关。
 */
class ConcurrencyIT extends AbstractIntegrationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 3, 9, 0);

    @Autowired
    private EnrollmentService enrollmentService;

    /** 每个任务的结果：SUCCESS 或业务错误码；非预期异常记为 UNEXPECTED:类名 */
    private Map<String, Integer> runConcurrently(List<Callable<?>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        Map<String, AtomicInteger> outcomes = new ConcurrentHashMap<>();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    String outcome;
                    try {
                        task.call();
                        outcome = "SUCCESS";
                    } catch (BizException e) {
                        outcome = e.getErrorCode().name();
                    } catch (Exception e) {
                        outcome = "UNEXPECTED:" + e.getClass().getSimpleName() + ":" + e.getMessage();
                    }
                    outcomes.computeIfAbsent(outcome, k -> new AtomicInteger()).incrementAndGet();
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        Map<String, Integer> result = new ConcurrentHashMap<>();
        outcomes.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    @RepeatedTest(3)
    void twentyUsersCompeteForFiveSeats() throws Exception {
        User admin = data.createAdmin("boss");
        long courseId = data.insertCourse("抢课", "王老师", START, START.plusHours(2), 5, admin);
        List<User> users = IntStream.range(0, 20).mapToObj(i -> data.createEmployee("racer" + i)).toList();

        Map<String, Integer> outcomes = runConcurrently(users.stream()
                .<Callable<?>>map(u -> () -> enrollmentService.enroll(courseId, data.currentUser(u)))
                .toList());

        System.out.printf("[ConcurrencyIT] 20 users / 5 seats: %s, enrolled_count=%d%n", outcomes, data.enrolledCount(courseId));
        assertThat(outcomes).containsOnlyKeys("SUCCESS", "COURSE_FULL");
        assertThat(outcomes.get("SUCCESS")).isEqualTo(5);
        assertThat(outcomes.get("COURSE_FULL")).isEqualTo(15);
        assertThat(data.enrolledCount(courseId)).isEqualTo(5);
        assertThat(data.seatRows(courseId)).isEqualTo(5);
    }

    @RepeatedTest(3)
    void sameUserClickingTenTimesEnrollsOnce() throws Exception {
        User admin = data.createAdmin("boss");
        long courseId = data.insertCourse("抢课", "王老师", START, START.plusHours(2), 5, admin);
        User user = data.createEmployee("clicker");

        Map<String, Integer> outcomes = runConcurrently(IntStream.range(0, 10)
                .<Callable<?>>mapToObj(i -> () -> enrollmentService.enroll(courseId, data.currentUser(user)))
                .toList());

        System.out.printf("[ConcurrencyIT] same user x10 clicks: %s%n", outcomes);
        assertThat(outcomes).containsOnlyKeys("SUCCESS", "ALREADY_ENROLLED");
        assertThat(outcomes.get("SUCCESS")).isEqualTo(1);
        assertThat(data.enrolledCount(courseId)).isEqualTo(1);
        assertThat(data.count("SELECT COUNT(*) FROM enrollment WHERE course_id = ?", courseId)).isEqualTo(1);
    }

    /** 报名与取消混合并发：结束后计数必须与实际占位记录一致，且不超过容量 */
    @RepeatedTest(3)
    void mixedEnrollAndCancelKeepsCountConsistent() throws Exception {
        User admin = data.createAdmin("boss");
        long courseId = data.insertCourse("抢课", "王老师", START, START.plusHours(2), 3, admin);
        List<User> holders = IntStream.range(0, 3).mapToObj(i -> data.createEmployee("holder" + i)).toList();
        for (User h : holders) {
            enrollmentService.enroll(courseId, data.currentUser(h));
        }
        List<User> newcomers = IntStream.range(0, 12).mapToObj(i -> data.createEmployee("new" + i)).toList();

        List<Callable<?>> tasks = new ArrayList<>();
        holders.forEach(h -> tasks.add(() -> {
            enrollmentService.cancel(courseId, data.currentUser(h));
            return null;
        }));
        newcomers.forEach(n -> tasks.add(() -> enrollmentService.enroll(courseId, data.currentUser(n))));
        Map<String, Integer> outcomes = runConcurrently(tasks);

        System.out.printf("[ConcurrencyIT] 3 cancels + 12 enrolls on 3 seats: %s, enrolled_count=%d%n", outcomes, data.enrolledCount(courseId));
        assertThat(outcomes.keySet()).allMatch(k -> k.equals("SUCCESS") || k.equals("COURSE_FULL"));
        assertThat(data.enrolledCount(courseId)).isEqualTo(data.seatRows(courseId));
        assertThat(data.enrolledCount(courseId)).isLessThanOrEqualTo(3);
        // 3 个取消必定成功；新报名的成功数 = 最终人数
        assertThat(outcomes.get("SUCCESS")).isEqualTo(3 + (int) data.enrolledCount(courseId));
    }
}
