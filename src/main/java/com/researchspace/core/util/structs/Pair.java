package com.researchspace.core.util.structs;

public class Pair<K, V> {

    private final K key;
    private final V val;

    public static <K, V> Pair<K, V> createPair(K key, V val) {
        return new Pair<>(key, val);
    }

    public Pair(K key, V val) {
        this.key = key;
        this.val = val;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return val;
    }

}