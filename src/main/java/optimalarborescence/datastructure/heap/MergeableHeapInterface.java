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

    // T insert(T node, int value);

    /**
     * Removes and returns the minimum element from the heap.
     *
     * @return the minimum element
     */
    T extractMin();

    /**
     * Removes and returns the element at index i from the heap.
     * Only implemented by indexable heaps
     * 
     * @param i the index of the element to remove
     * @return the element at index i
     */
    T extractMin(int i);

    /**
     * Decreases the key of a specific element in the heap.
     *
     * @param value the value to decrease
     * @param node the node to update
     */
    T decreaseKey(T node, int value);

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

    /**
     * Clears the heap.
     */
    void clear();
}