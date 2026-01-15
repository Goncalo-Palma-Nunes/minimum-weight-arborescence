package optimalarborescence.sequences;

import java.util.ArrayList;
import java.util.List;

/**
 * Sequence represents a generic sequence of data.
 *
 * @param <T> the type of data stored in the sequence
 */
public abstract class Sequence<T> {

    private T[] data;
    private int length;
    protected List<Integer> missingDataPositions;

    public Sequence(T[] data, int length) {
        this.data = data;
        this.length = length;
        this.missingDataPositions = null;
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
     * Checks if the element at the specified index is missing data.
     *
     * @param index the index to check
     * @return true if the element is missing data, false otherwise
     */
    public abstract boolean isMissingDataAt(int index);

    /**
     * Compares the element at the specified index with the corresponding element in another sequence.
     *
     * @param index the index of the element to compare
     * @param other the other sequence to compare with
     * @return a negative integer, zero, or a positive integer as this element is less than, equal to,
     *         or greater than the specified element in the other sequence
     */
    public abstract int compareAt(int index, Sequence<?> other);

    /**
     * Identifies positions with missing data in the sequence.
     *
     * @return an array of indices representing positions with missing data
     */
    public List<Integer> getPositionsWithMissingData() {
        if (missingDataPositions != null) {
            return missingDataPositions;
        }

        missingDataPositions = new ArrayList<>();
        for (int i = 0; i < getLength(); i++) {
            if (isMissingDataAt(i)) {
                missingDataPositions.add(i);
            }
        }
        return missingDataPositions;
    }

    @Override    
    public String toString() {
        return "Sequence{" +
                "data=" + data +
                '}';
    }
}
