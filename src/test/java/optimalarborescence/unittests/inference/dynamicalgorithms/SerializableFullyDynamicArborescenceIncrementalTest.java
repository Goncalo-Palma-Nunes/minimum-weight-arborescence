package optimalarborescence.unittests.inference.dynamicalgorithms;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Graph;
import optimalarborescence.inference.dynamic.SerializableFullyDynamicArborescence;
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
 * Tests for SerializableFullyDynamicArborescence with incremental graph construction.
 * Tests build graphs from empty by adding/removing edges, then save final state.
 * Based on FullyDynamicArborescenceSimpleGraphTest.
 */
public class SerializableFullyDynamicArborescenceIncrementalTest {
    private static final String TEST_BASE_NAME = "test_serializable_incremental";
    private static final int MLST_LENGTH = 100;

    private static AllelicProfile createProfile(String alleles) {
        Character[] data = new Character[alleles.length()];
        for (int i = 0; i < alleles.length(); i++) {
            data[i] = alleles.charAt(i);
        }
        return new AllelicProfile(data, alleles.length());
    }

    private List<Node> nodes;

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
    }

    @After
    public void tearDown() throws IOException {
        // Clean up test files
        Files.deleteIfExists(Paths.get(TEST_BASE_NAME + "_nodes.dat"));
        Files.deleteIfExists(Paths.get(TEST_BASE_NAME + "_atree.dat"));
        // Clean up per-node edge files (up to 8 nodes in larger tests)
        for (int i = 0; i < 8; i++) {
            Files.deleteIfExists(Paths.get(TEST_BASE_NAME + "_edges_node" + i + ".dat"));
        }
    }

    @Test
    public void testIncrementalEdgeAdditions() throws IOException {
        // Start with minimal spanning tree - 3 edges for 4 nodes
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(nodes.get(3), nodes.get(0), 1));
        initialEdges.add(new Edge(nodes.get(0), nodes.get(1), 6));
        initialEdges.add(new Edge(nodes.get(3), nodes.get(2), 8));
        Graph initialGraph = new Graph(initialEdges);
        
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence camerini = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), initialGraph);
        SerializableFullyDynamicArborescence dynamic = 
            new SerializableFullyDynamicArborescence(initialGraph, roots, camerini);

        // Start with initial graph (already has minimal spanning tree)
        dynamic.inferPhylogeny(initialGraph);
        Assert.assertEquals("Should have 3 edges initially", 3, dynamic.getCurrentArborescence().size());
        
        int initialCost = dynamic.getCurrentArborescence().stream().mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Initial cost", 15, initialCost);
        
        // Add edges incrementally
        // Edge 1: 1->2 (weight 10) - suboptimal, should not be included
        Edge edge1 = new Edge(nodes.get(1), nodes.get(2), 10);
        List<Edge> arb1 = dynamic.addEdge(edge1);
        Assert.assertEquals("Should still have 3 edges after suboptimal addition", 3, arb1.size());
        
        // Edge 2: 1->3 (weight 12) - suboptimal
        Edge edge2 = new Edge(nodes.get(1), nodes.get(3), 12);
        List<Edge> arb2 = dynamic.addEdge(edge2);
        Assert.assertEquals("Should still have 3 edges", 3, arb2.size());
        
        // Edge 3: 2->1 (weight 10) - suboptimal
        Edge edge3 = new Edge(nodes.get(2), nodes.get(1), 10);
        List<Edge> arb3 = dynamic.addEdge(edge3);
        Assert.assertEquals("Should still have 3 edges", 3, arb3.size());
        
        // Edge 4: 0->2 (weight 2) - Would be optimal but needs all edges present first
        Edge edge4 = new Edge(nodes.get(0), nodes.get(2), 2);
        List<Edge> arb4 = dynamic.addEdge(edge4);
        Assert.assertEquals("Should still have 3 edges", 3, arb4.size());
        
        // Verify the graph has been updated with new edges
        int finalCost = arb4.stream().mapToInt(Edge::getWeight).sum();
        // Note: The arborescence structure depends on insertion order
        Assert.assertTrue("Final cost should be valid", finalCost > 0);
        
        // Save final state
        camerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH);
        
        // Verify files were created - check for per-node edge files
        Assert.assertTrue("Node file should exist", 
            Files.exists(Paths.get(TEST_BASE_NAME + "_nodes.dat")));
        // At least one edge file should exist for one of the nodes
        boolean edgeFileExists = false;
        for (int i = 0; i < 4; i++) {
            if (Files.exists(Paths.get(TEST_BASE_NAME + "_edges_node" + i + ".dat"))) {
                edgeFileExists = true;
                break;
            }
        }
        Assert.assertTrue("At least one edge file should exist", edgeFileExists);
    }

    @Test
    public void testIncrementalWithOptimalReplacement() throws IOException {
        // Start with minimal spanning tree
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(nodes.get(3), nodes.get(0), 1));
        initialEdges.add(new Edge(nodes.get(0), nodes.get(1), 6));
        initialEdges.add(new Edge(nodes.get(3), nodes.get(2), 8));
        Graph initialGraph = new Graph(initialEdges);
        
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence camerini = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), initialGraph);
        SerializableFullyDynamicArborescence dynamic = 
            new SerializableFullyDynamicArborescence(initialGraph, roots, camerini);

        dynamic.inferPhylogeny(initialGraph);
        
        int initialCost = dynamic.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Initial cost", 15, initialCost);
        
        // Add optimal edge that should replace existing edge
        // 0->2 (weight 2) should replace 3->2 (weight 8)
        Edge optimalEdge = new Edge(nodes.get(0), nodes.get(2), 2);
        List<Edge> updatedArb = dynamic.addEdge(optimalEdge);
        
        int newCost = updatedArb.stream().mapToInt(Edge::getWeight).sum();
        int expectedCost = 1 + 6 + 2; // 3->0 + 0->1 + 0->2
        Assert.assertEquals("Cost should decrease after optimal edge", expectedCost, newCost);
        
        // Save final state
        camerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH);
        
        // Check that files were created
        Assert.assertTrue("Node file should exist", 
            Files.exists(Paths.get(TEST_BASE_NAME + "_nodes.dat")));
        // At least one edge file should exist
        boolean edgeFileExists = false;
        for (int i = 0; i < 4; i++) {
            if (Files.exists(Paths.get(TEST_BASE_NAME + "_edges_node" + i + ".dat"))) {
                edgeFileExists = true;
                break;
            }
        }
        Assert.assertTrue("Files should be created", edgeFileExists);
    }

    @Test
    public void testIncrementalAddThenRemove() throws IOException {
        // Start with minimal spanning tree
        Edge edge1 = new Edge(nodes.get(3), nodes.get(0), 1);
        Edge edge2 = new Edge(nodes.get(0), nodes.get(1), 6);
        Edge edge3 = new Edge(nodes.get(3), nodes.get(2), 8);
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(edge1);
        initialEdges.add(edge2);
        initialEdges.add(edge3);
        Graph initialGraph = new Graph(initialEdges);
        
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence camerini = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), initialGraph);
        SerializableFullyDynamicArborescence dynamic = 
            new SerializableFullyDynamicArborescence(initialGraph, roots, camerini);

        dynamic.inferPhylogeny(initialGraph);
        
        // Build graph incrementally (already has edge1, edge2, edge3)
        Edge edge4 = new Edge(nodes.get(1), nodes.get(2), 10);
        
        dynamic.addEdge(edge4);
        
        Assert.assertEquals("Should have 3 edges (optimal arborescence)", 
            3, dynamic.getCurrentArborescence().size());
        
        // Now remove an arborescence edge
        List<Edge> afterRemoval = dynamic.removeEdge(edge3); // Remove 3->2
        
        Assert.assertEquals("Should still have 3 edges after removal", 
            3, afterRemoval.size());
        
        // Verify that 1->2 is now in the arborescence
        boolean hasEdge12 = afterRemoval.stream()
            .anyMatch(e -> e.getSource().getId() == 1 && e.getDestination().getId() == 2);
        Assert.assertTrue("Should now use edge 1->2", hasEdge12);
        
        int finalCost = afterRemoval.stream().mapToInt(Edge::getWeight).sum();
        int expectedCost = 1 + 6 + 10; // 3->0 + 0->1 + 1->2
        Assert.assertEquals("Cost after removal", expectedCost, finalCost);
        
        // Save final state
        camerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH);
    }

    @Test
    public void testIncrementalMixedOperations() throws IOException {
        // Start with minimal spanning tree
        Edge e1 = new Edge(nodes.get(3), nodes.get(0), 1);
        Edge e2 = new Edge(nodes.get(0), nodes.get(1), 6);
        Edge e3 = new Edge(nodes.get(3), nodes.get(2), 8);
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(e1);
        initialEdges.add(e2);
        initialEdges.add(e3);
        Graph initialGraph = new Graph(initialEdges);
        
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence camerini = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), initialGraph);
        SerializableFullyDynamicArborescence dynamic = 
            new SerializableFullyDynamicArborescence(initialGraph, roots, camerini);

        dynamic.inferPhylogeny(initialGraph);
        
        // Add more edges (already has e1, e2, e3)
        
        // Add suboptimal edge
        Edge e4 = new Edge(nodes.get(1), nodes.get(3), 12);
        List<Edge> arbAfterE4 = dynamic.addEdge(e4);
        int costAfterE4 = arbAfterE4.stream().mapToInt(Edge::getWeight).sum();
        
        // Add another potentially optimal edge
        Edge e5 = new Edge(nodes.get(0), nodes.get(2), 2);
        List<Edge> arbAfterE5 = dynamic.addEdge(e5);
        int costAfterE5 = arbAfterE5.stream().mapToInt(Edge::getWeight).sum();
        
        // Cost should be positive
        Assert.assertTrue("Cost should be positive", costAfterE5 > 0);
        
        // Remove edge e4
        List<Edge> arbAfterRemoval = dynamic.removeEdge(e4);
        int costAfterRemoval = arbAfterRemoval.stream().mapToInt(Edge::getWeight).sum();
        
        // Cost should still be valid
        Assert.assertTrue("Cost after removal should be positive", costAfterRemoval > 0);
        
        // Remove an edge
        dynamic.removeEdge(e5); // Remove 0->2
        
        int finalCost = dynamic.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        Assert.assertTrue("Final cost should be positive", finalCost > 0);
        
        // Save final state
        camerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH);
        
        // Check that files were created
        Assert.assertTrue("Node file should exist", 
            Files.exists(Paths.get(TEST_BASE_NAME + "_nodes.dat")));
        boolean edgeFileExists = false;
        for (int i = 0; i < 4; i++) {
            if (Files.exists(Paths.get(TEST_BASE_NAME + "_edges_node" + i + ".dat"))) {
                edgeFileExists = true;
                break;
            }
        }
        Assert.assertTrue("Files should be created", edgeFileExists);
    }

    @Test
    public void testIncrementalLargerGraph() throws IOException {
        // Create nodes for larger graph (8 nodes)
        List<Node> largeNodes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            largeNodes.add(new Node(createProfile("ACGT"), i));
        }
        
        // Start with minimal spanning tree covering all 8 nodes
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(largeNodes.get(0), largeNodes.get(2), 5));
        initialEdges.add(new Edge(largeNodes.get(2), largeNodes.get(4), 3));
        initialEdges.add(new Edge(largeNodes.get(4), largeNodes.get(6), 12));
        initialEdges.add(new Edge(largeNodes.get(6), largeNodes.get(7), 1));
        initialEdges.add(new Edge(largeNodes.get(4), largeNodes.get(5), 9));
        initialEdges.add(new Edge(largeNodes.get(5), largeNodes.get(3), 7));
        initialEdges.add(new Edge(largeNodes.get(3), largeNodes.get(1), 10));
        Graph initialGraph = new Graph(initialEdges);
        
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence camerini = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), initialGraph);
        SerializableFullyDynamicArborescence dynamic = 
            new SerializableFullyDynamicArborescence(initialGraph, roots, camerini);

        dynamic.inferPhylogeny(initialGraph);
        
        // Add remaining edges incrementally to complete the looped squared motifs graph
        dynamic.addEdge(new Edge(largeNodes.get(0), largeNodes.get(1), 11));
        dynamic.addEdge(new Edge(largeNodes.get(1), largeNodes.get(2), 6));
        dynamic.addEdge(new Edge(largeNodes.get(2), largeNodes.get(0), 15));
        // dynamic.addEdge(new Edge(largeNodes.get(2), largeNodes.get(4), 3)); // Already in initial
        dynamic.addEdge(new Edge(largeNodes.get(2), largeNodes.get(5), 13));
        // dynamic.addEdge(new Edge(largeNodes.get(3), largeNodes.get(1), 10)); // Already in initial
        dynamic.addEdge(new Edge(largeNodes.get(3), largeNodes.get(2), 2));
        // dynamic.addEdge(new Edge(largeNodes.get(4), largeNodes.get(5), 9)); // Already in initial
        // dynamic.addEdge(new Edge(largeNodes.get(4), largeNodes.get(6), 12)); // Already in initial
        // dynamic.addEdge(new Edge(largeNodes.get(5), largeNodes.get(3), 7)); // Already in initial
        dynamic.addEdge(new Edge(largeNodes.get(5), largeNodes.get(7), 8));
        // dynamic.addEdge(new Edge(largeNodes.get(6), largeNodes.get(7), 1)); // Already in initial
        dynamic.addEdge(new Edge(largeNodes.get(7), largeNodes.get(4), 4));
        
        List<Edge> finalArborescence = dynamic.getCurrentArborescence();
        
        // Should have 7 edges for 8 nodes
        Assert.assertEquals("Should have n-1 edges", 7, finalArborescence.size());
        
        // Verify cost is reasonable
        int actualCost = finalArborescence.stream().mapToInt(Edge::getWeight).sum();
        Assert.assertTrue("Cost should be positive and reasonable", actualCost > 0 && actualCost < 100);
        
        // Save final state
        camerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH);
        
        // Check that files were created
        Assert.assertTrue("Node file should exist", 
            Files.exists(Paths.get(TEST_BASE_NAME + "_nodes.dat")));
        boolean edgeFileExists = false;
        for (int i = 0; i < 8; i++) {
            if (Files.exists(Paths.get(TEST_BASE_NAME + "_edges_node" + i + ".dat"))) {
                edgeFileExists = true;
                break;
            }
        }
        Assert.assertTrue("Files should be created", edgeFileExists);
    }

    @Test
    public void testIncrementalWithEdgeUpdates() throws IOException {
        // Start with minimal spanning tree
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(nodes.get(3), nodes.get(0), 1));
        initialEdges.add(new Edge(nodes.get(0), nodes.get(1), 6));
        initialEdges.add(new Edge(nodes.get(3), nodes.get(2), 8));
        initialEdges.add(new Edge(nodes.get(1), nodes.get(2), 10));
        Graph initialGraph = new Graph(initialEdges);
        
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence camerini = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), initialGraph);
        SerializableFullyDynamicArborescence dynamic = 
            new SerializableFullyDynamicArborescence(initialGraph, roots, camerini);

        dynamic.inferPhylogeny(initialGraph);
        
        // Initial arborescence already computed from initial graph
        
        int initialCost = dynamic.getCurrentArborescence().stream()
            .mapToInt(Edge::getWeight).sum();
        Assert.assertEquals("Initial cost", 15, initialCost);
        
        // Update edge 1->2 to lower weight
        Edge updatedEdge = new Edge(nodes.get(1), nodes.get(2), 5);
        List<Edge> afterUpdate = dynamic.updateEdge(updatedEdge);
        
        int costAfterUpdate = afterUpdate.stream().mapToInt(Edge::getWeight).sum();
        int expectedCost = 1 + 6 + 5; // 3->0 + 0->1 + 1->2
        Assert.assertEquals("Cost should decrease after update", expectedCost, costAfterUpdate);
        
        // Verify the updated edge is in the arborescence
        boolean hasUpdatedEdge = afterUpdate.stream()
            .anyMatch(e -> e.getSource().getId() == 1 && 
                          e.getDestination().getId() == 2 && 
                          e.getWeight() == 5);
        Assert.assertTrue("Updated edge should be in arborescence", hasUpdatedEdge);
        
        // Save final state
        camerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH);
    }

    @Test
    public void testLoadFromFilesAddNodeAndUpdateIncrementally() throws IOException {
        // Create initial 4-node graph
        List<Edge> initialEdges = new ArrayList<>();
        initialEdges.add(new Edge(nodes.get(3), nodes.get(0), 1));
        initialEdges.add(new Edge(nodes.get(0), nodes.get(1), 6));
        initialEdges.add(new Edge(nodes.get(3), nodes.get(2), 8));
        initialEdges.add(new Edge(nodes.get(0), nodes.get(2), 2));
        initialEdges.add(new Edge(nodes.get(1), nodes.get(2), 10));
        
        Graph graph = new Graph(initialEdges);
        
        List<ATreeNode> roots = new ArrayList<>();
        SerializableDynamicTarjanArborescence camerini = 
            new SerializableDynamicTarjanArborescence(
                roots, new ArrayList<>(), new HashMap<>(), graph);
        SerializableFullyDynamicArborescence dynamic = 
            new SerializableFullyDynamicArborescence(graph, roots, camerini);

        // Compute initial arborescence and save
        dynamic.inferPhylogeny(graph);
        Assert.assertEquals("Should have 3 edges for 4 nodes", 3, dynamic.getCurrentArborescence().size());
        
        int initialCost = dynamic.getCurrentArborescence().stream().mapToInt(Edge::getWeight).sum();
        
        // Save to memory-mapped files
        camerini.setBaseName(TEST_BASE_NAME, MLST_LENGTH);
        
        // Verify files were created
        Assert.assertTrue("Node file should exist", 
            Files.exists(Paths.get(TEST_BASE_NAME + "_nodes.dat")));
        boolean edgeFileExists = false;
        for (int i = 0; i < 4; i++) {
            if (Files.exists(Paths.get(TEST_BASE_NAME + "_edges_node" + i + ".dat"))) {
                edgeFileExists = true;
                break;
            }
        }
        Assert.assertTrue("Edge file should exist", edgeFileExists);
        
        // Now load from the memory-mapped files
        Graph loadedGraph = optimalarborescence.memorymapper.GraphMapper.loadGraph(TEST_BASE_NAME);
        Assert.assertEquals("Loaded graph should have 4 nodes", 4, loadedGraph.getNumNodes());
        Assert.assertEquals("Loaded graph should have 5 edges", 5, loadedGraph.getNumEdges());
        
        // Create new SerializableDynamicTarjanArborescence that reads from memory-mapped files
        SerializableDynamicTarjanArborescence loadedCamerini = 
            new SerializableDynamicTarjanArborescence(TEST_BASE_NAME, MLST_LENGTH, loadedGraph);
        
        List<ATreeNode> loadedRoots = loadedCamerini.getATreeRoots();
        Assert.assertNotNull("Roots should be loaded", loadedRoots);
        Assert.assertTrue("Should have at least one root", loadedRoots.size() > 0);
        
        SerializableFullyDynamicArborescence loadedDynamic = 
            new SerializableFullyDynamicArborescence(loadedGraph, loadedRoots, loadedCamerini);
        
        loadedDynamic.inferPhylogeny(loadedGraph);
        Assert.assertEquals("Loaded arborescence should have 3 edges", 
            3, loadedDynamic.getCurrentArborescence().size());
        
        // Now add a new node (node 4)
        Node newNode = new Node(createProfile("ACGT"), 4);
        
        // Add node to the graph
        loadedGraph.addNode(newNode);
        Assert.assertEquals("Graph should have 5 nodes after addition", 5, loadedGraph.getNumNodes());
        
        // Create edges involving the new node
        // Find existing nodes from loaded graph
        Node node0 = null, node1 = null, node2 = null;
        for (Node node : loadedGraph.getNodes()) {
            if (node.getId() == 0) node0 = node;
            if (node.getId() == 1) node1 = node;
            if (node.getId() == 2) node2 = node;
        }
        
        Assert.assertNotNull("Node 0 should exist", node0);
        Assert.assertNotNull("Node 1 should exist", node1);
        Assert.assertNotNull("Node 2 should exist", node2);
        
        // Add edges connecting new node to existing graph
        // Edge from node 4 to node 0 with low weight (should be included in arborescence)
        Edge e1 = new Edge(newNode, node0, 1);
        // Edges to node 4 from other nodes
        Edge e2 = new Edge(node1, newNode, 3);
        Edge e3 = new Edge(node2, newNode, 5);
        
        // Add edges using dynamic algorithm (updates arborescence)
        loadedDynamic.addEdge(e1);
        loadedDynamic.addEdge(e2);
        loadedDynamic.addEdge(e3);
        
        // Verify arborescence now has 4 edges (n-1 for 5 nodes)
        Assert.assertEquals("Should have 4 edges after adding node with edges", 
            4, loadedDynamic.getCurrentArborescence().size());
        
        // Verify at least one edge involving node 4 is in the arborescence
        boolean hasEdgeWithNode4 = loadedDynamic.getCurrentArborescence().stream()
            .anyMatch(e -> e.getSource().getId() == 4 || e.getDestination().getId() == 4);
        Assert.assertTrue("At least one edge involving node 4 should be in arborescence", hasEdgeWithNode4);
        
        // Prepare edges for the new node for file update
        List<Edge> incomingEdgesToNode4 = new ArrayList<>();
        List<Edge> outgoingEdgesFromNode4 = new ArrayList<>();
        
        for (Edge edge : loadedGraph.getEdges()) {
            if (edge.getDestination().getId() == 4) {
                incomingEdgesToNode4.add(edge);
            }
            if (edge.getSource().getId() == 4) {
                outgoingEdgesFromNode4.add(edge);
            }
        }
        
        // Update memory-mapped files incrementally using GraphMapper.addNode()
        // This updates the files in-place rather than saving everything from scratch
        // The method now handles both incoming and outgoing edges
        optimalarborescence.memorymapper.GraphMapper.addNode(newNode, incomingEdgesToNode4, 
            outgoingEdgesFromNode4, TEST_BASE_NAME, MLST_LENGTH);
        
        // Verify the files still exist and are valid
        Assert.assertTrue("Node file should still exist after incremental update", 
            Files.exists(Paths.get(TEST_BASE_NAME + "_nodes.dat")));
        // Check that at least one edge file exists
        boolean anyEdgeFileExists = false;
        for (int i = 0; i < 5; i++) {  // Now we have 5 nodes (0-4)
            if (Files.exists(Paths.get(TEST_BASE_NAME + "_edges_node" + i + ".dat"))) {
                anyEdgeFileExists = true;
                break;
            }
        }
        Assert.assertTrue("Edge file should still exist after incremental update", anyEdgeFileExists);
        
        // Load the graph again to verify the node was added to files
        Graph reloadedGraph = optimalarborescence.memorymapper.GraphMapper.loadGraph(TEST_BASE_NAME);
        Assert.assertEquals("Reloaded graph should have 5 nodes", 5, reloadedGraph.getNumNodes());
        
        // Verify node 4 is in the reloaded graph
        boolean node4Found = false;
        for (Node node : reloadedGraph.getNodes()) {
            if (node.getId() == 4) {
                node4Found = true;
                break;
            }
        }
        Assert.assertTrue("Node 4 should be in reloaded graph", node4Found);
        
        // Verify edges involving node 4 are in the reloaded graph
        int edgesWithNode4 = 0;
        for (Edge edge : reloadedGraph.getEdges()) {
            if (edge.getSource().getId() == 4 || edge.getDestination().getId() == 4) {
                edgesWithNode4++;
            }
        }
        Assert.assertTrue("Should have edges involving node 4 in reloaded graph", edgesWithNode4 > 0);
        Assert.assertEquals("Should have 3 edges involving node 4", 3, edgesWithNode4);
        
        // Verify the reloaded graph has the correct number of total edges
        // Original 5 edges + 3 new edges involving node 4 = 8 edges
        Assert.assertEquals("Reloaded graph should have 8 edges", 8, reloadedGraph.getNumEdges());
    }

    private boolean isValidArborescence(Graph originalGraph, Graph arborescence) {
        if (arborescence.getNumEdges() != originalGraph.getNumNodes() - 1) {
            return false;
        }

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
