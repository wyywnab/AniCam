package com.wyywn.anicam.utils;

import java.util.LinkedList;

public class FixedSizeQueue<E> {
    private final int maxSize;
    private final LinkedList<E> list;

    public FixedSizeQueue(int maxSize) {
        this.maxSize = maxSize;
        this.list = new LinkedList<>();
    }

    public void add(E element) {
        if (list.size() >= maxSize) {
            list.removeFirst(); // 移除最早的元素
        }
        list.addLast(element); // 添加新元素
    }

    public E get(int index) {
        return list.get(index);
    }

    public int size() {
        return list.size();
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    @Override
    public String toString() {
        return list.toString();
    }

    // 其他可能需要的方法...
}