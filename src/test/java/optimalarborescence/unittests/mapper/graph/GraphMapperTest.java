package optimalarborescence.unittests.mapper.graph;

import java.io.File;
import java.io.IOException;
import optimalarborescence.graph.Graph;
import optimalarborescence.memorymapper.GraphMapper;
import optimalarborescence.memorymapper.NodeIndexMapper;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GraphMapperTest {
    
    private static final String TEST_BASE_NAME = "test_graph_mapper";
    private static final int MLST_LENGTH = 20;
    private Graph testGraph;
    
    @Before
    public void setUp() {
        testGraph = createTestGraph();
    }
    
    @After
    public void tearDown() {
        // Clean up test files
        deleteTestFiles(TEST_BASE_NAME);
    }
    
    /**
     * Test saveGraph method - saves a graph to memory-mapped files.
     */
    @Test
    public void testSaveGraph() throws IOException {
        // Act
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);
        
        // Assert - verify all three files were created
        File edgeFile = new File(TEST_BASE_NAME + "_edges.dat");
        File nodeFile = new File(TEST_BASE_NAME + "_nodes.dat");
        
        Assert.assertTrue("Edge file should exist", edgeFile.exists());
        Assert.assertTrue("Node file should exist", nodeFile.exists());
        
        // Verify file sizes are reasonable
        Assert.assertTrue("Edge file should not be empty", edgeFile.length() > 0);
        Assert.assertTrue("Node file should not be empty", nodeFile.length() > 0);
        
        // Edge file: 4 bytes (header = num edges) + 6 edges * 28 bytes = 172 bytes
        Assert.assertEquals("Edge file size should be 172 bytes", 172, edgeFile.length());
        
        // Node file: header (8 bytes) + 4 nodes × (20 bytes MLST + 8 bytes offset) = 8 + 4×28 = 120 bytes
        Assert.assertEquals("Node file size should be 120 bytes", 120, nodeFile.length());

    }
    
    /**
     * Test loadGraph method - loads a complete graph from files.
     */
    @Test
    public void testLoadGraph() throws IOException {
        // Arrange
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);
        
        // Act
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        
        // Assert
        Assert.assertNotNull("Loaded graph should not be null", loadedGraph);
        Assert.assertEquals("Should have same number of nodes", 
                          testGraph.getNumNodes(), loadedGraph.getNumNodes());
        Assert.assertEquals("Should have same number of edges", 
                          testGraph.getNumEdges(), loadedGraph.getNumEdges());
        
        // Verify edges are preserved
        List<Edge> originalEdges = testGraph.getEdges();
        List<Edge> loadedEdges = loadedGraph.getEdges();
        
        Assert.assertEquals("Edge counts should match", originalEdges.size(), loadedEdges.size());
        
        // Verify at least one edge has correct data
        boolean foundMatchingEdge = false;
        for (Edge loadedEdge : loadedEdges) {
            if (loadedEdge.getSource().getID() == 0 && 
                loadedEdge.getDestination().getID() == 1 && 
                loadedEdge.getWeight() == 5) {
                foundMatchingEdge = true;
                Assert.assertEquals("Source MLST should match", "ATCGATCGATCGATCGATCG", 
                                  loadedEdge.getSource().getMLSTdata());
                Assert.assertEquals("Destination MLST should match", "GCTAGCTAGCTAGCTAGCTA", 
                                  loadedEdge.getDestination().getMLSTdata());
                break;
            }
        }
        Assert.assertTrue("Should find edge 0->1 with weight 5", foundMatchingEdge);
    }
    
    /**
     * Test save and load cycle preserves all graph data.
     */
    @Test
    public void testSaveLoadCycle() throws IOException {
        // Arrange & Act
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        
        // Assert - verify all edges are preserved with correct data
        Assert.assertEquals("Edge count should match", 6, loadedGraph.getNumEdges());
        Assert.assertEquals("Node count should match", 4, loadedGraph.getNumNodes());
        
        // Create a map of original edges for easy lookup
        Map<String, Edge> originalEdgeMap = new java.util.HashMap<>();
        for (Edge edge : testGraph.getEdges()) {
            String key = edge.getSource().getID() + "->" + edge.getDestination().getID();
            originalEdgeMap.put(key, edge);
        }
        
        // Verify each loaded edge matches an original edge
        for (Edge loadedEdge : loadedGraph.getEdges()) {
            String key = loadedEdge.getSource().getID() + "->" + loadedEdge.getDestination().getID();
            Edge originalEdge = originalEdgeMap.get(key);
            
            Assert.assertNotNull("Should find matching original edge for " + key, originalEdge);
            Assert.assertEquals("Weight should match for " + key, 
                              originalEdge.getWeight(), loadedEdge.getWeight());
            Assert.assertEquals("Source MLST should match for " + key,
                              originalEdge.getSource().getMLSTdata(), 
                              loadedEdge.getSource().getMLSTdata());
            Assert.assertEquals("Destination MLST should match for " + key,
                              originalEdge.getDestination().getMLSTdata(), 
                              loadedEdge.getDestination().getMLSTdata());
        }
    }
    
    /**
     * Test getIncomingEdgeOffset for node with no incoming edges.
     */
    @Test
    public void testGetIncomingEdgeOffsetNoIncoming() throws IOException {
        // Arrange - create a graph with a node that has no incoming edges
        Node n0 = new Node("AAAA", 0);
        Node n1 = new Node("TTTT", 1);
        Node n2 = new Node("GGGG", 2);
        
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(n0, n1, 10));
        edges.add(new Edge(n1, n2, 20));
        // Node 0 has no incoming edges
        
        Graph graph = new Graph(edges);
        String baseName = TEST_BASE_NAME + "_no_incoming";
        
        GraphMapper.saveGraph(graph, MLST_LENGTH, baseName);
        
        // Act
        long offset = GraphMapper.getIncomingEdgeOffset(baseName, 0);
        
        // Assert
        Assert.assertEquals("Node with no incoming edges should have offset -1", -1, offset);
        
        // Clean up
        deleteTestFiles(baseName);
    }
    
    /**
     * Test getIncomingEdges method - retrieves all incoming edges for a node.
     */
    @Test
    public void testGetIncomingEdges() throws IOException {
        // Arrange
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);
        System.out.println("graph edges = " + testGraph.getEdges());


        Map<Integer, Node> nodeMap = NodeIndexMapper.loadNodes(
            TEST_BASE_NAME + "_nodes.dat");
        
        // Act - get incoming edges for node 2 (should have 2 edges)
        List<Edge> incomingEdges = GraphMapper.getIncomingEdges(
            TEST_BASE_NAME, 2, nodeMap);

        System.out.println("incoming edges for node 2: " + incomingEdges);
        // Assert
        Assert.assertNotNull("Incoming edges list should not be null", incomingEdges);
        Assert.assertEquals("Node 2 should have 2 incoming edges", 2, incomingEdges.size());
        
        // Verify the edges are correct
        boolean foundEdge0to2 = false;
        boolean foundEdge1to2 = false;
        
        for (Edge edge : incomingEdges) {
            Assert.assertEquals("All edges should have destination 2", 2, edge.getDestination().getID());
            
            if (edge.getSource().getID() == 0 && edge.getWeight() == 3) {
                foundEdge0to2 = true;
            } else if (edge.getSource().getID() == 1 && edge.getWeight() == 2) {
                foundEdge1to2 = true;
            }
        }
        
        Assert.assertTrue("Should find edge 0->2", foundEdge0to2);
        Assert.assertTrue("Should find edge 1->2", foundEdge1to2);
    }
    
    /**
     * Test getIncomingEdges for node with single incoming edge.
     */
    @Test
    public void testGetIncomingEdgesSingle() throws IOException {
        // Arrange
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);
        Map<Integer, Node> nodeMap = NodeIndexMapper.loadNodes(
            TEST_BASE_NAME + "_nodes.dat");
        
        // Act - get incoming edges for node 1 (should have 1 edge)
        List<Edge> incomingEdges = GraphMapper.getIncomingEdges(
            TEST_BASE_NAME, 1, nodeMap);

        // Assert
        Assert.assertNotNull("Incoming edges list should not be null", incomingEdges);
        Assert.assertEquals("Node 1 should have 1 incoming edge", 1, incomingEdges.size());
        
        Edge edge = incomingEdges.get(0);
        Assert.assertEquals("Source should be node 0", 0, edge.getSource().getID());
        Assert.assertEquals("Destination should be node 1", 1, edge.getDestination().getID());
        Assert.assertEquals("Weight should be 5", 5, edge.getWeight());
    }
    
    /**
     * Test getIncomingEdges for node with no incoming edges.
     */
    @Test
    public void testGetIncomingEdgesNone() throws IOException {
        // Arrange
        Node n0 = new Node("AAAA", 0);
        Node n1 = new Node("TTTT", 1);
        
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(n0, n1, 10));
        // Node 0 has no incoming edges
        
        Graph graph = new Graph(edges);
        String baseName = TEST_BASE_NAME + "_no_incoming2";
        
        GraphMapper.saveGraph(graph, MLST_LENGTH, baseName);
        Map<Integer, Node> nodeMap = NodeIndexMapper.loadNodes(
            baseName + "_nodes.dat");
        
        // Act
        List<Edge> incomingEdges = GraphMapper.getIncomingEdges(
            baseName, 0, nodeMap);
        
        // Assert
        Assert.assertNotNull("Incoming edges list should not be null", incomingEdges);
        Assert.assertEquals("Node 0 should have no incoming edges", 0, incomingEdges.size());
        
        // Clean up
        deleteTestFiles(baseName);
    }
    
    /**
     * Test with empty graph.
     */
    @Test
    public void testEmptyGraph() throws IOException {
        // Arrange
        Graph emptyGraph = new Graph();
        String baseName = TEST_BASE_NAME + "_empty";
        
        // Act
        GraphMapper.saveGraph(emptyGraph, MLST_LENGTH, baseName);
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        
        // Assert
        Assert.assertNotNull("Loaded graph should not be null", loadedGraph);
        Assert.assertEquals("Empty graph should have 0 nodes", 0, loadedGraph.getNumNodes());
        Assert.assertEquals("Empty graph should have 0 edges", 0, loadedGraph.getNumEdges());
        
        // Clean up
        deleteTestFiles(baseName);
    }
    
    /**
     * Test with graph containing single node (no edges).
     */
    @Test
    public void testSingleNodeGraph() throws IOException {
        // Arrange
        Node n0 = new Node("ATCG", 0);
        Graph graph = new Graph();
        graph.addNode(n0);
        
        String baseName = TEST_BASE_NAME + "_single";
        
        // Act
        GraphMapper.saveGraph(graph, MLST_LENGTH, baseName);
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        
        // Assert
        Assert.assertNotNull("Loaded graph should not be null", loadedGraph);
        Assert.assertEquals("Should have 0 edges", 0, loadedGraph.getNumEdges());
        
        // Clean up
        deleteTestFiles(baseName);
    }
    
    /**
     * Test with different MLST lengths.
     */
    @Test
    public void testDifferentMlstLengths() throws IOException {
        int[] mlstLengths = {10, 20, 50, 100};
        
        for (int mlstLength : mlstLengths) {
            String baseName = TEST_BASE_NAME + "_mlst" + mlstLength;
            
            // Act
            GraphMapper.saveGraph(testGraph, mlstLength, baseName);
            Graph loadedGraph = GraphMapper.loadGraph(baseName);
            
            // Assert
            Assert.assertNotNull("Loaded graph should not be null for MLST length " + mlstLength, 
                                loadedGraph);
            Assert.assertEquals("Should have 6 edges for MLST length " + mlstLength, 
                              6, loadedGraph.getNumEdges());
            Assert.assertEquals("Should have 4 nodes for MLST length " + mlstLength, 
                              4, loadedGraph.getNumNodes());
            
            // Clean up
            deleteTestFiles(baseName);
        }
    }
    
    /**
     * Test with graph containing cycle.
     */
    @Test
    public void testGraphWithCycle() throws IOException {
        // Arrange - graph with cycle: 0->1->2->0
        Node n0 = new Node("AAAA", 0);
        Node n1 = new Node("TTTT", 1);
        Node n2 = new Node("GGGG", 2);
        
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(n0, n1, 10));
        edges.add(new Edge(n1, n2, 20));
        edges.add(new Edge(n2, n0, 30));
        
        Graph graph = new Graph(edges);
        String baseName = TEST_BASE_NAME + "_cycle";
        
        // Act
        GraphMapper.saveGraph(graph, MLST_LENGTH, baseName);
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        
        // Assert
        Assert.assertNotNull("Loaded graph should not be null", loadedGraph);
        Assert.assertEquals("Should have 3 edges", 3, loadedGraph.getNumEdges());
        Assert.assertEquals("Should have 3 nodes", 3, loadedGraph.getNumNodes());
        
        // Clean up
        deleteTestFiles(baseName);
    }

    // ===== Tests for addNode method =====

    /**
     * Test adding a new node with no incoming edges to an existing graph.
     */
    @Test
    public void testAddNodeWithNoIncomingEdges() throws IOException {
        // Arrange - save initial graph
        String baseName = TEST_BASE_NAME + "_addnode1";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        // Create new node with no incoming edges
        Node newNode = new Node("AAAAAAAAAAAAAAAAAAAA", 4);
        List<Edge> incomingEdges = new ArrayList<>();
        
        // Act
        GraphMapper.addNode(newNode, incomingEdges, baseName, MLST_LENGTH);
        
        // Assert - reload graph and verify
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 5 nodes (4 original + 1 new)", 
                          5, loadedGraph.getNumNodes());
        Assert.assertEquals("Should have same number of edges as original", 
                          testGraph.getNumEdges(), loadedGraph.getNumEdges());
        
        // Verify the new node exists
        boolean foundNewNode = false;
        for (Node node : loadedGraph.getNodes()) {
            if (node.getID() == 4) {
                foundNewNode = true;
                Assert.assertEquals("New node MLST should match", "AAAAAAAAAAAAAAAAAAAA", node.getMLSTdata());
                break;
            }
        }
        Assert.assertTrue("New node should be found in loaded graph", foundNewNode);
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test adding a new node with single incoming edge.
     */
    @Test
    public void testAddNodeWithSingleIncomingEdge() throws IOException {
        // Arrange - save initial graph
        String baseName = TEST_BASE_NAME + "_addnode2";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        // Create new node with one incoming edge
        Node newNode = new Node("AACCAACCAACCAACCAACC", 4);
        Node existingNode = new Node("ATCGATCGATCGATCGATCG", 0); // Node from original graph
        List<Edge> incomingEdges = new ArrayList<>();
        incomingEdges.add(new Edge(existingNode, newNode, 100));
        
        // Act
        GraphMapper.addNode(newNode, incomingEdges, baseName, MLST_LENGTH);
        
        // Assert - reload graph and verify
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 5 nodes", 5, loadedGraph.getNumNodes());
        Assert.assertEquals("Should have 7 edges (6 original + 1 new)", 
                          7, loadedGraph.getNumEdges());
        
        // Verify the new edge exists
        boolean foundNewEdge = false;
        for (Edge edge : loadedGraph.getEdges()) {
            if (edge.getSource().getID() == 0 && 
                edge.getDestination().getID() == 4 && 
                edge.getWeight() == 100) {
                foundNewEdge = true;
                break;
            }
        }
        Assert.assertTrue("New edge should be found in loaded graph", foundNewEdge);
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test adding a new node with multiple incoming edges.
     */
    @Test
    public void testAddNodeWithMultipleIncomingEdges() throws IOException {
        // Arrange - save initial graph
        String baseName = TEST_BASE_NAME + "_addnode3";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        // Create new node with multiple incoming edges
        Node newNode = new Node("AACCAACCAACCAACCAACC", 4);
        Node node0 = new Node("ATCGATCGATCGATCGATCG", 0);
        Node node1 = new Node("GCTAGCTAGCTAGCTAGCTA", 1);
        Node node2 = new Node("TGACTGACTGACTGACTGAC", 2);
        
        List<Edge> incomingEdges = new ArrayList<>();
        incomingEdges.add(new Edge(node0, newNode, 50));
        incomingEdges.add(new Edge(node1, newNode, 60));
        incomingEdges.add(new Edge(node2, newNode, 70));
        
        // Act
        GraphMapper.addNode(newNode, incomingEdges, baseName, MLST_LENGTH);
        
        // Assert - reload graph and verify
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 5 nodes", 5, loadedGraph.getNumNodes());
        Assert.assertEquals("Should have 9 edges (6 original + 3 new)", 
                          9, loadedGraph.getNumEdges());
        
        // Verify all new edges exist
        int newEdgesFound = 0;
        for (Edge edge : loadedGraph.getEdges()) {
            if (edge.getDestination().getID() == 4) {
                newEdgesFound++;
                Assert.assertTrue("Weight should be 50, 60, or 70", 
                    edge.getWeight() == 50 || edge.getWeight() == 60 || edge.getWeight() == 70);
            }
        }
        Assert.assertEquals("Should find all 3 new edges", 3, newEdgesFound);
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test adding multiple nodes sequentially.
     */
    @Test
    public void testAddMultipleNodesSequentially() throws IOException {
        // Arrange - save initial graph
        String baseName = TEST_BASE_NAME + "_addnode4";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        // Add first new node
        Node newNode1 = new Node("AAATAAATAAATAAATAAAT", 4);
        List<Edge> edges1 = new ArrayList<>();
        edges1.add(new Edge(new Node("ATCGATCGATCGATCGATCG", 0), newNode1, 10));
        GraphMapper.addNode(newNode1, edges1, baseName, MLST_LENGTH);
        
        // Add second new node
        Node newNode2 = new Node("AAAGAAAGAAAGAAAGAAAG", 5);
        List<Edge> edges2 = new ArrayList<>();
        edges2.add(new Edge(new Node("GCTAGCTAGCTAGCTAGCTA", 1), newNode2, 20));
        GraphMapper.addNode(newNode2, edges2, baseName, MLST_LENGTH);
        
        // Add third new node
        Node newNode3 = new Node("AAACAAACAAACAAACAAAC", 6);
        List<Edge> edges3 = new ArrayList<>();
        edges3.add(new Edge(newNode1, newNode3, 30)); // Edge from first new node
        GraphMapper.addNode(newNode3, edges3, baseName, MLST_LENGTH);
        
        // Assert - reload graph and verify
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 7 nodes (4 original + 3 new)", 
                          7, loadedGraph.getNumNodes());
        Assert.assertEquals("Should have 9 edges (6 original + 3 new)", 
                          9, loadedGraph.getNumEdges());
        
        // Verify all new nodes exist
        int newNodesFound = 0;
        for (Node node : loadedGraph.getNodes()) {
            if (node.getID() >= 4 && node.getID() <= 6) {
                newNodesFound++;
            }
        }
        Assert.assertEquals("Should find all 3 new nodes", 3, newNodesFound);
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test adding a node with edges of various weights.
     */
    @Test
    public void testAddNodeWithVariousEdgeWeights() throws IOException {
        // Arrange - save initial graph
        String baseName = TEST_BASE_NAME + "_addnode5";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        // Create new node with edges of various weights
        Node newNode = new Node("AAAAAAAAAAAAAAAAAAAA", 4);
        List<Edge> incomingEdges = new ArrayList<>();
        incomingEdges.add(new Edge(new Node("ATCGATCGATCGATCGATCG", 0), newNode, 0));
        incomingEdges.add(new Edge(new Node("GCTAGCTAGCTAGCTAGCTA", 1), newNode, Integer.MAX_VALUE));
        incomingEdges.add(new Edge(new Node("TGACTGACTGACTGACTGAC", 2), newNode, Integer.MIN_VALUE));
        incomingEdges.add(new Edge(new Node("CGATCGATCGATCGATCGAT", 3), newNode, -999));
        
        // Act
        GraphMapper.addNode(newNode, incomingEdges, baseName, MLST_LENGTH);
        
        // Assert - reload graph and verify
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 5 nodes", 5, loadedGraph.getNumNodes());
        Assert.assertEquals("Should have 10 edges (6 original + 4 new)", 
                          10, loadedGraph.getNumEdges());
        
        // Verify edge weights are preserved
        boolean foundZeroWeight = false;
        boolean foundMaxWeight = false;
        boolean foundMinWeight = false;
        boolean foundNegativeWeight = false;
        
        for (Edge edge : loadedGraph.getEdges()) {
            if (edge.getDestination().getID() == 4) {
                if (edge.getWeight() == 0) foundZeroWeight = true;
                if (edge.getWeight() == Integer.MAX_VALUE) foundMaxWeight = true;
                if (edge.getWeight() == Integer.MIN_VALUE) foundMinWeight = true;
                if (edge.getWeight() == -999) foundNegativeWeight = true;
            }
        }
        
        Assert.assertTrue("Should find edge with weight 0", foundZeroWeight);
        Assert.assertTrue("Should find edge with MAX weight", foundMaxWeight);
        Assert.assertTrue("Should find edge with MIN weight", foundMinWeight);
        Assert.assertTrue("Should find edge with negative weight", foundNegativeWeight);
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test adding a node to empty graph.
     */
    @Test
    public void testAddNodeToEmptyGraph() throws IOException {
        // Arrange - create and save empty graph
        String baseName = TEST_BASE_NAME + "_addnode6";
        Graph emptyGraph = new Graph(new ArrayList<>());
        GraphMapper.saveGraph(emptyGraph, MLST_LENGTH, baseName);
        
        // Create new node with no incoming edges
        Node newNode = new Node("ACGTACGTACGTACGTACGT", 0);
        List<Edge> incomingEdges = new ArrayList<>();
        
        // Act
        GraphMapper.addNode(newNode, incomingEdges, baseName, MLST_LENGTH);
        
        // Assert - reload graph and verify
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 1 node", 1, loadedGraph.getNumNodes());
        Assert.assertEquals("Should have 0 edges", 0, loadedGraph.getNumEdges());
        Assert.assertEquals("Node ID should be 0", 0, loadedGraph.getNodes().get(0).getID());
        Assert.assertEquals("Node MLST should match", "ACGTACGTACGTACGTACGT", 
                          loadedGraph.getNodes().get(0).getMLSTdata());
        
        // Clean up
        deleteTestFiles(baseName);
    }
    
    // Helper methods
    
    /**
     * Create a test graph with known structure.
     */
    private Graph createTestGraph() {
        Node n0 = new Node("ATCGATCGATCGATCGATCG", 0);
        Node n1 = new Node("GCTAGCTAGCTAGCTAGCTA", 1);
        Node n2 = new Node("TGACTGACTGACTGACTGAC", 2);
        Node n3 = new Node("CGATCGATCGATCGATCGAT", 3);
        
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(n0, n1, 5));
        edges.add(new Edge(n0, n2, 3));
        edges.add(new Edge(n1, n2, 2));
        edges.add(new Edge(n1, n3, 4));
        edges.add(new Edge(n2, n3, 1));
        edges.add(new Edge(n3, n0, 6));
        
        return new Graph(edges);
    }
    
    /**
     * Delete test files created during testing.
     */
    private void deleteTestFiles(String baseName) {
        new File(baseName + "_edges.dat").delete();
        new File(baseName + "_nodes.dat").delete();
    }
}
