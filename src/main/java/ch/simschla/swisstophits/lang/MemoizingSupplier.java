package ch.simschla.swisstophits.lang;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class MemoizingSupplier<T> implements Supplier<T> {

    private final Supplier<T> delegate;
    private volatile AtomicReference<T> value = null;

    public MemoizingSupplier(Supplier<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T get() {
        if (value == null) {
            synchronized (this) {
                if (value == null) {
                    value = new AtomicReference<>(delegate.get());
                }
            }
        }
        return value.get();
    }

    public boolean isResolved() {
        return value != null;
    }
}
