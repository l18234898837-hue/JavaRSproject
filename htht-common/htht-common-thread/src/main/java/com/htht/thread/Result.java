package com.htht.thread;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 线程结果
 *
 * @param <T>
 * @author wjx
 * @since 2023-5-4
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    public T value;
}
