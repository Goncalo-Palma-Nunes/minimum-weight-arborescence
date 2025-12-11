package optimalarborescence.sequences;

/**
 * Sequence represents a generic sequence of data.
 *
 * @param <T> the type of data stored in the sequence
 */
public abstract class Sequence<T> {

    private T[] data;
    private int length;

    public Sequence(T[] data, int length) {
        this.data = data;
        this.length = length;
    }

    public T[] getData() {
        return data;
    }

    public int getLength() {
        return length;
    }

    /**
     * Gets the element at the specified index in the sequence.
     */
    public abstract T getElementAt(int index);

    /**
     * Compares the element at the specified index with the corresponding element in another sequence.
     *
     * @param index the index of the element to compare
     * @param other the other sequence to compare with
     * @return a negative integer, zero, or a positive integer as this element is less than, equal to,
     *         or greater than the specified element in the other sequence
     */
    public abstract int compareAt(int index, Sequence<?> other);

    @Override    
    public String toString() {
        return "Sequence{" +
                "data=" + data +
                '}';
    }
}
