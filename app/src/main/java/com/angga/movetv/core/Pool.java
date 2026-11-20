package com.angga.movetv.core;

/**
 * A fixed-capacity object pool: all objects are created up front, so nothing is allocated while the game
 * runs. Plain Java, no android imports.
 *
 * Active objects live in positions 0..size()-1. For loops without allocation use
 * {@code for (int i = pool.size() - 1; i >= 0; i--) { T t = pool.at(i); ... pool.freeAt(i); }}
 * (going backwards makes it safe to free the current item). {@link #forEachActive} is handy, but a lambda
 * that captures variables allocates, so avoid it in per-frame code.
 *
 * @param <T> the pooled type
 */
public final class Pool<T> {

    /** Creates the pooled objects. */
    public interface Factory<T> {
        /** @return a new, ready-to-use object */
        T create();
    }

    /** Called for each active object. */
    public interface Visitor<T> {
        /** @param item an active object */
        void visit(T item);
    }

    private final Object[] items;
    private int active;

    /**
     * Creates the pool and all its objects.
     * @param capacity how many objects the pool holds
     * @param factory makes one object (called exactly {@code capacity} times, here)
     */
    public Pool(int capacity, Factory<T> factory) {
        items = new Object[capacity];
        for (int i = 0; i < capacity; i++) items[i] = factory.create();
    }

    /**
     * Takes an object out of the pool. The caller must set all its fields.
     * @return an object, or null when all {@code capacity} objects are in use
     */
    @SuppressWarnings("unchecked")
    public T obtain() {
        return active < items.length ? (T) items[active++] : null;
    }

    /**
     * Gives an object back. Objects that are not active in this pool are ignored.
     * @param item the object to free
     */
    public void free(T item) {
        for (int i = 0; i < active; i++) {
            if (items[i] == item) {
                freeAt(i);
                return;
            }
        }
    }

    /**
     * Gives back the active object at an index (it is swapped with the last active one).
     * @param index 0..size()-1
     */
    public void freeAt(int index) {
        if (index < 0 || index >= active) return;
        Object freed = items[index];
        active--;
        items[index] = items[active];
        items[active] = freed;
    }

    /**
     * Number of active objects.
     * @return the count
     */
    public int size() {
        return active;
    }

    /**
     * Total number of objects.
     * @return the capacity
     */
    public int capacity() {
        return items.length;
    }

    /**
     * An active object by position.
     * @param index 0..size()-1
     * @return the object
     */
    @SuppressWarnings("unchecked")
    public T at(int index) {
        if (index < 0 || index >= active) throw new IndexOutOfBoundsException("index " + index + ", size " + active);
        return (T) items[index];
    }

    /**
     * Calls the visitor for each active object. Do not free objects from inside the visitor.
     * @param v the visitor
     */
    @SuppressWarnings("unchecked")
    public void forEachActive(Visitor<T> v) {
        for (int i = 0; i < active; i++) v.visit((T) items[i]);
    }

    /** Gives back every object. */
    public void clear() {
        active = 0;
    }
}
