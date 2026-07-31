package com.htht.algorithm.data.tile.common;

import java.util.Objects;
import java.util.TreeMap;

public class MaxSizeHashMap<K, V> extends TreeMap<K, V> {

    private final int maxSize;


    private MapListener<V> listener;

    public MaxSizeHashMap(int maxSize, MapListener listener) {
        this.maxSize = maxSize;
        this.listener = listener;
    }

    @Override
    public V put(K key, V value) {
        V v = super.put(key, value);
        if (this.size() > maxSize) {
            K firstKey = this.firstKey();
            V firstVal = this.get(firstKey);
            this.remove(firstKey);
            if (Objects.nonNull(firstVal) && Objects.nonNull(listener)) {
                listener.doAfterRemove(firstVal);
            }
        }
        return v;
    }
}
