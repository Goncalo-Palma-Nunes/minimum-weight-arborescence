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
 * Tests for lazy ATree loading functionality.
 * Demonstrates Option 4: Lazy ATree loading for consecutive dynamic operations.
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
    public void testLazyATreeLoadingAfterSave() throws IOException {
        // Step 1: Run algorithm in-memory and save state
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence camerini = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), originalGraph);
        SerializableFullyDynamicArborescence algo = 
            new SerializableFullyDynamicArborescence(originalGraph, roots, camerini);

        algo.inferPhylogeny(originalGraph);
        System.out.println("After inferPhylogeny, roots size: " + camerini.getATreeRoots().size());
        
        // Enable file-based mode - this saves ATrees AND modified graph
        camerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH);
        System.out.println("After setBaseName, roots size: " + camerini.getATreeRoots().size());
        
        // Verify ATree file was created
        Assert.assertTrue("ATree file should exist", 
            Files.exists(Paths.get(TEST_BASE_NAME + "_atree.dat")));
        
        // Step 2: Load with lazy ATree loading
        SerializableDynamicTarjanArborescence lazyAlgo = 
            new SerializableDynamicTarjanArborescence(TEST_BASE_NAME, MLST_LENGTH, originalGraph);
        
        List<ATreeNode> lazyRoots = lazyAlgo.getATreeRoots();
        System.out.println("After loading, lazyRoots size: " + (lazyRoots != null ? lazyRoots.size() : "null"));
        
        // Verify roots are loaded
        Assert.assertNotNull("Roots should be loaded", lazyRoots);
        Assert.assertTrue("Should have at least one root", lazyRoots.size() > 0);
        
        // Verify roots are configured for lazy loading
        for (ATreeNode root : lazyRoots) {
            Assert.assertTrue("Root should be lazy-loadable", root.isLazyLoadable());
            Assert.assertFalse("Children should not be loaded yet", root.areChildrenLoaded());
        }
    }

    @Test
    public void testLazyChildrenLoading() throws IOException {
        // Step 1: Create and save state
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence camerini = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), originalGraph);
        SerializableFullyDynamicArborescence algo = 
            new SerializableFullyDynamicArborescence(originalGraph, roots, camerini);

        algo.inferPhylogeny(originalGraph);
        camerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH);
        
        // Step 2: Load lazily
        SerializableDynamicTarjanArborescence lazyAlgo = 
            new SerializableDynamicTarjanArborescence(TEST_BASE_NAME, MLST_LENGTH, originalGraph);
        
        List<ATreeNode> lazyRoots = lazyAlgo.getATreeRoots();
        
        // Find a root with children
        ATreeNode rootWithChildren = null;
        for (ATreeNode root : lazyRoots) {
            if (!root.areChildrenLoaded()) {
                rootWithChildren = root;
                break;
            }
        }
        
        if (rootWithChildren != null) {
            // Access children - should trigger lazy loading
            rootWithChildren.getATreeChildren(); // Triggers lazy loading
            
            // Verify children are now loaded
            Assert.assertTrue("Children should be loaded after access", 
                rootWithChildren.areChildrenLoaded());
        }
    }

    @Test
    public void testDynamicOperationsWithLazyATrees() throws IOException {
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
        
        // Step 2: Perform dynamic operation (this will trigger lazy loading of affected ATrees)
        List<Edge> resultAfterDelete = dynamic.removeEdge(edges.get(5)); // Remove 3->2
        int costAfterDelete = resultAfterDelete.stream().mapToInt(Edge::getWeight).sum();
        
        // Verify operation succeeded
        Assert.assertNotEquals("Cost should change after deletion", initialCost, costAfterDelete);
        Assert.assertEquals("Should still have 3 edges", 3, resultAfterDelete.size());
    }

    @Test
    public void testInMemoryVsLazyATreeConsistency() throws IOException {
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

        // Test 2: With lazy ATree loading
        List<ATreeNode> roots2 = new ArrayList<>();
        SerializableDynamicTarjanArborescence hybridCamerini = 
            new SerializableDynamicTarjanArborescence(
                roots2, new ArrayList<>(), new HashMap<>(), originalGraph);
        SerializableFullyDynamicArborescence hybridDynamic = 
            new SerializableFullyDynamicArborescence(originalGraph, roots2, hybridCamerini);

        hybridDynamic.inferPhylogeny(originalGraph);
        hybridCamerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH); // Enable lazy loading
        hybridDynamic.removeEdge(edges.get(5)); // Remove 3->2
        int hybridCost = hybridDynamic.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();

        Assert.assertEquals("Both approaches should produce same cost", inMemoryCost, hybridCost);
    }
}
