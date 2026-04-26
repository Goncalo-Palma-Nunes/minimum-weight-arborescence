package optimalarborescence.unittests.inference.dynamicalgorithms;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.dynamic.SerializableDynamicTarjanArborescence;
import optimalarborescence.inference.dynamic.SerializableFullyDynamicArborescence;
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
 * Tests for ATree save/load round-trip and dynamic operations after reloading from disk.
 */
public class LazyATreeLoadingTest {
    private static final String TEST_BASE_NAME = "test_lazy_atree";
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
                add(new Edge(nodes.get(0), nodes.get(1), 6));  // 0->1
                add(new Edge(nodes.get(1), nodes.get(2), 10)); // 1->2
                add(new Edge(nodes.get(1), nodes.get(3), 12)); // 1->3
                add(new Edge(nodes.get(2), nodes.get(1), 10)); // 2->1
                add(new Edge(nodes.get(3), nodes.get(0), 1));  // 3->0
                add(new Edge(nodes.get(3), nodes.get(2), 8));  // 3->2
            }
        };

        originalGraph = new Graph(edges);
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(Paths.get(TEST_BASE_NAME + "_edges.dat"));
        Files.deleteIfExists(Paths.get(TEST_BASE_NAME + "_nodes.dat"));
        Files.deleteIfExists(Paths.get(TEST_BASE_NAME + "_atree.dat"));
    }

    @Test
    public void testATreeLoadingAfterSave() throws IOException {
        // Step 1: Run algorithm in-memory and save state
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence camerini =
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), originalGraph);
        SerializableFullyDynamicArborescence algo =
            new SerializableFullyDynamicArborescence(originalGraph, roots, camerini);

        algo.inferPhylogeny(originalGraph);

        // Enable file-based mode - this saves ATrees AND modified graph
        camerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH);

        // Verify ATree file was created
        Assert.assertTrue("ATree file should exist",
            Files.exists(Paths.get(TEST_BASE_NAME + "_atree.dat")));

        // Step 2: Load from disk
        SerializableDynamicTarjanArborescence loadedAlgo =
            new SerializableDynamicTarjanArborescence(TEST_BASE_NAME, MLST_LENGTH, originalGraph);

        List<ATreeNode> loadedRoots = loadedAlgo.getATreeRoots();

        // Verify roots are loaded
        Assert.assertNotNull("Roots should be loaded", loadedRoots);
        Assert.assertTrue("Should have at least one root", loadedRoots.size() > 0);

        // Verify roots loaded eagerly with their full subtree
        for (ATreeNode root : loadedRoots) {
            Assert.assertNotNull("Root should be a valid node", root);
            if (!root.isSimpleNode()) {
                Assert.assertFalse("C-node root should have children loaded",
                    root.getATreeChildren().isEmpty());
            }
        }
    }

    @Test
    public void testEagerChildrenLoading() throws IOException {
        // Step 1: Create and save state
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence camerini =
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), originalGraph);
        SerializableFullyDynamicArborescence algo =
            new SerializableFullyDynamicArborescence(originalGraph, roots, camerini);

        algo.inferPhylogeny(originalGraph);
        camerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH);

        // Step 2: Load from disk
        SerializableDynamicTarjanArborescence loadedAlgo =
            new SerializableDynamicTarjanArborescence(TEST_BASE_NAME, MLST_LENGTH, originalGraph);

        List<ATreeNode> loadedRoots = loadedAlgo.getATreeRoots();

        // All children should already be available after eager loading
        for (ATreeNode root : loadedRoots) {
            List<ATreeNode> children = root.getATreeChildren();
            Assert.assertNotNull("Children list should not be null", children);
        }
    }

    @Test
    public void testDynamicOperationsWithReloadedATrees() throws IOException {
        // Step 1: Create initial solution and save
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence camerini =
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), originalGraph);
        SerializableFullyDynamicArborescence dynamic =
            new SerializableFullyDynamicArborescence(originalGraph, roots, camerini);

        dynamic.inferPhylogeny(originalGraph);
        camerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH);

        int initialCost = dynamic.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();

        // Step 2: Perform dynamic operation (this will use reloaded ATrees)
        List<Edge> resultAfterDelete = dynamic.removeEdge(edges.get(5)); // Remove 3->2
        int costAfterDelete = resultAfterDelete.stream().mapToInt(Edge::getWeight).sum();

        // Verify operation succeeded
        Assert.assertNotEquals("Cost should change after deletion", initialCost, costAfterDelete);
        Assert.assertEquals("Should still have 3 edges", 3, resultAfterDelete.size());
    }

    @Test
    public void testInMemoryVsReloadedATreeConsistency() throws IOException {
        // Test 1: Completely in-memory
        List<ATreeNode> roots1 = new ArrayList<>();
        SerializableDynamicTarjanArborescence inMemoryCamerini =
            new SerializableDynamicTarjanArborescence(
                roots1, new ArrayList<>(), new HashMap<>(), originalGraph);
        SerializableFullyDynamicArborescence inMemoryDynamic =
            new SerializableFullyDynamicArborescence(originalGraph, roots1, inMemoryCamerini);

        inMemoryDynamic.inferPhylogeny(originalGraph);
        inMemoryDynamic.removeEdge(edges.get(5)); // Remove 3->2
        int inMemoryCost = inMemoryDynamic.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();

        // Test 2: With ATree save/reload
        List<ATreeNode> roots2 = new ArrayList<>();
        SerializableDynamicTarjanArborescence hybridCamerini =
            new SerializableDynamicTarjanArborescence(
                roots2, new ArrayList<>(), new HashMap<>(), originalGraph);
        SerializableFullyDynamicArborescence hybridDynamic =
            new SerializableFullyDynamicArborescence(originalGraph, roots2, hybridCamerini);

        hybridDynamic.inferPhylogeny(originalGraph);
        hybridCamerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH); // Save to disk
        hybridDynamic.removeEdge(edges.get(5)); // Remove 3->2
        int hybridCost = hybridDynamic.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();

        Assert.assertEquals("Both approaches should produce same cost", inMemoryCost, hybridCost);
    }
}
