package com.htht.algorithm.data.tile.grid.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//@Component
public class SynchronizedByKey<T> {

    Map<T, Object> mutexCache = new ConcurrentHashMap<>();

    public void exec(T key, Runnable statement) {
        Object mutex4Key = mutexCache.computeIfAbsent(key, k -> new Object());
        synchronized (mutex4Key) {
            try {
                statement.run();
            } catch (Exception e) {
                mutexCache.remove(key);
            }
        }
    }
}
