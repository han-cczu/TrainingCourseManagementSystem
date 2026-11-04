package com.example.tcms.enrollment.dto;

import com.example.tcms.enrollment.EnrollmentStatus;

import lombok.Data;

/** GROUP BY status 的一行。 */
@Data
public class StatusCount {

    private EnrollmentStatus status;
    private long total;
}
