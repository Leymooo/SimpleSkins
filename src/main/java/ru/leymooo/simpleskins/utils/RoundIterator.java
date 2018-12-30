package ru.leymooo.simpleskins.utils;

import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class RoundIterator<T> implements Iterator<T> {

    private Iterator<T> iterator;
    private final List<T> toIterate;
    int cursor = 0;

    public RoundIterator(List<T> backend) {
        toIterate = backend;
        iterator = toIterate.iterator();
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public T next() {
        if (toIterate.isEmpty()) {
            return null;
        }
        if (cursor >= toIterate.size()) {
            cursor = 0;
            iterator = toIterate.iterator();
        }
        cursor++;
        return iterator.next();
    }

    @Override
    public void remove() {
        if (toIterate.isEmpty()) {
            return;
        }
        iterator.remove();
    }

    @Override
    public void forEachRemaining(Consumer<? super T> action) {
        iterator.forEachRemaining(action);
    }
}
