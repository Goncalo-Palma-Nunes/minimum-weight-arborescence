package optimalarborescence.unittests.inference.dynamicalgorithms;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.dynamic.SerializableFullyDynamicArborescence;
import optimalarborescence.inference.dynamic.SerializableDynamicTarjanArborescence;
import optimalarborescence.inference.dynamic.ATreeNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

/**
 * Tests for SerializableFullyDynamicArborescence with edge operations using lazy-loading.
 * Based on FullyDynamicArborescenceDeletionTest.
 */
public class SerializableFullyDynamicArborescenceDeletionTest {
    private static final Comparator<Edge> EDGE_COMPARATOR = 
        (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());

    private static final String ALLELIC_PROFILE = "ACGT";
    private static final String TEST_BASE_NAME = "test_serializable_deletion";
    private static final int MLST_LENGTH = 100;

    private List<Node> nodes;
    private List<Edge> edges;
    private Graph originalGraph;

    @Before
    public void setUp() {
        nodes = new ArrayList<>() {
            {
                add(new Node(ALLELIC_PROFILE, 0));
                add(new Node(ALLELIC_PROFILE, 1));
                add(new Node(ALLELIC_PROFILE, 2));
                add(new Node(ALLELIC_PROFILE, 3));
            }
        };

        edges = new ArrayList<>() {
            {
                add(new Edge(nodes.get(0), nodes.get(1), 6));
                add(new Edge(nodes.get(1), nodes.get(2), 10));
                add(new Edge(nodes.get(1), nodes.get(3), 12));
                add(new Edge(nodes.get(2), nodes.get(1), 10));
                add(new Edge(nodes.get(3), nodes.get(0), 1));
                add(new Edge(nodes.get(3), nodes.get(2), 8));
            }
        };

        originalGraph = new Graph(edges);
    }

    @After
    public void tearDown() throws IOException {
        // Clean up test files
        Files.deleteIfExists(Paths.get(TEST_BASE_NAME + "_edges.dat"));
        Files.deleteIfExists(Paths.get(TEST_BASE_NAME + "_nodes.dat"));
    }

    @Test
    public void testRemoveOptimalEdgeWithLazyLoading() throws IOException {
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence camerini = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), originalGraph);

        SerializableFullyDynamicArborescence dynamicAlgorithm = 
            new SerializableFullyDynamicArborescence(originalGraph, roots, camerini);

        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        // Enable file-based mode after running the algorithm
        camerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH);

        Edge edge = edges.get(5); // 3->2 (weight 8)
        List<Edge> updatedArborescence = dynamicAlgorithm.removeEdge(edge);

        List<Edge> expectedEdges = List.of(
            edges.get(0), // 0->1 (6)
            edges.get(4), // 3->0 (1)
            edges.get(1)  // 1->2 (10)
        );

        int expectedCost = expectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        
        Graph updatedGraph = new Graph(
            originalGraph.getEdges().stream()
                .filter(e -> !e.equals(edge))
                .collect(Collectors.toList())
        );
        
        Assert.assertTrue(isValidArborescence(updatedGraph, new Graph(updatedArborescence)));
        Assert.assertEquals("Total cost does not match expected value after edge removal.",
            expectedCost, resultCost);
    }

    @Test
    public void testRemoveTwoOptimalEdgesWithLazyLoading() throws IOException {
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence camerini = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), originalGraph);

        SerializableFullyDynamicArborescence dynamicAlgorithm = 
            new SerializableFullyDynamicArborescence(originalGraph, roots, camerini);

        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        // Enable file-based mode after running the algorithm
        camerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH);
        
        List<Edge> edgesToRemove = List.of(
            edges.get(5), // 3->2 (8)
            edges.get(4)  // 3->0 (1)
        );
        
        // Remove first edge
        dynamicAlgorithm.removeEdge(edgesToRemove.get(0));
        // Remove second edge
        List<Edge> updatedArborescence = dynamicAlgorithm.removeEdge(edgesToRemove.get(1));

        List<Edge> expectedEdges = List.of(
            edges.get(0), // 0->1 (6)
            edges.get(1), // 1->2 (10)
            edges.get(2)  // 1->3 (12)
        );

        int expectedCost = expectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        
        Graph updatedGraph = new Graph(
            originalGraph.getEdges().stream()
                .filter(e -> !edgesToRemove.contains(e))
                .collect(Collectors.toList())
        );
        
        Assert.assertTrue(isValidArborescence(updatedGraph, new Graph(updatedArborescence)));
        Assert.assertEquals("Total cost does not match expected value after removing two edges.",
            expectedCost, resultCost);
    }

    @Test
    public void testRemoveNonArborescenceEdgeWithLazyLoading() throws IOException {
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence camerini = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), originalGraph);

        SerializableFullyDynamicArborescence dynamicAlgorithm = 
            new SerializableFullyDynamicArborescence(originalGraph, roots, camerini);

        dynamicAlgorithm.inferPhylogeny(originalGraph);
        
        // Enable file-based mode after running the algorithm
        camerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH);
        
        List<Edge> initialArborescence = new ArrayList<>(dynamicAlgorithm.getCurrentArborescence());
        int initialCost = initialArborescence.stream().mapToInt(Edge::getWeight).sum();

        // Remove edge not in arborescence: 1->3 (12)
        Edge nonArborescenceEdge = edges.get(2);
        List<Edge> updatedArborescence = dynamicAlgorithm.removeEdge(nonArborescenceEdge);

        int newCost = updatedArborescence.stream().mapToInt(Edge::getWeight).sum();
        
        Assert.assertEquals("Arborescence should remain unchanged",
            initialArborescence, updatedArborescence);
        Assert.assertEquals("Cost should remain unchanged after removing non-arborescence edge",
            initialCost, newCost);
    }

    @Test
    public void testInMemoryVsLazyLoadingDeletion() throws IOException {
        // Test 1: Completely in-memory approach
        List<ATreeNode> roots1 = new ArrayList<>();
        SerializableDynamicTarjanArborescence inMemoryCamerini = 
            new SerializableDynamicTarjanArborescence(
                roots1, new ArrayList<>(), new HashMap<>(), originalGraph);
        SerializableFullyDynamicArborescence inMemoryAlgo = 
            new SerializableFullyDynamicArborescence(originalGraph, roots1, inMemoryCamerini);
        
        inMemoryAlgo.inferPhylogeny(originalGraph);
        List<Edge> inMemoryResult = inMemoryAlgo.removeEdge(edges.get(5)); // Remove 3->2
        int inMemoryCost = inMemoryResult.stream().mapToInt(Edge::getWeight).sum();

        // Test 2: Run in-memory first, then enable file-based mode for subsequent operations
        List<ATreeNode> roots2 = new ArrayList<>();
        SerializableDynamicTarjanArborescence hybridCamerini = 
            new SerializableDynamicTarjanArborescence(
                roots2, new ArrayList<>(), new HashMap<>(), originalGraph);
        SerializableFullyDynamicArborescence hybridAlgo = 
            new SerializableFullyDynamicArborescence(originalGraph, roots2, hybridCamerini);
        
        hybridAlgo.inferPhylogeny(originalGraph);
        
        // Enable file-based mode after initial solution
        hybridCamerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH);
        
        // Now perform deletion - this could potentially use lazy-loading for queue operations
        List<Edge> hybridResult = hybridAlgo.removeEdge(edges.get(5)); // Remove 3->2
        int hybridCost = hybridResult.stream().mapToInt(Edge::getWeight).sum();

        Assert.assertEquals("Both approaches should produce same cost after deletion",
            inMemoryCost, hybridCost);
        Assert.assertEquals("Both should produce same number of edges",
            inMemoryResult.size(), hybridResult.size());
    }

    private boolean isValidArborescence(Graph originalGraph, Graph arborescence) {
        // Check if number of edges is n-1 where n is number of nodes
        if (arborescence.getNumEdges() != originalGraph.getNumNodes() - 1) {
            return false;
        }

        // Check if all edges in arborescence exist in original graph
        for (Edge edge : arborescence.getEdges()) {
            boolean found = false;
            for (Edge originalEdge : originalGraph.getEdges()) {
                if (edge.getSource().getID() == originalEdge.getSource().getID() &&
                    edge.getDestination().getID() == originalEdge.getDestination().getID()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }

        return true;
    }
}
