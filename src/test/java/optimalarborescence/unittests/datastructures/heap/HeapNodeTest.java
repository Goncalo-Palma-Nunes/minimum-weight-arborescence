package optimalarborescence.unittests.datastructures.heap;

import optimalarborescence.datastructure.heap.HeapNode;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

public class HeapNodeTest {

    private HeapNode singletonHeapNode;
    private HeapNode nonSingletonHeapNode;
    private Edge testEdge;
    private int initialVal = 10;
    private int decreasedVal = 5;

    @Before
    public void setUp() {
        Node source = new Node("AAGCT", 1);
        Node destination = new Node("AACGT", 2);
        testEdge = new Edge(source, destination, initialVal);

        singletonHeapNode = new HeapNode(testEdge, null, null);
        nonSingletonHeapNode = new HeapNode(testEdge, new HeapNode(testEdge, null, null), null);
    }

    @Test
    public void testSetValOnSingleton() {
        singletonHeapNode.setVal(decreasedVal);
        assertEquals(decreasedVal, singletonHeapNode.getVal());
    }

    @Test
    public void testSetValOnNonSingleton() {
        int originalVal = nonSingletonHeapNode.getVal();
        nonSingletonHeapNode.setVal(decreasedVal);
        assertEquals(originalVal, nonSingletonHeapNode.getVal());
    }

    @Test
    public void testGetVal() {
        assertEquals(initialVal, singletonHeapNode.getVal());
    }

    @Test
    public void testGetEdge() {
        assertEquals(testEdge, singletonHeapNode.getEdge());
    }

}
