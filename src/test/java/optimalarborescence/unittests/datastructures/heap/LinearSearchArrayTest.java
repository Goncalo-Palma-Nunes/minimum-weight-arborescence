package optimalarborescence.unittests.datastructures.heap;

import optimalarborescence.datastructure.heap.LinearSearchArray;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

public class LinearSearchArrayTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Build a raw int[] edge: {weight, source, target} */
    private int[] edge(int weight, int source, int target) {
        return new int[]{weight, source, target};
    }

    /** Build an Edge object using the mock-friendly Node(int id) constructor */
    private Edge edgeObj(int weight, int source, int target) {
        return new Edge(new Node(source), new Node(target), weight);
    }

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    @Test
    public void testCapacityConstructorCreatesEmptyHeap() {
        LinearSearchArray heap = new LinearSearchArray(10);
        assertTrue(heap.isEmpty());
        assertEquals(0, heap.getSize());
        assertEquals(10, heap.getCapacity());
    }

    @Test
    public void testListConstructorPopulatesHeap() {
        List<Edge> edges = new ArrayList<>();
        edges.add(edgeObj(30, 1, 2));
        edges.add(edgeObj(10, 3, 4));
        edges.add(edgeObj(20, 5, 6));

        LinearSearchArray heap = new LinearSearchArray(edges);

        assertFalse(heap.isEmpty());
        assertEquals(3, heap.getSize());
        // min should be weight 10
        assertArrayEquals(new int[]{10, 3, 4}, heap.findMin());
    }

    @Test
    public void testListConstructorWithSingleEdge() {
        List<Edge> edges = List.of(edgeObj(42, 7, 8));
        LinearSearchArray heap = new LinearSearchArray(edges);
        assertFalse(heap.isEmpty());
        assertArrayEquals(new int[]{42, 7, 8}, heap.findMin());
    }

    // -----------------------------------------------------------------------
    // isEmpty
    // -----------------------------------------------------------------------

    @Test
    public void testIsEmptyOnFreshHeap() {
        assertTrue(new LinearSearchArray(5).isEmpty());
    }

    @Test
    public void testIsEmptyAfterInsert() {
        LinearSearchArray heap = new LinearSearchArray(5);
        heap.insert(edge(10, 1, 2));
        assertFalse(heap.isEmpty());
    }

    @Test
    public void testIsEmptyAfterExtractingLastElement() {
        LinearSearchArray heap = new LinearSearchArray(1);
        heap.insert(edge(10, 1, 2));
        heap.extractMin();
        assertTrue(heap.isEmpty());
    }

    // -----------------------------------------------------------------------
    // insert(int[])
    // -----------------------------------------------------------------------

    @Test
    public void testInsertIntArraySingleElement() {
        LinearSearchArray heap = new LinearSearchArray(5);
        heap.insert(edge(15, 1, 2));
        assertFalse(heap.isEmpty());
        assertArrayEquals(new int[]{15, 1, 2}, heap.findMin());
    }

    @Test
    public void testInsertIntArrayMultipleElements() {
        LinearSearchArray heap = new LinearSearchArray(5);
        heap.insert(edge(50, 1, 2));
        heap.insert(edge(10, 3, 4));
        heap.insert(edge(30, 5, 6));
        assertArrayEquals(new int[]{10, 3, 4}, heap.findMin());
    }

    @Test
    public void testInsertIntArrayAutoResizesBeyondInitialCapacity() {
        // Start with capacity 2 and insert 5 elements — should not throw
        LinearSearchArray heap = new LinearSearchArray(2);
        for (int i = 5; i >= 1; i--) {
            heap.insert(edge(i * 10, i, i + 1));
        }
        assertEquals(5, heap.getSize());
        assertArrayEquals(new int[]{10, 1, 2}, heap.findMin());
    }

    @Test
    public void testInsertIntArrayAutoResizesFromZeroCapacity() {
        // capacity 0 → first insert must not throw
        LinearSearchArray heap = new LinearSearchArray(0);
        heap.insert(edge(7, 1, 2));
        assertEquals(1, heap.getSize());
        assertArrayEquals(new int[]{7, 1, 2}, heap.findMin());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInsertIntArrayWrongLengthThrows() {
        LinearSearchArray heap = new LinearSearchArray(5);
        heap.insert(new int[]{10, 1}); // only 2 elements, needs 3
    }

    // -----------------------------------------------------------------------
    // insert(Edge)
    // -----------------------------------------------------------------------

    @Test
    public void testInsertEdgeSingleElement() {
        LinearSearchArray heap = new LinearSearchArray(5);
        heap.insert(edgeObj(25, 1, 2));
        assertFalse(heap.isEmpty());
        assertArrayEquals(new int[]{25, 1, 2}, heap.findMin());
    }

    @Test
    public void testInsertEdgeAutoResizesBeyondInitialCapacity() {
        LinearSearchArray heap = new LinearSearchArray(1);
        heap.insert(edgeObj(40, 1, 2));
        heap.insert(edgeObj(20, 3, 4)); // triggers resize
        heap.insert(edgeObj(60, 5, 6));
        assertEquals(3, heap.getSize());
        assertArrayEquals(new int[]{20, 3, 4}, heap.findMin());
    }

    @Test
    public void testInsertEdgeAutoResizesFromZeroCapacity() {
        LinearSearchArray heap = new LinearSearchArray(0);
        heap.insert(edgeObj(99, 7, 8));
        assertEquals(1, heap.getSize());
        assertArrayEquals(new int[]{99, 7, 8}, heap.findMin());
    }

    // -----------------------------------------------------------------------
    // findMin
    // -----------------------------------------------------------------------

    @Test
    public void testFindMinDoesNotRemoveElement() {
        LinearSearchArray heap = new LinearSearchArray(5);
        heap.insert(edge(10, 1, 2));
        heap.insert(edge(5, 3, 4));
        heap.findMin();
        assertEquals(2, heap.getSize()); // still two elements
        assertArrayEquals(new int[]{5, 3, 4}, heap.findMin());
    }

    @Test
    public void testFindMinWithAllEqualWeights() {
        LinearSearchArray heap = new LinearSearchArray(3);
        heap.insert(edge(7, 1, 2));
        heap.insert(edge(7, 3, 4));
        heap.insert(edge(7, 5, 6));
        assertEquals(7, heap.findMin()[0]);
    }

    @Test(expected = IllegalStateException.class)
    public void testFindMinOnEmptyHeapThrows() {
        new LinearSearchArray(5).findMin();
    }

    // -----------------------------------------------------------------------
    // extractMin
    // -----------------------------------------------------------------------

    @Test
    public void testExtractMinReturnsSingleElement() {
        LinearSearchArray heap = new LinearSearchArray(1);
        heap.insert(edge(42, 1, 2));
        int[] result = heap.extractMin();
        assertArrayEquals(new int[]{42, 1, 2}, result);
        assertTrue(heap.isEmpty());
    }

    @Test
    public void testExtractMinReturnsInSortedOrder() {
        LinearSearchArray heap = new LinearSearchArray(5);
        heap.insert(edge(30, 1, 2));
        heap.insert(edge(10, 3, 4));
        heap.insert(edge(50, 5, 6));
        heap.insert(edge(20, 7, 8));
        heap.insert(edge(40, 9, 10));

        assertEquals(10, heap.extractMin()[0]);
        assertEquals(20, heap.extractMin()[0]);
        assertEquals(30, heap.extractMin()[0]);
        assertEquals(40, heap.extractMin()[0]);
        assertEquals(50, heap.extractMin()[0]);
        assertTrue(heap.isEmpty());
    }

    @Test
    public void testExtractMinDecrementsSizeCorrectly() {
        LinearSearchArray heap = new LinearSearchArray(3);
        heap.insert(edge(10, 1, 2));
        heap.insert(edge(20, 3, 4));
        heap.insert(edge(30, 5, 6));

        heap.extractMin();
        assertEquals(2, heap.getSize());
        heap.extractMin();
        assertEquals(1, heap.getSize());
        heap.extractMin();
        assertEquals(0, heap.getSize());
    }

    @Test
    public void testExtractMinWhenMinIsLast() {
        // Insert in ascending order so the minimum sits at the last position
        LinearSearchArray heap = new LinearSearchArray(3);
        heap.insert(edge(30, 1, 2));
        heap.insert(edge(20, 3, 4));
        heap.insert(edge(10, 5, 6)); // min at index 2 (last)

        assertArrayEquals(new int[]{10, 5, 6}, heap.extractMin());
        assertEquals(2, heap.getSize());
        assertEquals(20, heap.findMin()[0]);
    }

    @Test(expected = IllegalStateException.class)
    public void testExtractMinOnEmptyHeapThrows() {
        new LinearSearchArray(5).extractMin();
    }

    // -----------------------------------------------------------------------
    // merge
    // -----------------------------------------------------------------------

    @Test
    public void testMergeTwoPopulatedHeaps() {
        LinearSearchArray heap1 = new LinearSearchArray(2);
        heap1.insert(edge(30, 1, 2));
        heap1.insert(edge(50, 3, 4));

        LinearSearchArray heap2 = new LinearSearchArray(2);
        heap2.insert(edge(10, 5, 6));
        heap2.insert(edge(20, 7, 8));

        heap1.merge(heap2);

        assertEquals(4, heap1.getSize());
        assertArrayEquals(new int[]{10, 5, 6}, heap1.findMin());
    }

    @Test
    public void testMergeEmptyIntoPopulated() {
        LinearSearchArray heap1 = new LinearSearchArray(2);
        heap1.insert(edge(15, 1, 2));

        LinearSearchArray emptyHeap = new LinearSearchArray(5);
        heap1.merge(emptyHeap);

        assertEquals(1, heap1.getSize());
        assertArrayEquals(new int[]{15, 1, 2}, heap1.findMin());
    }

    @Test
    public void testMergePopulatedIntoEmpty() {
        LinearSearchArray emptyHeap = new LinearSearchArray(0);

        LinearSearchArray heap2 = new LinearSearchArray(2);
        heap2.insert(edge(15, 1, 2));
        heap2.insert(edge(5, 3, 4));

        emptyHeap.merge(heap2);

        assertEquals(2, emptyHeap.getSize());
        assertArrayEquals(new int[]{5, 3, 4}, emptyHeap.findMin());
    }

    @Test
    public void testMergeAndExtractAllInOrder() {
        LinearSearchArray heap1 = new LinearSearchArray(3);
        heap1.insert(edge(25, 1, 2));
        heap1.insert(edge(5, 3, 4));
        heap1.insert(edge(15, 5, 6));

        LinearSearchArray heap2 = new LinearSearchArray(3);
        heap2.insert(edge(10, 7, 8));
        heap2.insert(edge(30, 9, 10));
        heap2.insert(edge(20, 11, 12));

        heap1.merge(heap2);

        int[] expectedWeights = {5, 10, 15, 20, 25, 30};
        for (int expected : expectedWeights) {
            assertEquals(expected, heap1.extractMin()[0]);
        }
        assertTrue(heap1.isEmpty());
    }

    @Test
    public void testMergePreservesCapacityExactly() {
        // After merge the new capacity should == combined size (no wasted space)
        LinearSearchArray heap1 = new LinearSearchArray(3);
        heap1.insert(edge(10, 1, 2));
        heap1.insert(edge(20, 3, 4));

        LinearSearchArray heap2 = new LinearSearchArray(3);
        heap2.insert(edge(30, 5, 6));

        heap1.merge(heap2);

        assertEquals(3, heap1.getSize());
        assertEquals(3, heap1.getCapacity());
    }

    @Test
    public void testMergedHeapStillAcceptsInserts() {
        // After a tight merge (capacity == size), subsequent inserts should auto-resize
        LinearSearchArray heap1 = new LinearSearchArray(1);
        heap1.insert(edge(20, 1, 2));

        LinearSearchArray heap2 = new LinearSearchArray(1);
        heap2.insert(edge(10, 3, 4));

        heap1.merge(heap2);
        // capacity is now 2 == size; next insert must resize
        heap1.insert(edge(5, 5, 6));

        assertEquals(3, heap1.getSize());
        assertArrayEquals(new int[]{5, 5, 6}, heap1.findMin());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMergeWithWrongTypeThrows() {
        LinearSearchArray heap = new LinearSearchArray(5);
        heap.insert(edge(10, 1, 2));
        // Pass an anonymous implementation that is not a LinearSearchArray
        heap.merge(new optimalarborescence.datastructure.heap.MergeableHeapInterface<int[]>() {
            public optimalarborescence.datastructure.heap.MergeableHeapInterface<int[]> merge(optimalarborescence.datastructure.heap.MergeableHeapInterface<int[]> o) { return this; }
            public void insert(int[] v) {}
            public int[] extractMin() { return null; }
            public int[] decreaseKey(int[] n, int v) { return null; }
            public int[] findMin() { return null; }
            public boolean isEmpty() { return true; }
            public void clear() {}
        });
    }

    // -----------------------------------------------------------------------
    // decreaseKey
    // -----------------------------------------------------------------------

    @Test
    public void testDecreaseKeyUpdatesWeight() {
        LinearSearchArray heap = new LinearSearchArray(3);
        heap.insert(edge(30, 1, 2));
        heap.insert(edge(20, 3, 4));
        heap.insert(edge(10, 5, 6));

        int[] updated = heap.decreaseKey(edge(30, 1, 2), 5);
        assertArrayEquals(new int[]{5, 1, 2}, updated);
        assertArrayEquals(new int[]{5, 1, 2}, heap.findMin());
    }

    @Test
    public void testDecreaseKeyOnCurrentMin() {
        LinearSearchArray heap = new LinearSearchArray(3);
        heap.insert(edge(10, 1, 2));
        heap.insert(edge(20, 3, 4));
        heap.insert(edge(30, 5, 6));

        int[] updated = heap.decreaseKey(edge(10, 1, 2), 1);
        assertEquals(1, updated[0]);
        assertEquals(1, heap.findMin()[0]);
    }

    @Test
    public void testDecreaseKeyNewMinBubblesToTop() {
        LinearSearchArray heap = new LinearSearchArray(3);
        heap.insert(edge(50, 1, 2));
        heap.insert(edge(30, 3, 4));
        heap.insert(edge(10, 5, 6));

        // Decrease the weight-50 edge so it becomes the new minimum
        heap.decreaseKey(edge(50, 1, 2), 2);
        assertArrayEquals(new int[]{2, 1, 2}, heap.findMin());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecreaseKeyWithLargerWeightThrows() {
        LinearSearchArray heap = new LinearSearchArray(3);
        heap.insert(edge(10, 1, 2));
        heap.decreaseKey(edge(10, 1, 2), 20); // 20 > 10: invalid
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecreaseKeyWithEqualWeightThrows() {
        LinearSearchArray heap = new LinearSearchArray(3);
        heap.insert(edge(10, 1, 2));
        heap.decreaseKey(edge(10, 1, 2), 10); // same weight: invalid
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecreaseKeyEdgeNotFoundThrows() {
        LinearSearchArray heap = new LinearSearchArray(3);
        heap.insert(edge(10, 1, 2));
        heap.decreaseKey(edge(10, 9, 9), 5); // source/target don't exist
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecreaseKeyWrongLengthThrows() {
        LinearSearchArray heap = new LinearSearchArray(3);
        heap.insert(edge(10, 1, 2));
        heap.decreaseKey(new int[]{10, 1}, 5); // only 2 elements
    }

    // -----------------------------------------------------------------------
    // clear
    // -----------------------------------------------------------------------

    @Test
    public void testClearResetsHeap() {
        LinearSearchArray heap = new LinearSearchArray(5);
        heap.insert(edge(10, 1, 2));
        heap.insert(edge(20, 3, 4));

        heap.clear();

        assertEquals(0, heap.getSize());
        assertEquals(0, heap.getCapacity());
        assertNull(heap.getArray());
        assertTrue(heap.isEmpty());
    }
}
