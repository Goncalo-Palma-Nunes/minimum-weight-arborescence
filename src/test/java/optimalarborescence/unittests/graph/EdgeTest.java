package optimalarborescence.unittests.graph;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EdgeTest {

    List<String> testStrings = new ArrayList<>(){
        {
            add("AACGT");
            add("AAGCT");
        }
    };

    private Node nodeA;
    private Node nodeB;
    private Edge edge;
    private int weight1 = 10;
    private int weight2 = 20;

    @BeforeEach
    public void setUp() {
        nodeA = new Node(testStrings.get(0), 1);
        nodeB = new Node(testStrings.get(1), 2);
        edge = new Edge(nodeA, nodeB, weight1);
    }

    @AfterEach
    public void tearDown() {
        nodeA = null;
        nodeB = null;
        edge = null;
    }

    @Test
    public void testGetters() {
        assertEquals(nodeA, edge.getSource());
        assertEquals(nodeB, edge.getDestination());
        assertEquals(weight1, edge.getWeight());
    }

    @Test
    public void testSetters() {
        Node nodeC = new Node(testStrings.get(1), 3);
        edge.setSource(nodeC);
        edge.setDestination(nodeA);
        edge.setWeight(weight2);

        assertEquals(nodeC, edge.getSource());
        assertEquals(nodeA, edge.getDestination());
        assertEquals(weight2, edge.getWeight());
    }

    @Test
    public void testGreaterThan() {
        Edge edge2 = new Edge(nodeA, nodeB, weight2);
        assertTrue(edge.compareTo(edge2) < 0);
        assertTrue(edge2.compareTo(edge) > 0);
    }

    @Test
    public void testEqualWeights() {
        Edge edge2 = new Edge(nodeB, nodeA, weight1);
        assertEquals(0, edge.compareTo(edge2));
    }


    @Test
    public void testUnequalEdges() {
        Edge edge2 = new Edge(nodeB, nodeA, weight2);
        assertNotEquals(edge2, edge);
        assertNotEquals(1, edge2);
        assertNotEquals(edge, 1);
        assertNotEquals(null, edge2);
    }

    @Test
    public void testEqualEdges() {
        Edge edge2 = new Edge(nodeA, nodeB, weight1);
        assertEquals(edge2, edge);
        assertEquals(edge, edge);
    }
}