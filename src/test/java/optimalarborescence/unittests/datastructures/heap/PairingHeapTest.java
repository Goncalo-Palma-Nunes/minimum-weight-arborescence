package optimalarborescence.unittests.datastructures.heap;

import optimalarborescence.datastructure.heap.PairingHeap;
import optimalarborescence.datastructure.heap.HeapNode;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Edge;

import java.util.List;

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

    @Test
    public void testMultipleSequentialMerges() {
        // Create multiple heaps
        HeapNode nodeA = new HeapNode(new Edge(new Node("AAAA", 1), new Node("TTTT", 2), 10), null, null);
        HeapNode nodeB = new HeapNode(new Edge(new Node("CCCC", 3), new Node("GGGG", 4), 20), null, null);
        HeapNode nodeC = new HeapNode(new Edge(new Node("ACGT", 5), new Node("TGCA", 6), 5), null, null);
        HeapNode nodeD = new HeapNode(new Edge(new Node("ATCG", 7), new Node("CGTA", 8), 15), null, null);
        
        PairingHeap heap1 = new PairingHeap(nodeA);
        PairingHeap heap2 = new PairingHeap(nodeB);
        PairingHeap heap3 = new PairingHeap(nodeC);
        PairingHeap heap4 = new PairingHeap(nodeD);
        
        // Sequential merges: heap1 <- heap2 <- heap3 <- heap4
        heap1.merge(heap2);
        assertEquals(nodeA, heap1.findMin()); // 10 is still min
        
        heap1.merge(heap3);
        assertEquals(nodeC, heap1.findMin()); // 5 is now min
        
        heap1.merge(heap4);
        assertEquals(nodeC, heap1.findMin()); // 5 is still min
        
        // Extract all elements and verify order
        assertEquals(5, heap1.extractMin().getEdge().getWeight()); // 5
        assertEquals(10, heap1.extractMin().getEdge().getWeight()); // 10
        assertEquals(15, heap1.extractMin().getEdge().getWeight()); // 15
        assertEquals(20, heap1.extractMin().getEdge().getWeight()); // 20
        assertTrue(heap1.isEmpty());
    }

    @Test
    public void testMergeExtractMinMerge() {
        // This pattern mimics what happens in Tarjan's algorithm
        HeapNode nodeA = new HeapNode(new Edge(new Node("AAAA", 1), new Node("TTTT", 2), 30), null, null);
        HeapNode nodeB = new HeapNode(new Edge(new Node("CCCC", 3), new Node("GGGG", 4), 10), null, null);
        HeapNode nodeC = new HeapNode(new Edge(new Node("ACGT", 5), new Node("TGCA", 6), 20), null, null);
        HeapNode nodeD = new HeapNode(new Edge(new Node("ATCG", 7), new Node("CGTA", 8), 15), null, null);
        
        PairingHeap heap1 = new PairingHeap(nodeA);
        PairingHeap heap2 = new PairingHeap(nodeB);
        
        // Merge and extract
        heap1.merge(heap2);
        assertEquals(nodeB, heap1.extractMin()); // Extract 10
        assertEquals(nodeA, heap1.findMin()); // 30 is now min
        
        // Merge again with another heap
        PairingHeap heap3 = new PairingHeap(nodeC);
        heap1.merge(heap3);
        assertEquals(nodeC, heap1.findMin()); // 20 is now min
        
        // Merge once more
        PairingHeap heap4 = new PairingHeap(nodeD);
        heap1.merge(heap4);
        assertEquals(nodeD, heap1.findMin()); // 15 is now min
        
        // Extract remaining
        assertEquals(15, heap1.extractMin().getEdge().getWeight()); // 15
        assertEquals(20, heap1.extractMin().getEdge().getWeight()); // 20
        assertEquals(30, heap1.extractMin().getEdge().getWeight()); // 30
        assertTrue(heap1.isEmpty());
    }

    @Test
    public void testExtractMinAfterComplexMerges() {
        // Create heaps with multiple nodes to test complex internal structures
        PairingHeap heap1 = new PairingHeap();
        heap1.insert(new HeapNode(new Edge(new Node("AAAA", 1), new Node("TTTT", 2), 50), null, null));
        heap1.insert(new HeapNode(new Edge(new Node("CCCC", 3), new Node("GGGG", 4), 60), null, null));
        heap1.insert(new HeapNode(new Edge(new Node("ACGT", 5), new Node("TGCA", 6), 70), null, null));
        
        PairingHeap heap2 = new PairingHeap();
        heap2.insert(new HeapNode(new Edge(new Node("ATCG", 7), new Node("CGTA", 8), 30), null, null));
        heap2.insert(new HeapNode(new Edge(new Node("ATAT", 9), new Node("GCGC", 10), 40), null, null));
        
        PairingHeap heap3 = new PairingHeap();
        heap3.insert(new HeapNode(new Edge(new Node("TATA", 11), new Node("GCAA", 12), 10), null, null));
        heap3.insert(new HeapNode(new Edge(new Node("CTCT", 13), new Node("AGAG", 14), 20), null, null));
        heap3.insert(new HeapNode(new Edge(new Node("GTGT", 15), new Node("CACA", 16), 25), null, null));
        
        // Merge all three
        heap1.merge(heap2);
        heap1.merge(heap3);
        
        // Extract all elements and verify no infinite loops occur
        assertEquals(10, heap1.extractMin().getEdge().getWeight());
        assertEquals(20, heap1.extractMin().getEdge().getWeight());
        assertEquals(25, heap1.extractMin().getEdge().getWeight());
        assertEquals(30, heap1.extractMin().getEdge().getWeight());
        assertEquals(40, heap1.extractMin().getEdge().getWeight());
        assertEquals(50, heap1.extractMin().getEdge().getWeight());
        assertEquals(60, heap1.extractMin().getEdge().getWeight());
        assertEquals(70, heap1.extractMin().getEdge().getWeight());
        assertTrue(heap1.isEmpty());
    }

    @Test
    public void testDeepHeapMerge() {
        // Create a heap with multiple children, then merge with another complex heap
        PairingHeap heap1 = new PairingHeap();
        heap1.insert(new HeapNode(new Edge(new Node("AAAA", 1), new Node("TTTT", 2), 100), null, null));
        heap1.insert(new HeapNode(new Edge(new Node("CCCC", 3), new Node("GGGG", 4), 110), null, null));
        heap1.insert(new HeapNode(new Edge(new Node("ACGT", 5), new Node("TGCA", 6), 120), null, null));
        heap1.insert(new HeapNode(new Edge(new Node("ATCG", 7), new Node("CGTA", 8), 130), null, null));
        
        // Extract min to create a deeper structure
        heap1.extractMin(); // This restructures the heap
        
        PairingHeap heap2 = new PairingHeap();
        heap2.insert(new HeapNode(new Edge(new Node("ATAT", 9), new Node("GCGC", 10), 90), null, null));
        heap2.insert(new HeapNode(new Edge(new Node("TATA", 11), new Node("GCAA", 12), 95), null, null));
        heap2.insert(new HeapNode(new Edge(new Node("CTCT", 13), new Node("AGAG", 14), 105), null, null));
        
        // Extract min from heap2 as well
        heap2.extractMin();
        
        // Now merge two heaps that have both been restructured by extractMin
        heap1.merge(heap2);
        
        // Verify we can extract all elements without infinite loops
        assertEquals(95, heap1.extractMin().getEdge().getWeight());
        assertEquals(105, heap1.extractMin().getEdge().getWeight());
        assertEquals(110, heap1.extractMin().getEdge().getWeight());
        assertEquals(120, heap1.extractMin().getEdge().getWeight());
        assertEquals(130, heap1.extractMin().getEdge().getWeight());
        assertTrue(heap1.isEmpty());
    }

    @Test
    public void testExtractAllAfterMultipleMerges() {
        // This test specifically targets the scenario that caused the infinite loop bug
        // Multiple merges followed by extractMin operations
        PairingHeap mainHeap = new PairingHeap();
        mainHeap.insert(new HeapNode(new Edge(new Node("AAAA", 1), new Node("TTTT", 2), 50), null, null));
        
        // Perform multiple merge operations
        String[] seqs1 = {"ACGT", "TGCA", "ATCG", "CGTA", "ATAT"};
        String[] seqs2 = {"GCGC", "TATA", "GCAA", "CTCT", "AGAG"};
        for (int i = 0; i < 5; i++) {
            PairingHeap tempHeap = new PairingHeap();
            tempHeap.insert(new HeapNode(new Edge(new Node(seqs1[i], i * 2 + 3), new Node(seqs2[i], i * 2 + 4), 40 - i * 5), null, null));
            tempHeap.insert(new HeapNode(new Edge(new Node(seqs2[i], i * 2 + 5), new Node(seqs1[i], i * 2 + 6), 45 - i * 5), null, null));
            mainHeap.merge(tempHeap);
        }
        
        // Now extract all elements - this should not infinite loop
        int previousWeight = Integer.MIN_VALUE;
        while (!mainHeap.isEmpty()) {
            HeapNode extracted = mainHeap.extractMin();
            assertNotNull(extracted);
            int currentWeight = extracted.getEdge().getWeight();
            assertTrue("Heap should maintain min-heap property", currentWeight >= previousWeight);
            previousWeight = currentWeight;
        }
        
        assertTrue(mainHeap.isEmpty());
    }


    @Test
    public void testCorrectOrderAfterSeveralMerges() {
        Node node1 = new Node("ACGT", 1);
        Node node2 = new Node("TGCA", 2);

        // First Heap
        PairingHeap heap1 = new PairingHeap();
        HeapNode heapNode1 = new HeapNode(new Edge(node1, node2, 25), null, null);
        HeapNode heapNode2 = new HeapNode(new Edge(node2, node1, 15), null, null);
        HeapNode heapNode3 = new HeapNode(new Edge(node1, node2, 5), null, null);
        HeapNode heapNode4 = new HeapNode(new Edge(node2, node1, 10), null, null);
        HeapNode heapNode5 = new HeapNode(new Edge(node1, node2, 20), null, null);
        heap1.insert(heapNode1);
        heap1.insert(heapNode2);
        heap1.insert(heapNode3);
        heap1.insert(heapNode4);
        heap1.insert(heapNode5);

        // Second Heap
        PairingHeap heap2 = new PairingHeap();
        HeapNode heapNode6 = new HeapNode(new Edge(node2, node1, 18), null, null);
        HeapNode heapNode7 = new HeapNode(new Edge(node1, node2, 8), null, null);
        HeapNode heapNode8 = new HeapNode(new Edge(node2, node1, 28), null, null);
        HeapNode heapNode9 = new HeapNode(new Edge(node1, node2, 12), null, null);
        HeapNode heapNode10 = new HeapNode(new Edge(node2, node1, 22), null, null);
        heap2.insert(heapNode6);
        heap2.insert(heapNode7);
        heap2.insert(heapNode8);
        heap2.insert(heapNode9);
        heap2.insert(heapNode10);

        // Third Heap
        PairingHeap heap3 = new PairingHeap();
        HeapNode heapNode11 = new HeapNode(new Edge(node1, node2, 30), null, null);
        HeapNode heapNode12 = new HeapNode(new Edge(node2, node1, 3), null, null);
        HeapNode heapNode13 = new HeapNode(new Edge(node1, node2, 27), null, null);
        HeapNode heapNode14 = new HeapNode(new Edge(node2, node1, 17), null, null);
        HeapNode heapNode15 = new HeapNode(new Edge(node1, node2, 13), null, null);
        heap3.insert(heapNode11);
        heap3.insert(heapNode12);
        heap3.insert(heapNode13);
        heap3.insert(heapNode14);
        heap3.insert(heapNode15);

        List<HeapNode> allNodes = List.of(
            heapNode1, heapNode2, heapNode3, heapNode4, heapNode5,
            heapNode6, heapNode7, heapNode8, heapNode9, heapNode10,
            heapNode11, heapNode12, heapNode13, heapNode14, heapNode15
        );
        List<Edge> sortedEdges = allNodes.stream()
            .map(HeapNode::getEdge)
            .sorted((e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight()))
            .toList();

        // Merge all heaps
        heap1.merge(heap2);
        heap1.merge(heap3);

        // Extract all elements and verify order
        for (Edge expectedEdge : sortedEdges) {
            HeapNode extractedNode = heap1.extractMin();
            assertNotNull("Extracted node should not be null", extractedNode);
            Edge extractedEdge = extractedNode.getEdge();
            assertEquals("Extracted edge weight should match expected", expectedEdge.getWeight(), extractedEdge.getWeight());
        }
    }
}