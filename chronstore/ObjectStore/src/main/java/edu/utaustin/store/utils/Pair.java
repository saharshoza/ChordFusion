package edu.utaustin.store.utils;

import java.io.Serializable;

public class Pair<K, V> implements Serializable {
    private K key;
    private V value;

    public Pair(K k, V v) {
        key = k;
        value = v;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public void setKey(K key) {
        this.key = key;
    }

    public void setValue(V value) {
        this.value = value;
    }

    @Override
    public int hashCode() {
        return key.hashCode() * 17 + value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Pair))
            return false;
        return key.equals(((Pair)obj).getKey())  &&
                value.equals(((Pair) obj).getValue());
    }

    public String toString() {
        return "(" + key.toString() + ", " + value.toString() + ")";
    }

}



