package optimalarborescence.unittests.inference.dynamicalgorithms;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.dynamic.SerializableDynamicTarjanArborescence;
import optimalarborescence.inference.dynamic.ATreeNode;
import optimalarborescence.sequences.AllelicProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

/**
 * Tests for SerializableDynamicTarjanArborescence with lazy-loading from memory-mapped files.
 * Based on FullyDynamicArborescenceSimpleGraphTest.
 */
public class SerializableDynamicTarjanArborescenceSimpleGraphTest {

    private static final String TEST_BASE_NAME = "test_serializable_simple";
    private static final int MLST_LENGTH = 100;

    private static AllelicProfile createProfile(String alleles) {
        Character[] data = new Character[alleles.length()];
        for (int i = 0; i < alleles.length(); i++) {
            data[i] = alleles.charAt(i);
        }
        return new AllelicProfile(data, alleles.length());
    }

    private List<Node> nodes;
    private List<Edge> edges;
    private Graph originalGraph;

    @Before
    public void setUp() {
        nodes = new ArrayList<>() {
            {
                add(new Node(createProfile("ACGT"), 0));
                add(new Node(createProfile("ACGT"), 1));
                add(new Node(createProfile("ACGT"), 2));
                add(new Node(createProfile("ACGT"), 3));
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
    public void testLazyLoadingInferPhylogeny() throws IOException {
        // Create algorithm and run in-memory first
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence algo = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), originalGraph);

        // Run inference in-memory
        Graph result = algo.inferPhylogeny(originalGraph);
        
        // Now enable file-based mode
        algo.setBaseName(TEST_BASE_NAME, MLST_LENGTH);

        Assert.assertNotNull(result);
        Assert.assertEquals(originalGraph.getNumNodes(), result.getNumNodes());
        Assert.assertEquals(originalGraph.getNumNodes() - 1, result.getNumEdges());

        List<Edge> expectedEdges = List.of(
            new Edge(nodes.get(3), nodes.get(0), 1),
            new Edge(nodes.get(0), nodes.get(1), 6),
            new Edge(nodes.get(3), nodes.get(2), 8)
        );

        int expectedCost = expectedEdges.stream().mapToInt(Edge::getWeight).sum();
        int resultCost = result.getEdges().stream().mapToInt(Edge::getWeight).sum();
        
        Assert.assertTrue(isValidArborescence(originalGraph, result));
        Assert.assertEquals("Total cost of the arborescence does not match expected value.",
            expectedCost, resultCost);
    }

    @Test
    public void testInMemoryVsLazyLoading() throws IOException {
        // Test 1: Completely in-memory
        List<ATreeNode> roots1 = new ArrayList<>();
        SerializableDynamicTarjanArborescence inMemoryAlgo = 
            new SerializableDynamicTarjanArborescence(
                roots1, new ArrayList<>(), new HashMap<>(), originalGraph);

        Graph inMemoryResult = inMemoryAlgo.inferPhylogeny(originalGraph);
        int inMemoryCost = inMemoryResult.getEdges().stream().mapToInt(Edge::getWeight).sum();

        // Test 2: Run in-memory then switch to file-based mode
        List<ATreeNode> roots2 = new ArrayList<>();
        SerializableDynamicTarjanArborescence hybridAlgo = 
            new SerializableDynamicTarjanArborescence(
                roots2, new ArrayList<>(), new HashMap<>(), originalGraph);

        Graph hybridResult = hybridAlgo.inferPhylogeny(originalGraph);
        
        // Enable file-based mode after running the algorithm
        hybridAlgo.setBaseName(TEST_BASE_NAME, MLST_LENGTH);
        
        int hybridCost = hybridResult.getEdges().stream().mapToInt(Edge::getWeight).sum();

        Assert.assertEquals("Both approaches should produce same cost",
            inMemoryCost, hybridCost);
        Assert.assertEquals("Both should produce same number of edges",
            inMemoryResult.getNumEdges(), hybridResult.getNumEdges());
    }

    @Test
    public void testFilesAreCreated() throws IOException {
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence algo = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), originalGraph);
        
        // Run algorithm first
        algo.inferPhylogeny(originalGraph);
        
        // Enable file-based mode - this should create the files
        algo.setBaseName(TEST_BASE_NAME, MLST_LENGTH);

        // Verify files were created
        Assert.assertTrue("Edge file should exist", 
            Files.exists(Paths.get(TEST_BASE_NAME + "_edges.dat")));
        Assert.assertTrue("Node file should exist", 
            Files.exists(Paths.get(TEST_BASE_NAME + "_nodes.dat")));
    }

    @Test
    public void testSetBaseNameEnablesLazyLoading() throws IOException {
        // Create in-memory algorithm
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence algo = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), originalGraph);

        // Enable lazy loading via setBaseName
        algo.setBaseName(TEST_BASE_NAME, MLST_LENGTH);

        // Run inference with lazy loading
        Graph result = algo.inferPhylogeny(originalGraph);

        Assert.assertNotNull(result);
        Assert.assertEquals(originalGraph.getNumNodes() - 1, result.getNumEdges());
        Assert.assertTrue(isValidArborescence(originalGraph, result));
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
                if (edge.getSource().getId() == originalEdge.getSource().getId() &&
                    edge.getDestination().getId() == originalEdge.getDestination().getId()) {
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
