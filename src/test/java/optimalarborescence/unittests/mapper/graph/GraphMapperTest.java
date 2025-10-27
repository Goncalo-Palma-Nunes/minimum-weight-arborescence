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
        File mlstFile = new File(TEST_BASE_NAME + "_mlst.dat");
        
        Assert.assertTrue("Edge file should exist", edgeFile.exists());
        Assert.assertTrue("Node file should exist", nodeFile.exists());
        Assert.assertTrue("MLST file should exist", mlstFile.exists());
        
        // Verify file sizes are reasonable
        Assert.assertTrue("Edge file should not be empty", edgeFile.length() > 0);
        Assert.assertTrue("Node file should not be empty", nodeFile.length() > 0);
        Assert.assertTrue("MLST file should not be empty", mlstFile.length() > 0);
        
        // Edge file: 6 edges * 12 bytes = 72 bytes
        Assert.assertEquals("Edge file size should be 72 bytes", 72, edgeFile.length());
        
        // Node file: 2 metadata ints + 4 node IDs = 6 ints * 4 bytes = 24 bytes
        Assert.assertEquals("Node file size should be 24 bytes", 24, nodeFile.length());
        
        // MLST file: 4 nodes * (20 bytes MLST + 8 bytes offset) = 112 bytes
        Assert.assertEquals("MLST file size should be 112 bytes", 112, mlstFile.length());
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
                Assert.assertEquals("Source MLST should match", "ATCG", 
                                  loadedEdge.getSource().getMLSTdata());
                Assert.assertEquals("Destination MLST should match", "GCTA", 
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
     * Test getIncomingEdgeOffset method - retrieves offset for node's incoming edges.
     */
    @Test
    public void testGetIncomingEdgeOffset() throws IOException {
        // Arrange
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);
        
        // Act & Assert
        // Node 0 has 1 incoming edge (from node 3)
        long offset0 = GraphMapper.getIncomingEdgeOffset(TEST_BASE_NAME, 0, MLST_LENGTH);
        Assert.assertEquals("Node 0 should have offset 0", 0, offset0);
        
        // Node 1 has 1 incoming edge (from node 0)
        long offset1 = GraphMapper.getIncomingEdgeOffset(TEST_BASE_NAME, 1, MLST_LENGTH);
        Assert.assertEquals("Node 1 should have offset 12", 12, offset1);
        
        // Node 2 has 2 incoming edges (from nodes 0 and 1)
        long offset2 = GraphMapper.getIncomingEdgeOffset(TEST_BASE_NAME, 2, MLST_LENGTH);
        Assert.assertEquals("Node 2 should have offset 24", 24, offset2);
        
        // Node 3 has 2 incoming edges (from nodes 1 and 2)
        long offset3 = GraphMapper.getIncomingEdgeOffset(TEST_BASE_NAME, 3, MLST_LENGTH);
        Assert.assertEquals("Node 3 should have offset 48", 48, offset3);
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
        long offset = GraphMapper.getIncomingEdgeOffset(baseName, 0, MLST_LENGTH);
        
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
        Map<Integer, Node> nodeMap = NodeIndexMapper.loadNodes(
            TEST_BASE_NAME + "_nodes.dat", TEST_BASE_NAME + "_mlst.dat");
        
        // Act - get incoming edges for node 2 (should have 2 edges)
        List<Edge> incomingEdges = GraphMapper.getIncomingEdges(
            TEST_BASE_NAME, 2, MLST_LENGTH, nodeMap);
        
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
            TEST_BASE_NAME + "_nodes.dat", TEST_BASE_NAME + "_mlst.dat");
        
        // Act - get incoming edges for node 1 (should have 1 edge)
        List<Edge> incomingEdges = GraphMapper.getIncomingEdges(
            TEST_BASE_NAME, 1, MLST_LENGTH, nodeMap);
        
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
            baseName + "_nodes.dat", baseName + "_mlst.dat");
        
        // Act
        List<Edge> incomingEdges = GraphMapper.getIncomingEdges(
            baseName, 0, MLST_LENGTH, nodeMap);
        
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
    
    // Helper methods
    
    /**
     * Create a test graph with known structure.
     */
    private Graph createTestGraph() {
        Node n0 = new Node("ATCG", 0);
        Node n1 = new Node("GCTA", 1);
        Node n2 = new Node("TGAC", 2);
        Node n3 = new Node("CGAT", 3);
        
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
        new File(baseName + "_mlst.dat").delete();
    }
}
