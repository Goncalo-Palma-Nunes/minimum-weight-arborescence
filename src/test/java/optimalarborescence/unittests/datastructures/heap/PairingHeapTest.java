package optimalarborescence.unittests.datastructures.heap;

import optimalarborescence.datastructure.heap.PairingHeap;
import optimalarborescence.datastructure.heap.HeapNode;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Edge;

import org.junit.Test;
import static org.junit.Assert.*;

public class PairingHeapTest {

    /** largeWeight = 200 */
    private int largeWeight = 200;

    /** smallWeight = 1 */
    private int smallWeight = 1;

    /** intermediateWeight = 100 */
    private int intermediateWeight = 100;

    /** smallIntermediateWeight = 50 */
    private int smallIntermediateWeight = 50;

    /** weight = largeWeight = 200 */
    HeapNode node1 = new HeapNode(
        new Edge(
            new Node("A", 1), 
            new Node("C", 2), 
            largeWeight), 
        null, 
        null);

    /** weight = intermediateWeight = 100 */
    HeapNode node2 = new HeapNode(
        new Edge(
            new Node("T", 3), 
            new Node("G", 4), 
            intermediateWeight), 
        null, 
        null);

    /** weight = smallWeight = 1 */
    HeapNode lowestWeightNode = new HeapNode(
        new Edge(
            new Node("G", 5), 
            new Node("T", 6), 
            smallWeight), 
        null, 
        null);

    /** weight = smallIntermediateWeight = 50 */
    HeapNode smallIntermediateNode = new HeapNode(
        new Edge(
            new Node("C", 7), 
            new Node("A", 8), 
            smallIntermediateWeight), 
        null, 
        null);

    @Test
    public void testEmptyConstructor() {
        PairingHeap heap = new PairingHeap();
        assertTrue(heap.isEmpty());
    }

    @Test
    public void testPopulatedConstructor() {
        PairingHeap heap = new PairingHeap(node1);
        assertFalse(heap.isEmpty());
        assertEquals(node1, heap.findMin());
    }

    @Test
    public void testFindMin() {
        PairingHeap heap = new PairingHeap(node1);
        assertEquals(node1, heap.findMin());
    }

    @Test
    public void testIsEmpty() {
        PairingHeap emptyHeap = new PairingHeap();
        assertTrue(emptyHeap.isEmpty());

        PairingHeap populatedHeap = new PairingHeap(node1);
        assertFalse(populatedHeap.isEmpty());
    }

    @Test
    public void testMergeEmptyHeaps() {
        PairingHeap heap1 = new PairingHeap();
        PairingHeap heap2 = new PairingHeap();

        try {
            heap1.merge(heap2);
            fail("Expected IllegalArgumentException for merging empty heaps");
        } catch (IllegalArgumentException e) {
            // Expected exception
            assertEquals("Neither heap should be empty", e.getMessage());
        }

        PairingHeap populatedHeap = new PairingHeap(node1);
        try {
            populatedHeap.merge(heap1);
            fail("Expected IllegalArgumentException for merging with an empty heap");
        } catch (IllegalArgumentException e) {
            // Expected exception
            assertEquals("Neither heap should be empty", e.getMessage());
        }

        try {
            heap1.merge(populatedHeap);
            fail("Expected IllegalArgumentException for merging with an empty heap");
        } catch (IllegalArgumentException e) {
            // Expected exception
            assertEquals("Neither heap should be empty", e.getMessage());
        }
    }

    @Test
    public void testMergePopulatedHeaps() {
        HeapNode node2 = new HeapNode(
            new Edge(
                new Node("T", 3), 
                new Node("G", 4), 
                smallWeight), 
            null, 
            null);
        PairingHeap heap1 = new PairingHeap(node1);
        PairingHeap heap2 = new PairingHeap(node2);
        heap1.merge(heap2);
        assertEquals(node2, heap1.findMin());
    }

    @Test
    public void testInsertOnEmptyHeap() {
        PairingHeap heap = new PairingHeap();
        heap.insert(node1);
        assertEquals(node1, heap.findMin());
    }

    @Test
    public void testInsertOnPopulatedHeap() {
        PairingHeap heap = new PairingHeap();
        heap.insert(node1);
        heap.insert(smallIntermediateNode);
        assertEquals(smallIntermediateNode, heap.findMin());

        heap.insert(node2);
        assertEquals(smallIntermediateNode, heap.findMin());

        heap.insert(lowestWeightNode);
        assertEquals(lowestWeightNode, heap.findMin());
    }

    @Test
    public void testDecreaseRootKey() {
        PairingHeap heap = new PairingHeap();
        heap.insert(node1);
        heap.insert(node2);
        heap.insert(smallIntermediateNode);

        HeapNode decreasedNode = heap.decreaseKey(heap.findMin(), smallWeight);
        assertEquals(decreasedNode, heap.findMin());
        assertEquals(smallWeight, decreasedNode.getVal());
        assertEquals(smallWeight, heap.findMin().getVal());
    }

    @Test
    public void testDecreaseNonRootKey() {
        PairingHeap heap = new PairingHeap();
        heap.insert(node1);
        heap.insert(node2);
        heap.insert(smallIntermediateNode);

        assertEquals(smallIntermediateNode, heap.findMin());
        HeapNode decreasedNode = heap.decreaseKey(node2, smallWeight);
        assertEquals(decreasedNode, heap.findMin());
        assertEquals(decreasedNode, node2);
        assertEquals(node2, heap.findMin());
        assertEquals(smallWeight, decreasedNode.getVal());
        assertEquals(smallWeight, heap.findMin().getVal());
    }

    @Test
    public void testExtractMin() {
        PairingHeap heap = new PairingHeap();
        heap.insert(node1);
        heap.insert(node2);
        heap.insert(smallIntermediateNode);
        heap.insert(lowestWeightNode);

        assertEquals(lowestWeightNode, heap.extractMin());
        assertEquals(smallIntermediateNode, heap.findMin());

        assertEquals(smallIntermediateNode, heap.extractMin());
        assertEquals(node2, heap.findMin());

        assertEquals(node2, heap.extractMin());
        assertEquals(node1, heap.findMin());

        assertEquals(node1, heap.extractMin());
        assertTrue(heap.isEmpty());
    }

    @Test
    public void testExtractMinOnEmptyHeap() {
        PairingHeap heap = new PairingHeap();
        assertEquals(null, heap.extractMin());
    }

    @Test
    public void testDecreaseAllKeys() {
        PairingHeap heap = new PairingHeap();
        heap.insert(node1);
        heap.insert(node2);
        heap.insert(smallIntermediateNode);

        heap.decreaseAllKeys(25);
        assertEquals(smallIntermediateNode, heap.findMin());
        assertEquals(smallIntermediateWeight - 25, heap.findMin().getVal());

        heap.extractMin();
        assertEquals(node2, heap.findMin());
        assertEquals(intermediateWeight - 25, heap.findMin().getVal());

        heap.extractMin();
        assertEquals(node1, heap.findMin());
        assertEquals(largeWeight - 25, heap.findMin().getVal());
    }
}