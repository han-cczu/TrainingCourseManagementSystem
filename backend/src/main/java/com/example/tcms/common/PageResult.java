package com.example.tcms.common;

import java.util.List;
import java.util.function.Function;

import com.baomidou.mybatisplus.core.metadata.IPage;

/** 分页响应（技术设计 §7.1），page 从 1 开始。 */
public record PageResult<T>(List<T> records, long total, long page, long size) {

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public static <S, T> PageResult<T> of(IPage<S> page, Function<? super S, ? extends T> mapper) {
        List<T> records = page.getRecords().stream().<T>map(mapper).toList();
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
