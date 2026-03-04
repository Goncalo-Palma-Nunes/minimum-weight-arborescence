package optimalarborescence.datastructure.heap;

import java.util.Comparator;
import java.util.List;
import optimalarborescence.graph.Edge;

public class LinearSearchArray implements MergeableHeapInterface<int[]> {

    protected int capacity;
    protected int size;
    protected int[] array;
    private final Comparator<int[]> comparator;

    private static final int ENTRY_SIZE = 3; // weight, source, target

    public LinearSearchArray(int capacity) {
        this.capacity = capacity;
        this.size = 0;
        this.array = new int[capacity * ENTRY_SIZE];
        this.comparator = null;
    }

    public LinearSearchArray(int capacity, Comparator<int[]> comparator) {
        this.capacity = capacity;
        this.size = 0;
        this.array = new int[capacity * ENTRY_SIZE];
        this.comparator = comparator;
    }

    public LinearSearchArray(List<Edge> edges) {
        this.capacity = edges.size();
        this.size = edges.size();
        this.array = new int[capacity * ENTRY_SIZE];
        this.comparator = null;
        for (int i = 0; i < edges.size(); i++) {
            this.array[i * ENTRY_SIZE] = edges.get(i).getWeight();
            this.array[i * ENTRY_SIZE + 1] = edges.get(i).getSource().getId();
            this.array[i * ENTRY_SIZE + 2] = edges.get(i).getDestination().getId();
        }
    }

    /** Returns true if a is less than b under this heap's ordering. */
    private boolean isLess(int indexA, int indexB) {
        if (comparator != null) {
            int[] a = new int[]{ array[indexA * ENTRY_SIZE], array[indexA * ENTRY_SIZE + 1], array[indexA * ENTRY_SIZE + 2] };
            int[] b = new int[]{ array[indexB * ENTRY_SIZE], array[indexB * ENTRY_SIZE + 1], array[indexB * ENTRY_SIZE + 2] };
            return comparator.compare(a, b) < 0;
        }
        return array[indexA * ENTRY_SIZE] < array[indexB * ENTRY_SIZE];
    }

    private int getSourceFromEdge(int[] edge) {
        return edge[1];
    }

    private int getTargetFromEdge(int[] edge) {
        return edge[2];
    }

    @Override
    public boolean isEmpty() {
        return this.size == 0;
    }
    
    @Override
    public MergeableHeapInterface<int[]> merge(MergeableHeapInterface<int[]> other) {
        if (!(other instanceof LinearSearchArray)) {
            throw new IllegalArgumentException("Expected an argument of type LinearSearchArray");
        }

        LinearSearchArray otherHeap = (LinearSearchArray) other;
        if (this.isEmpty()) {
            this.array = otherHeap.array;
            this.size = otherHeap.size;
            this.capacity = otherHeap.capacity;
            return this;
        }
        if (otherHeap.isEmpty()) {
            return this;
        }

        int mergedSize = this.size + otherHeap.size;
        int[] mergedArray = new int[mergedSize * ENTRY_SIZE];
        System.arraycopy(this.array, 0, mergedArray, 0, this.size * ENTRY_SIZE);
        System.arraycopy(otherHeap.array, 0, mergedArray, this.size * ENTRY_SIZE, otherHeap.size * ENTRY_SIZE);
        
        this.array = mergedArray;
        this.size = mergedSize;
        this.capacity = mergedSize;

        return this;
    }
    
    @Override
    public int[] findMin() {
        if (this.isEmpty()) {
            throw new IllegalStateException("Heap is empty");
        }
        int minIndex = 0;
        for (int i = 1; i < size; i++) {
            if (isLess(i, minIndex)) {
                minIndex = i;
            }
        }
        return new int[] { array[minIndex * ENTRY_SIZE], array[minIndex * ENTRY_SIZE + 1], array[minIndex * ENTRY_SIZE + 2] };
    }
    
    @Override
    public void insert(int[] edge) {
        if (edge.length != ENTRY_SIZE) {
            throw new IllegalArgumentException("Edge must have exactly 3 elements: weight, source, target");
        }
        if (size >= capacity) {
            int newCapacity = capacity == 0 ? 10 : capacity * 2;
            int[] newArray = new int[newCapacity * ENTRY_SIZE];
            if (array != null && size > 0) {
                System.arraycopy(array, 0, newArray, 0, size * ENTRY_SIZE);
            }
            this.array = newArray;
            this.capacity = newCapacity;
        }
        array[size * ENTRY_SIZE] = edge[0]; // weight
        array[size * ENTRY_SIZE + 1] = edge[1]; // source
        array[size * ENTRY_SIZE + 2] = edge[2]; // target
        size++;
    }

    public void insert(Edge edge) {
        if (size >= capacity) {
            int newCapacity = capacity == 0 ? 10 : capacity * 2;
            int[] newArray = new int[newCapacity * ENTRY_SIZE];
            if (array != null && size > 0) {
                System.arraycopy(array, 0, newArray, 0, size * ENTRY_SIZE);
            }
            this.array = newArray;
            this.capacity = newCapacity;
        }
        array[size * ENTRY_SIZE] = edge.getWeight(); // weight
        array[size * ENTRY_SIZE + 1] = edge.getSource().getId(); // source
        array[size * ENTRY_SIZE + 2] = edge.getDestination().getId(); // target
        size++;
    }

    @Override
    public int[] extractMin() {
        if (this.isEmpty()) {
            throw new IllegalStateException("Heap is empty");
        }

        int minIndex = 0;
        for (int i = 1; i < size; i++) {
            if (isLess(i, minIndex)) {
                minIndex = i;
            }
        }
        int[] minEdge = new int[] { array[minIndex * ENTRY_SIZE], array[minIndex * ENTRY_SIZE + 1], array[minIndex * ENTRY_SIZE + 2] };

        // Shift the last edge to the position of the extracted minimum edge
        array[minIndex * ENTRY_SIZE] = array[(size - 1) * ENTRY_SIZE];
        array[minIndex * ENTRY_SIZE + 1] = array[(size - 1) * ENTRY_SIZE + 1];
        array[minIndex * ENTRY_SIZE + 2] = array[(size - 1) * ENTRY_SIZE + 2];
        size--;

        return minEdge;
    }

    @Override
    public int[] decreaseKey(int[] edge, int newWeight) {
        if (edge.length != ENTRY_SIZE) {
            throw new IllegalArgumentException("Edge must have exactly 3 elements: weight, source, target");
        }
        for (int i = 0; i < size; i++) {
            if (array[i * ENTRY_SIZE + 1] == getSourceFromEdge(edge) && array[i * ENTRY_SIZE + 2] == getTargetFromEdge(edge)) { // Match source and target
                if (newWeight < array[i * ENTRY_SIZE]) { // Check if the new weight is smaller
                    array[i * ENTRY_SIZE] = newWeight; // Update the weight
                    return new int[] { newWeight, getSourceFromEdge(edge), getTargetFromEdge(edge) };
                } else {
                    throw new IllegalArgumentException("New weight must be smaller than the current weight");
                }
            }
        }
        throw new IllegalArgumentException("Edge not found in heap");
    }

    @Override
    public void clear() {
        this.size = 0;
        this.array = null;
        this.capacity = 0;
    }

    public int getSize() {
        return this.size;
    }

    public int getCapacity() {
        return this.capacity;
    }

    public int[] getArray() {
        return this.array;
    }
}
