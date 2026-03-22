package optimalarborescence.datastructure.heap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import optimalarborescence.graph.Edge;

public class LinearSearchArray implements MergeableHeapInterface<int[]> {

    protected int capacity;
    protected int size;
    protected int[] array;
    private final Comparator<int[]> comparator;
    private List<LinearSearchArray> linked;  // null when no arrays are linked

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

    /** Returns true if a is less than b under this heap's ordering (local array only). */
    private boolean isLess(int indexA, int indexB) {
        if (comparator != null) {
            int[] a = new int[]{ array[indexA * ENTRY_SIZE], array[indexA * ENTRY_SIZE + 1], array[indexA * ENTRY_SIZE + 2] };
            int[] b = new int[]{ array[indexB * ENTRY_SIZE], array[indexB * ENTRY_SIZE + 1], array[indexB * ENTRY_SIZE + 2] };
            return comparator.compare(a, b) < 0;
        }
        return array[indexA * ENTRY_SIZE] < array[indexB * ENTRY_SIZE];
    }

    /** Find index of the minimum entry in THIS array only (no linked). */
    private int localMinIndex() {
        int minIdx = 0;
        for (int i = 1; i < size; i++) {
            if (isLess(i, minIdx)) minIdx = i;
        }
        return minIdx;
    }

    /** Compare entry at idxA in array A with entry at idxB in array B. */
    private int compareBetween(LinearSearchArray a, int idxA, LinearSearchArray b, int idxB) {
        if (comparator != null) {
            int[] entryA = new int[]{ a.array[idxA * ENTRY_SIZE], a.array[idxA * ENTRY_SIZE + 1], a.array[idxA * ENTRY_SIZE + 2] };
            int[] entryB = new int[]{ b.array[idxB * ENTRY_SIZE], b.array[idxB * ENTRY_SIZE + 1], b.array[idxB * ENTRY_SIZE + 2] };
            return comparator.compare(entryA, entryB);
        }
        return Integer.compare(a.array[idxA * ENTRY_SIZE], b.array[idxB * ENTRY_SIZE]);
    }

    private int getSourceFromEdge(int[] edge) {
        return edge[1];
    }

    private int getTargetFromEdge(int[] edge) {
        return edge[2];
    }

    @Override
    public boolean isEmpty() {
        if (this.size > 0) return false;
        if (linked != null) {
            for (LinearSearchArray la : linked) {
                if (la.size > 0) return false;
            }
        }
        return true;
    }

    @Override
    public MergeableHeapInterface<int[]> merge(MergeableHeapInterface<int[]> other) {
        if (!(other instanceof LinearSearchArray)) {
            throw new IllegalArgumentException("Expected an argument of type LinearSearchArray");
        }

        LinearSearchArray otherHeap = (LinearSearchArray) other;
        if (otherHeap.isEmpty()) {
            return this;
        }

        if (this.linked == null) this.linked = new ArrayList<>();
        this.linked.add(otherHeap);
        // Flatten: adopt other's linked arrays to avoid tree-shaped traversal
        if (otherHeap.linked != null) {
            this.linked.addAll(otherHeap.linked);
            otherHeap.linked = null;
        }
        return this;
    }

    @Override
    public int[] findMin() {
        if (this.isEmpty()) {
            throw new IllegalStateException("Heap is empty");
        }

        LinearSearchArray bestArr = null;
        int bestIdx = -1;

        if (this.size > 0) {
            bestArr = this;
            bestIdx = localMinIndex();
        }
        if (linked != null) {
            for (LinearSearchArray la : linked) {
                if (la.size > 0) {
                    int laIdx = la.localMinIndex();
                    if (bestArr == null || compareBetween(la, laIdx, bestArr, bestIdx) < 0) {
                        bestArr = la;
                        bestIdx = laIdx;
                    }
                }
            }
        }

        return new int[]{ bestArr.array[bestIdx * ENTRY_SIZE],
                          bestArr.array[bestIdx * ENTRY_SIZE + 1],
                          bestArr.array[bestIdx * ENTRY_SIZE + 2] };
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


    public int[] extractMin(int index) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("Index out of bounds");
        }
        int[] minEdge = new int[] { array[index * ENTRY_SIZE], array[index * ENTRY_SIZE + 1], array[index * ENTRY_SIZE + 2] };

        // Shift the last edge to the position of the extracted edge
        array[index * ENTRY_SIZE] = array[(size - 1) * ENTRY_SIZE];
        array[index * ENTRY_SIZE + 1] = array[(size - 1) * ENTRY_SIZE + 1];
        array[index * ENTRY_SIZE + 2] = array[(size - 1) * ENTRY_SIZE + 2];
        size--;

        return minEdge;
    }

    @Override
    public int[] extractMin() {
        if (this.isEmpty()) {
            throw new IllegalStateException("Heap is empty");
        }

        LinearSearchArray bestArr = null;
        int bestIdx = -1;

        if (this.size > 0) {
            bestArr = this;
            bestIdx = localMinIndex();
        }
        if (linked != null) {
            for (LinearSearchArray la : linked) {
                if (la.size > 0) {
                    int laIdx = la.localMinIndex();
                    if (bestArr == null || compareBetween(la, laIdx, bestArr, bestIdx) < 0) {
                        bestArr = la;
                        bestIdx = laIdx;
                    }
                }
            }
        }

        return bestArr.extractMin(bestIdx);
    }

    @Override
    public int[] decreaseKey(int[] edge, int newWeight) {
        if (edge.length != ENTRY_SIZE) {
            throw new IllegalArgumentException("Edge must have exactly 3 elements: weight, source, target");
        }
        // Search in this array
        int[] result = localDecreaseKey(edge, newWeight);
        if (result != null) return result;
        // Search in linked arrays
        if (linked != null) {
            for (LinearSearchArray la : linked) {
                result = la.localDecreaseKey(edge, newWeight);
                if (result != null) return result;
            }
        }
        throw new IllegalArgumentException("Edge not found in heap");
    }

    /**
     * Attempts to decrease the key of an edge in this local array only.
     * Returns the updated edge if found and decreased, null if not found.
     * Throws if found but newWeight is not smaller.
     */
    private int[] localDecreaseKey(int[] edge, int newWeight) {
        for (int i = 0; i < size; i++) {
            if (array[i * ENTRY_SIZE + 1] == getSourceFromEdge(edge) && array[i * ENTRY_SIZE + 2] == getTargetFromEdge(edge)) {
                if (newWeight < array[i * ENTRY_SIZE]) {
                    array[i * ENTRY_SIZE] = newWeight;
                    return new int[] { newWeight, getSourceFromEdge(edge), getTargetFromEdge(edge) };
                } else {
                    throw new IllegalArgumentException("New weight must be smaller than the current weight");
                }
            }
        }
        return null;
    }

    @Override
    public void clear() {
        this.size = 0;
        this.array = null;
        this.capacity = 0;
        if (linked != null) {
            for (LinearSearchArray la : linked) {
                la.size = 0;
                la.array = null;
                la.capacity = 0;
            }
            linked = null;
        }
    }

    public int getSize() {
        int total = this.size;
        if (linked != null) {
            for (LinearSearchArray la : linked) {
                total += la.size;
            }
        }
        return total;
    }

    public int getCapacity() {
        return this.capacity;
    }

    public int[] getArray() {
        return this.array;
    }


    public static int[] extractMinFromSetOfArrays(List<LinearSearchArray> heaps) {
        int minWeight = Integer.MAX_VALUE;
        int heapIndex = -1;

        for (int i = 0; i < heaps.size(); i++) {
            LinearSearchArray heap = heaps.get(i);
            if (!heap.isEmpty()) {
                int[] minEdge = heap.findMin();
                if (minEdge[0] < minWeight) { // Compare weights
                    minWeight = minEdge[0];
                    heapIndex = i; // Remember which heap it came from
                }
            }
        }

        if (heapIndex == -1) {
            throw new IllegalStateException("All heaps are empty");
        }

        return heaps.get(heapIndex).extractMin();
    }
}
