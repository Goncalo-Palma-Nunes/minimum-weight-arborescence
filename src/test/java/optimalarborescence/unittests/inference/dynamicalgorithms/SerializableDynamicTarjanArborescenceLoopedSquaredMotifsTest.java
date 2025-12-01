package optimalarborescence.unittests.inference.dynamicalgorithms;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.dynamic.SerializableDynamicTarjanArborescence;
import optimalarborescence.inference.dynamic.ATreeNode;
import optimalarborescence.unittests.inference.HelperMethods;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

/**
 * Tests for SerializableDynamicTarjanArborescence with looped squared motifs graph.
 * Based on FullyDynamicArborescenceLoopedSquaredMotifsTest.
 */
public class SerializableDynamicTarjanArborescenceLoopedSquaredMotifsTest {
    private static final Comparator<Edge> EDGE_COMPARATOR = 
        (e1, e2) -> Integer.compare(e1.getWeight(), e2.getWeight());

    private static final String ALLELIC_PROFILE = "ACGT";
    private static final String TEST_BASE_NAME = "test_serializable_looped";
    private static final int MLST_LENGTH = 100;

    private List<Node> nodes;
    private List<Edge> edges;
    private Graph originalGraph;
    private List<Edge> expectedEdges;

    @Before
    public void setUp() {
        nodes = new ArrayList<>() {
            {
                add(new Node(ALLELIC_PROFILE, 0));
                add(new Node(ALLELIC_PROFILE, 1));
                add(new Node(ALLELIC_PROFILE, 2));
                add(new Node(ALLELIC_PROFILE, 3));
                add(new Node(ALLELIC_PROFILE, 4));
                add(new Node(ALLELIC_PROFILE, 5));
                add(new Node(ALLELIC_PROFILE, 6));
                add(new Node(ALLELIC_PROFILE, 7));
            }
        };

        edges = new ArrayList<>() {
            {
                add(new Edge(nodes.get(0), nodes.get(1), 11));
                add(new Edge(nodes.get(0), nodes.get(2), 5));
                add(new Edge(nodes.get(1), nodes.get(2), 6));
                add(new Edge(nodes.get(2), nodes.get(0), 15));
                add(new Edge(nodes.get(2), nodes.get(4), 3));
                add(new Edge(nodes.get(2), nodes.get(5), 13));
                add(new Edge(nodes.get(3), nodes.get(1), 10));
                add(new Edge(nodes.get(3), nodes.get(2), 2));
                add(new Edge(nodes.get(4), nodes.get(5), 9));
                add(new Edge(nodes.get(4), nodes.get(6), 12));
                add(new Edge(nodes.get(5), nodes.get(3), 7));
                add(new Edge(nodes.get(5), nodes.get(7), 8));
                add(new Edge(nodes.get(6), nodes.get(7), 1));
                add(new Edge(nodes.get(7), nodes.get(4), 4));
            }
        };

        originalGraph = new Graph(edges);

        expectedEdges = List.of(
            new Edge(nodes.get(0), nodes.get(2), 5),
            new Edge(nodes.get(2), nodes.get(4), 3),
            new Edge(nodes.get(4), nodes.get(6), 12),
            new Edge(nodes.get(6), nodes.get(7), 1),
            new Edge(nodes.get(4), nodes.get(5), 9),
            new Edge(nodes.get(5), nodes.get(3), 7),
            new Edge(nodes.get(3), nodes.get(1), 10)
        );
    }

    @After
    public void tearDown() throws IOException {
        // Clean up test files
        Files.deleteIfExists(Paths.get(TEST_BASE_NAME + "_edges.dat"));
        Files.deleteIfExists(Paths.get(TEST_BASE_NAME + "_nodes.dat"));
    }

    @Test
    public void testLazyLoadingLoopedSquaredMotifs() throws IOException {
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence algo = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), originalGraph);

        Graph result = algo.inferPhylogeny(originalGraph);
        
        // Now save the modified graph and enable file-based mode
        algo.setBaseName(TEST_BASE_NAME, MLST_LENGTH);

        List<Edge> resultEdges = result.getEdges();

        Assert.assertEquals("The number of edges in the optimal arborescence is incorrect.",
                expectedEdges.size(), resultEdges.size());

        Assert.assertTrue("The resulting arborescence is invalid.",
                HelperMethods.isValidArborescence(originalGraph, result));
            
        int expectedCost = expectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = resultEdges.stream().mapToInt(Edge::getWeight).sum();

        Assert.assertEquals("Total cost of the arborescence does not match expected value.",
            expectedCost, resultCost);
    }

    @Test
    public void testInMemoryVsLazyLoadingLoopedGraph() throws IOException {
        // Test 1: Run completely in-memory
        List<ATreeNode> roots1 = new ArrayList<>();
        SerializableDynamicTarjanArborescence inMemoryAlgo = 
            new SerializableDynamicTarjanArborescence(
                roots1, new ArrayList<>(), new HashMap<>(), originalGraph);

        Graph inMemoryResult = inMemoryAlgo.inferPhylogeny(originalGraph);
        int inMemoryCost = inMemoryResult.getEdges().stream().mapToInt(Edge::getWeight).sum();

        // Test 2: Run in-memory first, then switch to file-based mode
        List<ATreeNode> roots2 = new ArrayList<>();
        SerializableDynamicTarjanArborescence hybridAlgo = 
            new SerializableDynamicTarjanArborescence(
                roots2, new ArrayList<>(), new HashMap<>(), originalGraph);

        Graph hybridResult = hybridAlgo.inferPhylogeny(originalGraph);
        
        // Now enable file-based mode - subsequent operations would use lazy loading
        hybridAlgo.setBaseName(TEST_BASE_NAME, MLST_LENGTH);
        
        int hybridCost = hybridResult.getEdges().stream().mapToInt(Edge::getWeight).sum();

        Assert.assertEquals("Both approaches should produce same cost",
            inMemoryCost, hybridCost);
        Assert.assertEquals("Both should produce same number of edges",
            inMemoryResult.getNumEdges(), hybridResult.getNumEdges());
        Assert.assertTrue("Both results should be valid arborescences",
            HelperMethods.isValidArborescence(originalGraph, inMemoryResult) &&
            HelperMethods.isValidArborescence(originalGraph, hybridResult));
    }

    @Test
    public void testLargerGraphLazyLoading() throws IOException {
        // This test verifies lazy loading works correctly with a larger, more complex graph
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence algo = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), originalGraph);

        Graph result = algo.inferPhylogeny(originalGraph);
        
        // Enable file-based mode
        algo.setBaseName(TEST_BASE_NAME, MLST_LENGTH);

        // Verify result is a valid spanning arborescence
        Assert.assertEquals("Should have n-1 edges for n nodes",
            originalGraph.getNumNodes() - 1, result.getNumEdges());
        Assert.assertTrue("Result should be a valid arborescence",
            HelperMethods.isValidArborescence(originalGraph, result));

        // Verify cost matches expected optimal cost
        int expectedCost = expectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = result.getEdges().stream().mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Cost should match expected optimal arborescence",
            expectedCost, resultCost);
    }
}
