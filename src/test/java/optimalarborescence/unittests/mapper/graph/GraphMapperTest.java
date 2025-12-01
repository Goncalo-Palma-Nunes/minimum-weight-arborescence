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

    // ===== Remove Node Tests =====

    @Test
    public void testRemoveNodeWithNoIncomingNorOutgoingEdges() throws IOException {
        // Arrange - save initial graph
        String baseName = TEST_BASE_NAME + "_removenode1";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        // Add a new isolated node
        Node isolatedNode = new Node("CCCCCCCCCCCCCCCCCCCC", 4);
        GraphMapper.addNode(isolatedNode, new ArrayList<>(), baseName, MLST_LENGTH);
        
        // Act - remove the isolated node
        GraphMapper.removeNode(isolatedNode, baseName, MLST_LENGTH);
        
        // Assert - reload graph and verify
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 4 nodes after removal", 4, loadedGraph.getNumNodes());
        Assert.assertEquals("Should have 6 edges after removal", 6, loadedGraph.getNumEdges());
        
        // Verify the isolated node is gone
        for (Node node : loadedGraph.getNodes()) {
            Assert.assertNotEquals("Isolated node should be removed", 4, node.getID());
        }
        
        // Clean up
        deleteTestFiles(baseName);
    }

    @Test
    public void testRemoveNodeWithIncomingAndNoOutgoingEdges() throws IOException {
        // Arrange - save initial graph
        String baseName = TEST_BASE_NAME + "_removenode2";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);

        // Add a new node with incoming edges
        Node newNode = new Node("CCCCCCCCCCCCCCCCCCCC", 4);
        List<Edge> incomingEdges = new ArrayList<>();
        incomingEdges.add(new Edge(testGraph.getNodes().get(0), newNode, 1));
        GraphMapper.addNode(newNode, incomingEdges, baseName, MLST_LENGTH);

        // Act - remove the new node
        GraphMapper.removeNode(newNode, baseName, MLST_LENGTH);

        // Assert - reload graph and verify
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 4 nodes after removal", 4, loadedGraph.getNumNodes());
        Assert.assertEquals("Should have 6 edges after removal", 6, loadedGraph.getNumEdges());

        // Verify the new node is gone
        for (Node node : loadedGraph.getNodes()) {
            Assert.assertNotEquals("New node should be removed", 4, node.getID());
        }

        // Clean up
        deleteTestFiles(baseName);
    }

    @Test
    public void testRemoveNodeWithNoIncomingAndWithOutgoingEdges() throws IOException {
        // Arrange - save initial graph
        String baseName = TEST_BASE_NAME + "_removenode3";
        Graph g = createTestGraphWithNodeWithoutIncomingEdges();
        GraphMapper.saveGraph(g, MLST_LENGTH, baseName);

        // Act - remove the new node
        GraphMapper.removeNode(g.getNodes().get(g.getNumNodes() - 1), baseName, MLST_LENGTH);

        // Assert - reload graph and verify
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 4 nodes after removal", 4, loadedGraph.getNumNodes());
        Assert.assertEquals("Should have 6 edges after removal", 6, loadedGraph.getNumEdges());

        // Verify the new node is gone
        for (Node node : loadedGraph.getNodes()) {
            Assert.assertNotEquals("New node should be removed", 4, node.getID());
        }

        // Clean up
        deleteTestFiles(baseName);
    }

    // @Test
    // public void testRemoveNodeWithIncomingAndOutgoingEdges() throws IOException {
    // }

    // @Test
    // public void testRemoveNodeThatDoesNotExist() throws IOException {
    // }

    // @Test
    // public void testRemoveNodeFromEmptyGraph() throws IOException {
    //     // Arrange - create and save empty graph
    //     String baseName = TEST_BASE_NAME + "_removenode_empty";;
    //     Graph emptyGraph = new Graph(new ArrayList<>());
    //     GraphMapper.saveGraph(emptyGraph, MLST_LENGTH, baseName);

    //     // Create a node that does not exist in the graph
    //     Node nonExistentNode = new Node("ACGTACGTACGTACGTACGT", 0);
    //     // Act - attempt to remove the non-existent node
    //     GraphMapper.removeNode(nonExistentNode, baseName, MLST_LENGTH);
    //     // Assert - reload graph and verify it is still empty
    //     Graph loadedGraph = GraphMapper.loadGraph(baseName);
    //     Assert.assertEquals("Should still have 0 nodes", 0, loadedGraph.getNumNodes());
    //     Assert.assertEquals("Should still have 0 edges", 0, loadedGraph.getNumEdges());
    //     // Clean up
    //     deleteTestFiles(baseName);
    // }

    // ===== Tests for edgeExists method =====

    /**
     * Test edgeExists returns true for an edge that exists in the graph.
     */
    @Test
    public void testEdgeExistsWhenEdgeIsPresent() throws IOException {
        // Arrange - save test graph
        String baseName = TEST_BASE_NAME + "_edgeexists1";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        // Act - check if edge 0->1 exists
        boolean exists = GraphMapper.edgeExists(0, 1, baseName);
        
        // Assert
        Assert.assertTrue("Edge 0->1 should exist", exists);
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test edgeExists returns false for an edge that does not exist.
     */
    @Test
    public void testEdgeExistsWhenEdgeIsNotPresent() throws IOException {
        // Arrange - save test graph
        String baseName = TEST_BASE_NAME + "_edgeexists2";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        // Act - check if edge 0->3 exists (does not exist in test graph)
        boolean exists = GraphMapper.edgeExists(0, 3, baseName);
        
        // Assert
        Assert.assertFalse("Edge 0->3 should not exist", exists);
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test edgeExists when destination node has no incoming edges.
     */
    @Test
    public void testEdgeExistsWhenDestinationHasNoIncomingEdges() throws IOException {
        // Arrange - create graph with node 4 having no incoming edges
        String baseName = TEST_BASE_NAME + "_edgeexists3";
        Graph g = createTestGraphWithNodeWithoutIncomingEdges();
        GraphMapper.saveGraph(g, MLST_LENGTH, baseName);
        
        // Act - check if edge to node 4 exists
        boolean exists = GraphMapper.edgeExists(0, 4, baseName);
        
        // Assert
        Assert.assertFalse("Edge 0->4 should not exist", exists);
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test edgeExists with multiple edges to same destination.
     */
    @Test
    public void testEdgeExistsWithMultipleEdgesToSameDestination() throws IOException {
        // Arrange - save test graph (node 2 has 2 incoming edges)
        String baseName = TEST_BASE_NAME + "_edgeexists4";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        // Act - check both edges to node 2
        boolean exists0to2 = GraphMapper.edgeExists(0, 2, baseName);
        boolean exists1to2 = GraphMapper.edgeExists(1, 2, baseName);
        boolean exists3to2 = GraphMapper.edgeExists(3, 2, baseName);
        
        // Assert
        Assert.assertTrue("Edge 0->2 should exist", exists0to2);
        Assert.assertTrue("Edge 1->2 should exist", exists1to2);
        Assert.assertFalse("Edge 3->2 should not exist", exists3to2);
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test edgeExists for all edges in the test graph.
     */
    @Test
    public void testEdgeExistsForAllEdges() throws IOException {
        // Arrange - save test graph
        String baseName = TEST_BASE_NAME + "_edgeexists5";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        // Act & Assert - verify all edges exist
        Assert.assertTrue("Edge 0->1 should exist", GraphMapper.edgeExists(0, 1, baseName));
        Assert.assertTrue("Edge 0->2 should exist", GraphMapper.edgeExists(0, 2, baseName));
        Assert.assertTrue("Edge 1->2 should exist", GraphMapper.edgeExists(1, 2, baseName));
        Assert.assertTrue("Edge 1->3 should exist", GraphMapper.edgeExists(1, 3, baseName));
        Assert.assertTrue("Edge 2->3 should exist", GraphMapper.edgeExists(2, 3, baseName));
        Assert.assertTrue("Edge 3->0 should exist", GraphMapper.edgeExists(3, 0, baseName));
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test edgeExists on empty graph.
     */
    @Test
    public void testEdgeExistsOnEmptyGraph() throws IOException {
        // Arrange - create empty graph
        String baseName = TEST_BASE_NAME + "_edgeexists6";
        Graph emptyGraph = new Graph(new ArrayList<>());
        GraphMapper.saveGraph(emptyGraph, MLST_LENGTH, baseName);
        
        // Act
        boolean exists = GraphMapper.edgeExists(0, 1, baseName);
        
        // Assert
        Assert.assertFalse("No edges should exist in empty graph", exists);
        
        // Clean up
        deleteTestFiles(baseName);
    }

    // ===== Tests for removeEdge method =====

    /**
     * Test removing an edge that exists.
     */
    @Test
    public void testRemoveEdgeWhenEdgeExists() throws IOException {
        // Arrange - save test graph
        String baseName = TEST_BASE_NAME + "_removeedge1";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        // Verify edge exists before removal
        Assert.assertTrue("Edge 0->1 should exist before removal", 
                        GraphMapper.edgeExists(0, 1, baseName));
        
        // Act - remove edge 0->1
        GraphMapper.removeEdge(0, 1, baseName);
        
        // Assert - verify edge no longer exists
        Assert.assertFalse("Edge 0->1 should not exist after removal", 
                         GraphMapper.edgeExists(0, 1, baseName));
        
        // Verify graph integrity - reload and check
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 5 edges after removal", 5, loadedGraph.getNumEdges());
        Assert.assertEquals("Should still have 4 nodes", 4, loadedGraph.getNumNodes());
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test removing an edge that does not exist.
     */
    @Test
    public void testRemoveEdgeWhenEdgeDoesNotExist() throws IOException {
        // Arrange - save test graph
        String baseName = TEST_BASE_NAME + "_removeedge2";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        int initialEdgeCount = testGraph.getNumEdges();
        
        // Act - attempt to remove non-existent edge 0->3
        GraphMapper.removeEdge(0, 3, baseName);
        
        // Assert - graph should be unchanged
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Edge count should be unchanged", 
                          initialEdgeCount, loadedGraph.getNumEdges());
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test removing edge from middle of linked list.
     */
    @Test
    public void testRemoveEdgeFromMiddleOfLinkedList() throws IOException {
        // Arrange - save test graph (node 2 has incoming edges from nodes 0 and 1)
        String baseName = TEST_BASE_NAME + "_removeedge3";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        // Act - remove edge 1->2 (one of two edges to node 2)
        GraphMapper.removeEdge(1, 2, baseName);
        
        // Assert - edge 1->2 should not exist, but 0->2 should still exist
        Assert.assertFalse("Edge 1->2 should not exist after removal", 
                         GraphMapper.edgeExists(1, 2, baseName));
        Assert.assertTrue("Edge 0->2 should still exist", 
                        GraphMapper.edgeExists(0, 2, baseName));
        
        // Verify edge count
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 5 edges after removal", 5, loadedGraph.getNumEdges());
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test removing edge when destination has no incoming edges.
     */
    @Test
    public void testRemoveEdgeWhenDestinationHasNoIncomingEdges() throws IOException {
        // Arrange - create graph with node 4 having no incoming edges
        String baseName = TEST_BASE_NAME + "_removeedge4";
        Graph g = createTestGraphWithNodeWithoutIncomingEdges();
        GraphMapper.saveGraph(g, MLST_LENGTH, baseName);
        
        int initialEdgeCount = g.getNumEdges();
        
        // Act - attempt to remove edge to node 4 (which has no incoming edges)
        GraphMapper.removeEdge(0, 4, baseName);
        
        // Assert - graph should be unchanged
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Edge count should be unchanged", 
                          initialEdgeCount, loadedGraph.getNumEdges());
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test removing the only edge to a destination.
     */
    @Test
    public void testRemoveEdgeWhenOnlyEdgeToDestination() throws IOException {
        // Arrange - save test graph
        String baseName = TEST_BASE_NAME + "_removeedge5";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        // Act - remove edge 0->1 (only edge to node 1)
        GraphMapper.removeEdge(0, 1, baseName);
        
        // Assert - node 1 should have no incoming edges
        Assert.assertFalse("Edge 0->1 should not exist", 
                         GraphMapper.edgeExists(0, 1, baseName));
        
        // Verify graph can still be loaded
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 5 edges", 5, loadedGraph.getNumEdges());
        Assert.assertEquals("Should still have 4 nodes", 4, loadedGraph.getNumNodes());
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test removing multiple edges sequentially.
     */
    @Test
    public void testRemoveMultipleEdgesSequentially() throws IOException {
        // Arrange - save test graph
        String baseName = TEST_BASE_NAME + "_removeedge6";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        // Act - remove multiple edges
        GraphMapper.removeEdge(0, 1, baseName);
        GraphMapper.removeEdge(1, 2, baseName);
        GraphMapper.removeEdge(2, 3, baseName);
        
        // Assert - verify all removed edges don't exist
        Assert.assertFalse("Edge 0->1 should not exist", 
                         GraphMapper.edgeExists(0, 1, baseName));
        Assert.assertFalse("Edge 1->2 should not exist", 
                         GraphMapper.edgeExists(1, 2, baseName));
        Assert.assertFalse("Edge 2->3 should not exist", 
                         GraphMapper.edgeExists(2, 3, baseName));
        
        // Verify remaining edges still exist
        Assert.assertTrue("Edge 0->2 should still exist", 
                        GraphMapper.edgeExists(0, 2, baseName));
        Assert.assertTrue("Edge 1->3 should still exist", 
                        GraphMapper.edgeExists(1, 3, baseName));
        Assert.assertTrue("Edge 3->0 should still exist", 
                        GraphMapper.edgeExists(3, 0, baseName));
        
        // Verify edge count
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 3 edges after removing 3", 3, loadedGraph.getNumEdges());
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test removing all edges from the graph.
     */
    @Test
    public void testRemoveAllEdges() throws IOException {
        // Arrange - save test graph
        String baseName = TEST_BASE_NAME + "_removeedge7";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        // Act - remove all edges
        GraphMapper.removeEdge(0, 1, baseName);
        GraphMapper.removeEdge(0, 2, baseName);
        GraphMapper.removeEdge(1, 2, baseName);
        GraphMapper.removeEdge(1, 3, baseName);
        GraphMapper.removeEdge(2, 3, baseName);
        GraphMapper.removeEdge(3, 0, baseName);
        
        // Assert - verify no edges exist
        Assert.assertFalse("Edge 0->1 should not exist", 
                         GraphMapper.edgeExists(0, 1, baseName));
        Assert.assertFalse("Edge 0->2 should not exist", 
                         GraphMapper.edgeExists(0, 2, baseName));
        Assert.assertFalse("Edge 1->2 should not exist", 
                         GraphMapper.edgeExists(1, 2, baseName));
        Assert.assertFalse("Edge 1->3 should not exist", 
                         GraphMapper.edgeExists(1, 3, baseName));
        Assert.assertFalse("Edge 2->3 should not exist", 
                         GraphMapper.edgeExists(2, 3, baseName));
        Assert.assertFalse("Edge 3->0 should not exist", 
                         GraphMapper.edgeExists(3, 0, baseName));
        
        // Verify graph has no edges but nodes remain
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 0 edges", 0, loadedGraph.getNumEdges());
        Assert.assertEquals("Should still have 4 nodes", 4, loadedGraph.getNumNodes());
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test removeEdge on empty graph does not throw exception.
     */
    @Test
    public void testRemoveEdgeOnEmptyGraph() throws IOException {
        // Arrange - create empty graph
        String baseName = TEST_BASE_NAME + "_removeedge8";
        Graph emptyGraph = new Graph(new ArrayList<>());
        GraphMapper.saveGraph(emptyGraph, MLST_LENGTH, baseName);
        
        // Act - attempt to remove edge (should not throw)
        GraphMapper.removeEdge(0, 1, baseName);
        
        // Assert - graph should remain empty
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 0 edges", 0, loadedGraph.getNumEdges());
        Assert.assertEquals("Should have 0 nodes", 0, loadedGraph.getNumNodes());
        
        // Clean up
        deleteTestFiles(baseName);
    }

    /**
     * Test integration of edgeExists after removeEdge.
     */
    @Test
    public void testEdgeExistsAfterRemoveEdge() throws IOException {
        // Arrange - save test graph
        String baseName = TEST_BASE_NAME + "_removeedge9";
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, baseName);
        
        // Act - remove edge and check existence
        Assert.assertTrue("Edge 1->3 should exist initially", 
                        GraphMapper.edgeExists(1, 3, baseName));
        GraphMapper.removeEdge(1, 3, baseName);
        Assert.assertFalse("Edge 1->3 should not exist after removal", 
                         GraphMapper.edgeExists(1, 3, baseName));
        
        // Verify other edges are unaffected
        Assert.assertTrue("Edge 0->1 should still exist", 
                        GraphMapper.edgeExists(0, 1, baseName));
        Assert.assertTrue("Edge 2->3 should still exist", 
                        GraphMapper.edgeExists(2, 3, baseName));
        
        // Clean up
        deleteTestFiles(baseName);
    }

    @Test
    public void testRemoveAllNodes() throws IOException {
    }
    
    // Helper methods

    private List<Edge> prepareEdges() {
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

        return edges;
    }
    
    /**
     * Create a test graph with known structure.
     */
    private Graph createTestGraph() {
        return new Graph(prepareEdges());
    }

    /**
     * Create a test graph with a node that has no incoming edges, but has outgoing edges.
     */
    private Graph createTestGraphWithNodeWithoutIncomingEdges() {
        List<Edge> edges = prepareEdges();
        Node n4 = new Node("TTTTTTTTTTTTTTTTTTTT", 4);
        // No edges to n4
        Edge e1 = new Edge(n4, edges.get(0).getSource(), 7);
        Edge e2 = new Edge(n4, edges.get(3).getSource(), 8);
        edges.add(e1);
        edges.add(e2);
        return new Graph(edges);
    }

    /**
     * Create a test graph with a cycle.
     */
    // private Graph createTestGraphWithCycle() {
    //     Node n0 = new Node("AAAA", 0);
    //     Node n1 = new Node("TTTT", 1);
    //     Node n2 = new Node("GGGG", 2);
        
    //     List<Edge> edges = new ArrayList<>();
    //     edges.add(new Edge(n0, n1, 10));
    //     edges.add(new Edge(n1, n2, 20));
    //     edges.add(new Edge(n2, n0, 30));
        
    //     return new Graph(edges);
    // }

    // private Graph createTestGraphWithNodeWithIncomingEdgesAndOutgoingEdges() {
    //     List<Edge> edges = prepareEdges();
    //     Node n4 = new Node("TTTTTTTTTTTTTTTTTTTT", 4);
    //     Edge e1 = new Edge(edges.get(0).getDestination(), n4, 7);
    //     Edge e3 = new Edge(n4, edges.get(1).getDestination(), 5);
    //     Edge e2 = new Edge(edges.get(2).getDestination(), n4, 8);
    //     edges.add(e1);
    //     edges.add(e2);
    //     edges.add(e3);
    //     return new Graph(edges);
    // }

    /**
     * Delete test files created during testing.
     */
    private void deleteTestFiles(String baseName) {
        new File(baseName + "_edges.dat").delete();
        new File(baseName + "_nodes.dat").delete();
    }
}
