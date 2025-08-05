package optimalarborescence.datastructure.heap;

public interface MergeableHeapInterface<T> {
    /**
     * Merges this heap with another heap.
     *
     * @param other the other heap to merge with
     * @return the tree formed by linking the two
     */
    MergeableHeapInterface<T> merge(MergeableHeapInterface<T> other);

    /**
     * Inserts a new element into the heap.
     *
     * @param value the value to insert
     */
    void insert(T value);

    /**
     * Removes and returns the minimum element from the heap.
     *
     * @return the minimum element
     */
    T exctractMin();

    /**
     * Decreases the key of a specific element in the heap.
     *
     * @param value the value to decrease
     * @param newValue the new value to set
     */
    void decreaseKey(T value, T newValue);

    /**
     * Returns the minimum element without removing it.
     *
     * @return the minimum element
     */
    T findMin();

    /**
     * Checks if the heap is empty.
     *
     * @return true if the heap is empty, false otherwise
     */
    boolean isEmpty();
}