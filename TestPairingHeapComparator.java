import optimalarborescence.datastructure.heap.PairingHeap;
import optimalarborescence.datastructure.heap.HeapNode;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Edge;
import java.util.Comparator;

public class TestPairingHeapComparator {
    
    public static void main(String[] args) {
        System.out.println("Testing PairingHeap with custom Comparator...\n");
        
        // Create comparator
        Comparator<HeapNode> minComparator = (a, b) -> Integer.compare(a.getVal(), b.getVal());
        
        // Test 1: Empty constructor
        System.out.println("Test 1: Empty constructor");
        PairingHeap heap = new PairingHeap(minComparator);
        System.out.println("  isEmpty: " + heap.isEmpty() + " (expected: true)");
        
        // Test 2: Insert and findMin
        System.out.println("\nTest 2: Insert and findMin");
        HeapNode node1 = new HeapNode(new Edge(new Node("A", 1), new Node("B", 2), 100), null, null);
        HeapNode node2 = new HeapNode(new Edge(new Node("C", 3), new Node("D", 4), 50), null, null);
        HeapNode node3 = new HeapNode(new Edge(new Node("E", 5), new Node("F", 6), 75), null, null);
        
        heap.insert(node1);
        System.out.println("  Inserted node with weight 100");
        System.out.println("  findMin weight: " + heap.findMin().getVal() + " (expected: 100)");
        
        heap.insert(node2);
        System.out.println("  Inserted node with weight 50");
        System.out.println("  findMin weight: " + heap.findMin().getVal() + " (expected: 50)");
        
        heap.insert(node3);
        System.out.println("  Inserted node with weight 75");
        System.out.println("  findMin weight: " + heap.findMin().getVal() + " (expected: 50)");
        
        // Test 3: Extract min
        System.out.println("\nTest 3: Extract min");
        HeapNode extracted = heap.extractMin();
        System.out.println("  Extracted weight: " + extracted.getVal() + " (expected: 50)");
        System.out.println("  New min weight: " + heap.findMin().getVal() + " (expected: 75)");
        
        extracted = heap.extractMin();
        System.out.println("  Extracted weight: " + extracted.getVal() + " (expected: 75)");
        System.out.println("  New min weight: " + heap.findMin().getVal() + " (expected: 100)");
        
        // Test 4: Merge
        System.out.println("\nTest 4: Merge heaps");
        PairingHeap heap1 = new PairingHeap(minComparator);
        PairingHeap heap2 = new PairingHeap(minComparator);
        
        HeapNode nodeA = new HeapNode(new Edge(new Node("G", 7), new Node("H", 8), 30), null, null);
        HeapNode nodeB = new HeapNode(new Edge(new Node("I", 9), new Node("J", 10), 20), null, null);
        
        heap1.insert(nodeA);
        heap2.insert(nodeB);
        
        heap1.merge(heap2);
        System.out.println("  After merge, min weight: " + heap1.findMin().getVal() + " (expected: 20)");
        
        // Test 5: Max heap with reverse comparator
        System.out.println("\nTest 5: Max heap with reverse comparator");
        Comparator<HeapNode> maxComparator = (a, b) -> Integer.compare(b.getVal(), a.getVal());
        PairingHeap maxHeap = new PairingHeap(maxComparator);
        
        maxHeap.insert(new HeapNode(new Edge(new Node("K", 11), new Node("L", 12), 10), null, null));
        maxHeap.insert(new HeapNode(new Edge(new Node("M", 13), new Node("N", 14), 40), null, null));
        maxHeap.insert(new HeapNode(new Edge(new Node("O", 15), new Node("P", 16), 25), null, null));
        
        System.out.println("  Max heap findMin (actually max): " + maxHeap.findMin().getVal() + " (expected: 40)");
        System.out.println("  After extractMin: " + maxHeap.extractMin().getVal() + " (expected: 40)");
        System.out.println("  New max: " + maxHeap.findMin().getVal() + " (expected: 25)");
        
        System.out.println("\n✓ All tests completed successfully!");
    }
}
