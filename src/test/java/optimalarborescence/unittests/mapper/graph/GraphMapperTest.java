package optimalarborescence.unittests.mapper.graph;

import java.io.File;
import java.io.IOException;
import optimalarborescence.graph.Graph;
import optimalarborescence.memorymapper.GraphMapper;
import optimalarborescence.memorymapper.NodeIndexMapper;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.sequences.AllelicProfile;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GraphMapperTest {

    private static AllelicProfile createProfile(String data) {
        Character[] chars = new Character[data.length()];
        for (int i = 0; i < data.length(); i++) {
            chars[i] = data.charAt(i);
        }
        return new AllelicProfile(chars, data.length());
    }
    
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
        
        // Node file: header (9 bytes: 4+4+1 for num_nodes, mlst_length, sequence_type) + 4 nodes × (4 bytes node_id + 20 bytes MLST + 8 bytes offset) = 9 + 4×32 = 137 bytes
        Assert.assertEquals("Node file size should be 137 bytes", 137, nodeFile.length());

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
            if (loadedEdge.getSource().getId() == 0 && 
                loadedEdge.getDestination().getId() == 1 && 
                loadedEdge.getWeight() == 5) {
                foundMatchingEdge = true;
                Assert.assertEquals("Source MLST should match", createProfile("ATCGATCGATCGATCGATCG"), 
                                  loadedEdge.getSource().getMLSTdata());
                Assert.assertEquals("Destination MLST should match", createProfile("GCTAGCTAGCTAGCTAGCTA"), 
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
            String key = edge.getSource().getId() + "->" + edge.getDestination().getId();
            originalEdgeMap.put(key, edge);
        }
        
        // Verify each loaded edge matches an original edge
        for (Edge loadedEdge : loadedGraph.getEdges()) {
            String key = loadedEdge.getSource().getId() + "->" + loadedEdge.getDestination().getId();
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
        Node n0 = new Node(createProfile("AAAA"), 0);
        Node n1 = new Node(createProfile("TTTT"), 1);
        Node n2 = new Node(createProfile("GGGG"), 2);
        
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
            Assert.assertEquals("All edges should have destination 2", 2, edge.getDestination().getId());
            
            if (edge.getSource().getId() == 0 && edge.getWeight() == 3) {
                foundEdge0to2 = true;
            } else if (edge.getSource().getId() == 1 && edge.getWeight() == 2) {
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
        Assert.assertEquals("Source should be node 0", 0, edge.getSource().getId());
        Assert.assertEquals("Destination should be node 1", 1, edge.getDestination().getId());
        Assert.assertEquals("Weight should be 5", 5, edge.getWeight());
    }
    
    /**
     * Test getIncomingEdges for node with no incoming edges.
     */
    @Test
    public void testGetIncomingEdgesNone() throws IOException {
        // Arrange
        Node n0 = new Node(createProfile("AAAA"), 0);
        Node n1 = new Node(createProfile("TTTT"), 1);
        
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
        Node n0 = new Node(createProfile("ATCG"), 0);
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
        Node n0 = new Node(createProfile("AAAA"), 0);
        Node n1 = new Node(createProfile("TTTT"), 1);
        Node n2 = new Node(createProfile("GGGG"), 2);
        
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
        Node newNode = new Node(createProfile("AAAAAAAAAAAAAAAAAAAA"), 4);
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
            if (node.getId() == 4) {
                foundNewNode = true;
                Assert.assertEquals("New node MLST should match", createProfile("AAAAAAAAAAAAAAAAAAAA"), node.getMLSTdata());
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
        Node newNode = new Node(createProfile("AACCAACCAACCAACCAACC"), 4);
        Node existingNode = new Node(createProfile("ATCGATCGATCGATCGATCG"), 0); // Node from original graph
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
            if (edge.getSource().getId() == 0 && 
                edge.getDestination().getId() == 4 && 
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
        Node newNode = new Node(createProfile("AACCAACCAACCAACCAACC"), 4);
        Node node0 = new Node(createProfile("ATCGATCGATCGATCGATCG"), 0);
        Node node1 = new Node(createProfile("GCTAGCTAGCTAGCTAGCTA"), 1);
        Node node2 = new Node(createProfile("TGACTGACTGACTGACTGAC"), 2);
        
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
            if (edge.getDestination().getId() == 4) {
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
        Node newNode1 = new Node(createProfile("AAATAAATAAATAAATAAAT"), 4);
        List<Edge> edges1 = new ArrayList<>();
        edges1.add(new Edge(new Node(createProfile("ATCGATCGATCGATCGATCG"), 0), newNode1, 10));
        GraphMapper.addNode(newNode1, edges1, baseName, MLST_LENGTH);
        
        // Add second new node
        Node newNode2 = new Node(createProfile("AAAGAAAGAAAGAAAGAAAG"), 5);
        List<Edge> edges2 = new ArrayList<>();
        edges2.add(new Edge(new Node(createProfile("GCTAGCTAGCTAGCTAGCTA"), 1), newNode2, 20));
        GraphMapper.addNode(newNode2, edges2, baseName, MLST_LENGTH);
        
        // Add third new node
        Node newNode3 = new Node(createProfile("AAACAAACAAACAAACAAAC"), 6);
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
            if (node.getId() >= 4 && node.getId() <= 6) {
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
        Node newNode = new Node(createProfile("AAAAAAAAAAAAAAAAAAAA"), 4);
        List<Edge> incomingEdges = new ArrayList<>();
        incomingEdges.add(new Edge(new Node(createProfile("ATCGATCGATCGATCGATCG"), 0), newNode, 0));
        incomingEdges.add(new Edge(new Node(createProfile("GCTAGCTAGCTAGCTAGCTA"), 1), newNode, Integer.MAX_VALUE));
        incomingEdges.add(new Edge(new Node(createProfile("TGACTGACTGACTGACTGAC"), 2), newNode, Integer.MIN_VALUE));
        incomingEdges.add(new Edge(new Node(createProfile("CGATCGATCGATCGATCGAT"), 3), newNode, -999));
        
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
            if (edge.getDestination().getId() == 4) {
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
        Node newNode = new Node(createProfile("ACGTACGTACGTACGTACGT"), 0);
        List<Edge> incomingEdges = new ArrayList<>();
        
        // Act
        GraphMapper.addNode(newNode, incomingEdges, baseName, MLST_LENGTH);
        
        // Assert - reload graph and verify
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 1 node", 1, loadedGraph.getNumNodes());
        Assert.assertEquals("Should have 0 edges", 0, loadedGraph.getNumEdges());
        Assert.assertEquals("Node ID should be 0", 0, loadedGraph.getNodes().get(0).getId());
        Assert.assertEquals("Node MLST should match", createProfile("ACGTACGTACGTACGTACGT"), 
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
        Node isolatedNode = new Node(createProfile("CCCCCCCCCCCCCCCCCCCC"), 4);
        GraphMapper.addNode(isolatedNode, new ArrayList<>(), baseName, MLST_LENGTH);
        
        // Act - remove the isolated node
        GraphMapper.removeNode(isolatedNode, baseName, MLST_LENGTH);
        
        // Assert - reload graph and verify
        Graph loadedGraph = GraphMapper.loadGraph(baseName);
        Assert.assertEquals("Should have 4 nodes after removal", 4, loadedGraph.getNumNodes());
        Assert.assertEquals("Should have 6 edges after removal", 6, loadedGraph.getNumEdges());
        
        // Verify the isolated node is gone
        for (Node node : loadedGraph.getNodes()) {
            Assert.assertNotEquals("Isolated node should be removed", 4, node.getId());
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
        Node newNode = new Node(createProfile("CCCCCCCCCCCCCCCCCCCC"), 4);
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
            Assert.assertNotEquals("New node should be removed", 4, node.getId());
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
            Assert.assertNotEquals("New node should be removed", 4, node.getId());
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
    //     Node nonExistentNode = new Node(createProfile("ACGTACGTACGTACGTACGT"), 0);
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
        Node n0 = new Node(createProfile("ATCGATCGATCGATCGATCG"), 0);
        Node n1 = new Node(createProfile("GCTAGCTAGCTAGCTAGCTA"), 1);
        Node n2 = new Node(createProfile("TGACTGACTGACTGACTGAC"), 2);
        Node n3 = new Node(createProfile("CGATCGATCGATCGATCGAT"), 3);
        
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
        Node n4 = new Node(createProfile("TTTTTTTTTTTTTTTTTTTT"), 4);
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
    //     Node n0 = new Node(createProfile("AAAA"), 0);
    //     Node n1 = new Node(createProfile("TTTT"), 1);
    //     Node n2 = new Node(createProfile("GGGG"), 2);
        
    //     List<Edge> edges = new ArrayList<>();
    //     edges.add(new Edge(n0, n1, 10));
    //     edges.add(new Edge(n1, n2, 20));
    //     edges.add(new Edge(n2, n0, 30));
        
    //     return new Graph(edges);
    // }

    // private Graph createTestGraphWithNodeWithIncomingEdgesAndOutgoingEdges() {
    //     List<Edge> edges = prepareEdges();
    //     Node n4 = new Node(createProfile("TTTTTTTTTTTTTTTTTTTT"), 4);
    //     Edge e1 = new Edge(edges.get(0).getDestination(), n4, 7);
    //     Edge e3 = new Edge(n4, edges.get(1).getDestination(), 5);
    //     Edge e2 = new Edge(edges.get(2).getDestination(), n4, 8);
    //     edges.add(e1);
    //     edges.add(e2);
    //     edges.add(e3);
    //     return new Graph(edges);
    // }

    // ===== Tests for addNodesBatch method =====

    @Test
    public void testAddNodesBatchToExistingGraph() throws IOException {
        // Save initial graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Prepare batch of nodes with edges
        List<Node> newNodes = new ArrayList<>();
        Node node4 = new Node(createProfile("GGGGGGGGGGGGGGGGGGGG"), 4);
        Node node5 = new Node(createProfile("CCCCCCCCCCCCCCCCCCCC"), 5);
        newNodes.add(node4);
        newNodes.add(node5);

        // Prepare edges for new nodes (incoming edges)
        Map<Node, List<Edge>> nodeEdges = new java.util.HashMap<>();
        List<Edge> edgesForNode4 = new ArrayList<>();
        edgesForNode4.add(new Edge(testGraph.getNodes().get(0), node4, 100));
        edgesForNode4.add(new Edge(testGraph.getNodes().get(1), node4, 110));
        nodeEdges.put(node4, edgesForNode4);
        
        List<Edge> edgesForNode5 = new ArrayList<>();
        edgesForNode5.add(new Edge(testGraph.getNodes().get(2), node5, 200));
        nodeEdges.put(node5, edgesForNode5);

        // Add batch
        GraphMapper.addNodesBatch(newNodes, nodeEdges, TEST_BASE_NAME, MLST_LENGTH);

        // Load and verify
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        Assert.assertEquals("Should have 6 nodes (4 original + 2 new)", 6, loadedGraph.getNumNodes());
        Assert.assertEquals("Should have 9 edges (6 original + 3 new)", 9, loadedGraph.getNumEdges());

        // Verify new nodes exist
        boolean hasNode4 = false;
        boolean hasNode5 = false;
        for (Node node : loadedGraph.getNodes()) {
            if (node.getId() == 4) {
                hasNode4 = true;
                Assert.assertEquals("Node 4 MLST should match", createProfile("GGGGGGGGGGGGGGGGGGGG"), 
                                  node.getMLSTdata());
            }
            if (node.getId() == 5) {
                hasNode5 = true;
                Assert.assertEquals("Node 5 MLST should match", createProfile("CCCCCCCCCCCCCCCCCCCC"), 
                                  node.getMLSTdata());
            }
        }
        Assert.assertTrue("Should contain node 4", hasNode4);
        Assert.assertTrue("Should contain node 5", hasNode5);
    }

    @Test
    public void testAddNodesBatchLargeNumber() throws IOException {
        // Save initial graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Prepare 50 new nodes
        List<Node> newNodes = new ArrayList<>();
        for (int i = 4; i < 54; i++) {
            String mlstData = "A".repeat(MLST_LENGTH);
            newNodes.add(new Node(createProfile(mlstData), i));
        }

        // Prepare edges (each new node gets one incoming edge from node 0)
        Map<Node, List<Edge>> nodeEdges = new java.util.HashMap<>();
        Node sourceNode = testGraph.getNodes().get(0);
        for (Node newNode : newNodes) {
            List<Edge> edges = new ArrayList<>();
            edges.add(new Edge(sourceNode, newNode, newNode.getId() * 10));
            nodeEdges.put(newNode, edges);
        }

        // Add batch
        GraphMapper.addNodesBatch(newNodes, nodeEdges, TEST_BASE_NAME, MLST_LENGTH);

        // Load and verify
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        Assert.assertEquals("Should have 54 nodes (4 original + 50 new)", 54, loadedGraph.getNumNodes());
        Assert.assertEquals("Should have 56 edges (6 original + 50 new)", 56, loadedGraph.getNumEdges());
    }

    @Test
    public void testAddNodesBatchEmptyBatch() throws IOException {
        // Save initial graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Get initial counts
        Graph graphBefore = GraphMapper.loadGraph(TEST_BASE_NAME);
        int nodeCountBefore = graphBefore.getNumNodes();
        int edgeCountBefore = graphBefore.getNumEdges();

        // Add empty batch
        GraphMapper.addNodesBatch(new ArrayList<>(), new java.util.HashMap<>(), TEST_BASE_NAME, MLST_LENGTH);

        // Verify no change
        Graph graphAfter = GraphMapper.loadGraph(TEST_BASE_NAME);
        Assert.assertEquals("Node count should not change", nodeCountBefore, graphAfter.getNumNodes());
        Assert.assertEquals("Edge count should not change", edgeCountBefore, graphAfter.getNumEdges());
    }

    @Test
    public void testAddNodesBatchNodesWithoutEdges() throws IOException {
        // Save initial graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Prepare nodes without any incoming edges
        List<Node> newNodes = new ArrayList<>();
        Node node4 = new Node(createProfile("TTTTTTTTTTTTTTTTTTTT"), 4);
        Node node5 = new Node(createProfile("AAAAAAAAAAAAAAAAAAA"), 5);
        newNodes.add(node4);
        newNodes.add(node5);

        // Empty edge map (no incoming edges for new nodes)
        Map<Node, List<Edge>> nodeEdges = new java.util.HashMap<>();

        // Add batch
        GraphMapper.addNodesBatch(newNodes, nodeEdges, TEST_BASE_NAME, MLST_LENGTH);

        // Load and verify
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        Assert.assertEquals("Should have 6 nodes (4 original + 2 new)", 6, loadedGraph.getNumNodes());
        Assert.assertEquals("Should have 6 edges (same as before)", 6, loadedGraph.getNumEdges());

        // Verify nodes exist with correct MLST data
        boolean hasNode4 = false;
        boolean hasNode5 = false;
        for (Node node : loadedGraph.getNodes()) {
            if (node.getId() == 4) hasNode4 = true;
            if (node.getId() == 5) hasNode5 = true;
        }
        Assert.assertTrue("Should contain node 4", hasNode4);
        Assert.assertTrue("Should contain node 5", hasNode5);
    }

    @Test
    public void testAddNodesBatchPreservesOriginalGraph() throws IOException {
        // Save initial graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);
        Graph originalGraph = GraphMapper.loadGraph(TEST_BASE_NAME);

        // Prepare new nodes and edges
        List<Node> newNodes = new ArrayList<>();
        Node node4 = new Node(createProfile("GGGGGGGGGGGGGGGGGGGG"), 4);
        newNodes.add(node4);

        Map<Node, List<Edge>> nodeEdges = new java.util.HashMap<>();
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(testGraph.getNodes().get(0), node4, 100));
        nodeEdges.put(node4, edges);

        // Add batch
        GraphMapper.addNodesBatch(newNodes, nodeEdges, TEST_BASE_NAME, MLST_LENGTH);

        // Load and verify original nodes/edges are preserved
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        
        // Check all original nodes are present with correct MLST data
        for (Node originalNode : originalGraph.getNodes()) {
            Node loadedNode = null;
            for (Node node : loadedGraph.getNodes()) {
                if (node.getId() == originalNode.getId()) {
                    loadedNode = node;
                    break;
                }
            }
            Assert.assertNotNull("Original node " + originalNode.getId() + " should be preserved", loadedNode);
            Assert.assertEquals("MLST data should match for node " + originalNode.getId(),
                              originalNode.getMLSTdata(), loadedNode.getMLSTdata());
        }

        // Check all original edges are present
        for (Edge originalEdge : originalGraph.getEdges()) {
            boolean found = false;
            for (Edge loadedEdge : loadedGraph.getEdges()) {
                if (originalEdge.getSource().getId() == loadedEdge.getSource().getId() &&
                    originalEdge.getDestination().getId() == loadedEdge.getDestination().getId() &&
                    originalEdge.getWeight() == loadedEdge.getWeight()) {
                    found = true;
                    break;
                }
            }
            Assert.assertTrue("Original edge should be preserved: " + 
                            originalEdge.getSource().getId() + " -> " + originalEdge.getDestination().getId(), 
                            found);
        }
    }

    @Test
    public void testAddNodesBatchMultipleEdgesPerNode() throws IOException {
        // Save initial graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Prepare one new node with multiple incoming edges
        List<Node> newNodes = new ArrayList<>();
        Node node4 = new Node(createProfile("GGGGGGGGGGGGGGGGGGGG"), 4);
        newNodes.add(node4);

        // Add 3 incoming edges to node4
        Map<Node, List<Edge>> nodeEdges = new java.util.HashMap<>();
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(testGraph.getNodes().get(0), node4, 100));
        edges.add(new Edge(testGraph.getNodes().get(1), node4, 110));
        edges.add(new Edge(testGraph.getNodes().get(2), node4, 120));
        nodeEdges.put(node4, edges);

        // Add batch
        GraphMapper.addNodesBatch(newNodes, nodeEdges, TEST_BASE_NAME, MLST_LENGTH);

        // Load and verify
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        Assert.assertEquals("Should have 5 nodes (4 original + 1 new)", 5, loadedGraph.getNumNodes());
        Assert.assertEquals("Should have 9 edges (6 original + 3 new)", 9, loadedGraph.getNumEdges());

        // Count edges to node 4
        int countEdgesToNode4 = 0;
        for (Edge edge : loadedGraph.getEdges()) {
            if (edge.getDestination().getId() == 4) {
                countEdgesToNode4++;
            }
        }
        Assert.assertEquals("Should have 3 edges to node 4", 3, countEdgesToNode4);
    }

    /**
     * Test batch removal of multiple nodes and their edges.
     */
    @Test
    public void testRemoveNodesBatch() throws IOException {
        // Create test graph with interconnected nodes
        Graph graph = createTestGraphWithMLST();
        GraphMapper.saveGraph(graph, MLST_LENGTH, TEST_BASE_NAME);

        // Verify initial state
        Graph initialGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        int initialNodeCount = initialGraph.getNumNodes();
        int initialEdgeCount = initialGraph.getNumEdges();
        
        Assert.assertTrue("Should have multiple nodes", initialNodeCount > 3);
        Assert.assertTrue("Should have multiple edges", initialEdgeCount > 0);

        // Remove 2 nodes in batch
        List<Node> nodesToRemove = new ArrayList<>();
        nodesToRemove.add(initialGraph.getNodes().get(1));
        nodesToRemove.add(initialGraph.getNodes().get(2));
        
        GraphMapper.removeNodesBatch(nodesToRemove, TEST_BASE_NAME, MLST_LENGTH);

        // Load and verify
        Graph finalGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        Assert.assertEquals("Should have 2 fewer nodes", 
            initialNodeCount - 2, finalGraph.getNumNodes());

        // Verify removed nodes are not present
        for (Node removedNode : nodesToRemove) {
            boolean found = false;
            for (Node node : finalGraph.getNodes()) {
                if (node.getId() == removedNode.getId()) {
                    found = true;
                    break;
                }
            }
            Assert.assertFalse("Removed node should not be present", found);
        }

        // Verify no edges involve removed nodes
        for (Edge edge : finalGraph.getEdges()) {
            for (Node removedNode : nodesToRemove) {
                Assert.assertNotEquals(removedNode.getId(), edge.getSource().getId());
                Assert.assertNotEquals(removedNode.getId(), edge.getDestination().getId());
            }
        }
    }

    /**
     * Test batch removal with empty list - should be a no-op.
     */
    @Test
    public void testRemoveNodesBatchEmptyList() throws IOException {
        Graph graph = createTestGraph();
        GraphMapper.saveGraph(graph, MLST_LENGTH, TEST_BASE_NAME);

        Graph initialGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        int initialNodeCount = initialGraph.getNumNodes();
        int initialEdgeCount = initialGraph.getNumEdges();

        // Remove empty list
        GraphMapper.removeNodesBatch(List.of(), TEST_BASE_NAME, MLST_LENGTH);

        Graph finalGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        Assert.assertEquals(initialNodeCount, finalGraph.getNumNodes());
        Assert.assertEquals(initialEdgeCount, finalGraph.getNumEdges());
    }

    /**
     * Test batch removal of all nodes.
     */
    @Test
    public void testRemoveNodesBatchAllNodes() throws IOException {
        Graph graph = createTestGraph();
        GraphMapper.saveGraph(graph, MLST_LENGTH, TEST_BASE_NAME);

        Graph initialGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        List<Node> allNodes = new ArrayList<>(initialGraph.getNodes());

        // Remove all nodes
        GraphMapper.removeNodesBatch(allNodes, TEST_BASE_NAME, MLST_LENGTH);

        Graph finalGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        Assert.assertEquals("Should have no nodes", 0, finalGraph.getNumNodes());
        Assert.assertEquals("Should have no edges", 0, finalGraph.getNumEdges());
    }

    /**
     * Test batch removal with complex interconnected graph.
     */
    @Test
    public void testRemoveNodesBatchComplexGraph() throws IOException {
        // Create a complex graph with many interconnections
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            nodes.add(new Node(createProfile(generateMLSTData(i)), i));
        }

        List<Edge> edges = new ArrayList<>();
        // Create a web of connections
        for (int i = 0; i < 9; i++) {
            edges.add(new Edge(nodes.get(i), nodes.get(i + 1), i * 10));
        }
        for (int i = 0; i < 8; i += 2) {
            edges.add(new Edge(nodes.get(i), nodes.get(i + 2), i * 5));
        }

        Graph graph = new Graph(edges);
        GraphMapper.saveGraph(graph, MLST_LENGTH, TEST_BASE_NAME);

        // Remove nodes 2, 4, 6 in batch
        List<Node> nodesToRemove = List.of(nodes.get(2), nodes.get(4), nodes.get(6));
        GraphMapper.removeNodesBatch(nodesToRemove, TEST_BASE_NAME, MLST_LENGTH);

        Graph finalGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        Assert.assertEquals("Should have 7 nodes remaining", 7, finalGraph.getNumNodes());

        // Verify removed nodes are absent
        for (Node node : finalGraph.getNodes()) {
            Assert.assertNotEquals(2, node.getId());
            Assert.assertNotEquals(4, node.getId());
            Assert.assertNotEquals(6, node.getId());
        }

        // Verify no edges involve removed nodes
        for (Edge edge : finalGraph.getEdges()) {
            Assert.assertNotEquals(2, edge.getSource().getId());
            Assert.assertNotEquals(2, edge.getDestination().getId());
            Assert.assertNotEquals(4, edge.getSource().getId());
            Assert.assertNotEquals(4, edge.getDestination().getId());
            Assert.assertNotEquals(6, edge.getSource().getId());
            Assert.assertNotEquals(6, edge.getDestination().getId());
        }
    }

    /**
     * Test batch removal efficiency - removing many nodes at once.
     */
    @Test
    public void testRemoveNodesBatchLargeScale() throws IOException {
        // Create a large graph
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            nodes.add(new Node(createProfile(generateMLSTData(i)), i));
        }

        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < 49; i++) {
            edges.add(new Edge(nodes.get(i), nodes.get(i + 1), i));
        }

        Graph graph = new Graph(edges);
        GraphMapper.saveGraph(graph, MLST_LENGTH, TEST_BASE_NAME);

        // Remove 20 nodes in batch
        List<Node> nodesToRemove = new ArrayList<>();
        for (int i = 10; i < 30; i++) {
            nodesToRemove.add(nodes.get(i));
        }

        GraphMapper.removeNodesBatch(nodesToRemove, TEST_BASE_NAME, MLST_LENGTH);

        Graph finalGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        Assert.assertEquals("Should have 30 nodes remaining", 30, finalGraph.getNumNodes());

        // Verify removed nodes are absent
        for (Node node : finalGraph.getNodes()) {
            Assert.assertTrue("Node ID should be outside removed range", 
                node.getId() < 10 || node.getId() >= 30);
        }
    }

    /**
     * Helper method to generate MLST data for testing.
     */
    private String generateMLSTData(int seed) {
        char[] bases = {'A', 'T', 'G', 'C'};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MLST_LENGTH; i++) {
            sb.append(bases[(seed + i) % 4]);
        }
        return sb.toString();
    }

    /**
     * Helper method to create a test graph with proper MLST data.
     */
    private Graph createTestGraphWithMLST() {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            nodes.add(new Node(createProfile(generateMLSTData(i)), i));
        }

        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(nodes.get(0), nodes.get(1), 10));
        edges.add(new Edge(nodes.get(1), nodes.get(2), 20));
        edges.add(new Edge(nodes.get(2), nodes.get(3), 30));
        edges.add(new Edge(nodes.get(3), nodes.get(4), 40));
        edges.add(new Edge(nodes.get(0), nodes.get(3), 15));

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
