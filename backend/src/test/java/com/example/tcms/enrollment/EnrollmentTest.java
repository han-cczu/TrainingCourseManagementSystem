package com.example.tcms.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.example.tcms.common.BizException;
import com.example.tcms.common.ErrorCode;

/** 报名状态机（技术设计 §5.2）：所有合法与非法的转换。 */
class EnrollmentTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 2, 9, 0);

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void newEnrollment() {
        Enrollment e = Enrollment.newEnrolled(1, 2, T0);
        assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(e.getEnrolledAt()).isEqualTo(T0);
        assertThat(e.occupiesSeat()).isTrue();
    }

    @Test
    void enrolledCanCancelThenReEnroll() {
        Enrollment e = Enrollment.newEnrolled(1, 2, T0);
        e.cancel(T0.plusHours(1));
        assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(e.getCancelledAt()).isEqualTo(T0.plusHours(1));
        assertThat(e.occupiesSeat()).isFalse();

        e.reEnroll(T0.plusHours(2));
        assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(e.getEnrolledAt()).isEqualTo(T0.plusHours(2));
        assertThat(e.getCancelledAt()).isNull();
    }

    @Test
    void enrolledCanComplete() {
        Enrollment e = Enrollment.newEnrolled(1, 2, T0);
        e.complete(T0.plusDays(1), 99);
        assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(e.getCompletedAt()).isEqualTo(T0.plusDays(1));
        assertThat(e.getCompletedBy()).isEqualTo(99);
        assertThat(e.occupiesSeat()).isTrue();
    }

    @Test
    void illegalTransitions() {
        Enrollment enrolled = Enrollment.newEnrolled(1, 2, T0);
        assertInvalid(() -> enrolled.reEnroll(T0));

        Enrollment cancelled = Enrollment.newEnrolled(1, 2, T0);
        cancelled.cancel(T0);
        assertInvalid(() -> cancelled.cancel(T0));
        assertInvalid(() -> cancelled.complete(T0, 2));

        Enrollment completed = Enrollment.newEnrolled(1, 2, T0);
        completed.complete(T0, 2);
        assertInvalid(() -> completed.cancel(T0));
        assertInvalid(() -> completed.complete(T0, 2));
        assertInvalid(() -> completed.reEnroll(T0));
    }
}
