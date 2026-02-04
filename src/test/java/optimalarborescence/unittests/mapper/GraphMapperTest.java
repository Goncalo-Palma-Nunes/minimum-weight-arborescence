package optimalarborescence.unittests.mapper;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Node;
import optimalarborescence.memorymapper.GraphMapper;
import optimalarborescence.sequences.AllelicProfile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for GraphMapper class.
 */
public class GraphMapperTest {

    private static final String TEST_BASE_NAME = "test_graph_mapper";
    private static final int MLST_LENGTH = 7;
    
    private List<Node> testNodes;
    private List<Edge> testEdges;
    private Graph testGraph;

    private static AllelicProfile createProfile(String data) {
        Character[] chars = new Character[data.length()];
        for (int i = 0; i < data.length(); i++) {
            chars[i] = data.charAt(i);
        }
        return new AllelicProfile(chars, data.length());
    }

    @Before
    public void setup() {
        // Create test nodes
        testNodes = new ArrayList<>();
        testNodes.add(new Node(createProfile("AAAAAAA"), 0));
        testNodes.add(new Node(createProfile("CCCCCCC"), 1));
        testNodes.add(new Node(createProfile("GGGGGGG"), 2));
        testNodes.add(new Node(createProfile("TTTTTTT"), 3));
        testNodes.add(new Node(createProfile("ACGTACG"), 4));

        // Create test edges (directed graph)
        testEdges = new ArrayList<>();
        testEdges.add(new Edge(testNodes.get(0), testNodes.get(1), 10)); // 0->1
        testEdges.add(new Edge(testNodes.get(0), testNodes.get(2), 20)); // 0->2
        testEdges.add(new Edge(testNodes.get(1), testNodes.get(2), 15)); // 1->2
        testEdges.add(new Edge(testNodes.get(2), testNodes.get(3), 25)); // 2->3
        testEdges.add(new Edge(testNodes.get(3), testNodes.get(4), 30)); // 3->4
        testEdges.add(new Edge(testNodes.get(1), testNodes.get(4), 35)); // 1->4

        // Create test graph
        testGraph = new Graph(testEdges);
    }

    @After
    public void cleanup() {
        try {
            // Clean up all test files
            Files.deleteIfExists(Path.of(TEST_BASE_NAME + "_nodes.dat"));
            Files.deleteIfExists(Path.of(TEST_BASE_NAME + "_phylogeny_edges.dat"));
            
            // Clean up per-node edge files
            for (int i = 0; i < 10; i++) {
                Files.deleteIfExists(Path.of(TEST_BASE_NAME + "_edges_node" + i + ".dat"));
            }
        } catch (IOException e) {
            System.err.println("Failed to clean up test files: " + e.getMessage());
        }
    }

    @Test
    public void testSaveAndLoadGraph() throws IOException {
        // Save the graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Verify node file exists
        assertTrue("Node file should exist", 
                  Files.exists(Path.of(TEST_BASE_NAME + "_nodes.dat")));

        // Verify edge files exist for nodes with incoming edges
        assertTrue("Edge file for node 1 should exist", 
                  Files.exists(Path.of(TEST_BASE_NAME + "_edges_node1.dat")));
        assertTrue("Edge file for node 2 should exist", 
                  Files.exists(Path.of(TEST_BASE_NAME + "_edges_node2.dat")));

        // Load the graph back
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);

        // Verify node count
        assertEquals("Should have same number of nodes", 
                    testGraph.getNumNodes(), loadedGraph.getNumNodes());

        // Verify edge count
        assertEquals("Should have same number of edges", 
                    testGraph.getNumEdges(), loadedGraph.getNumEdges());

        // Verify edges are preserved
        List<Edge> loadedEdges = loadedGraph.getEdges();
        for (Edge originalEdge : testEdges) {
            boolean found = false;
            for (Edge loadedEdge : loadedEdges) {
                if (loadedEdge.getSource().getId() == originalEdge.getSource().getId() &&
                    loadedEdge.getDestination().getId() == originalEdge.getDestination().getId() &&
                    loadedEdge.getWeight() == originalEdge.getWeight()) {
                    found = true;
                    break;
                }
            }
            assertTrue("Edge " + originalEdge.getSource().getId() + "->" + 
                      originalEdge.getDestination().getId() + " should exist", found);
        }
    }

    @Test
    public void testSaveGraphNodesOnly() throws IOException {
        // Save just nodes without edges
        GraphMapper.saveGraph(testNodes, MLST_LENGTH, TEST_BASE_NAME);

        // Verify node file exists
        assertTrue("Node file should exist", 
                  Files.exists(Path.of(TEST_BASE_NAME + "_nodes.dat")));

        // Load and verify
        Map<Integer, Node> loadedNodes = GraphMapper.loadNodeMap(TEST_BASE_NAME);
        assertEquals("Should have all nodes", testNodes.size(), loadedNodes.size());

        for (Node node : testNodes) {
            assertTrue("Node " + node.getId() + " should exist", 
                      loadedNodes.containsKey(node.getId()));
        }
    }

    @Test
    public void testLoadNodeMap() throws IOException {
        // Save graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Load just the node map
        Map<Integer, Node> nodeMap = GraphMapper.loadNodeMap(TEST_BASE_NAME);

        // Verify
        assertEquals("Should have all nodes", testGraph.getNumNodes(), nodeMap.size());
        for (Node node : testGraph.getNodes()) {
            assertTrue("Node " + node.getId() + " should exist", 
                      nodeMap.containsKey(node.getId()));
        }
    }

    @Test
    public void testGetIncomingEdges() throws IOException {
        // Save graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Get incoming edges for node 2 (should have 2: 0->2 and 1->2)
        List<Edge> incomingEdges = GraphMapper.getIncomingEdges(TEST_BASE_NAME, 2);

        assertEquals("Node 2 should have 2 incoming edges", 2, incomingEdges.size());
        
        // Verify the edges
        boolean hasEdgeFrom0 = false, hasEdgeFrom1 = false;
        for (Edge edge : incomingEdges) {
            if (edge.getSource().getId() == 0 && edge.getDestination().getId() == 2) {
                hasEdgeFrom0 = true;
            }
            if (edge.getSource().getId() == 1 && edge.getDestination().getId() == 2) {
                hasEdgeFrom1 = true;
            }
        }
        assertTrue("Should have edge 0->2", hasEdgeFrom0);
        assertTrue("Should have edge 1->2", hasEdgeFrom1);
    }

    @Test
    public void testAddNode() throws IOException {
        // Save initial graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Create a new node
        Node newNode = new Node(createProfile("ATCGATC"), 5);
        
        // Create incoming edges to the new node
        List<Edge> incomingEdges = new ArrayList<>();
        incomingEdges.add(new Edge(testNodes.get(0), newNode, 40)); // 0->5
        incomingEdges.add(new Edge(testNodes.get(2), newNode, 45)); // 2->5
        
        // Create outgoing edges from the new node
        List<Edge> outgoingEdges = new ArrayList<>();
        outgoingEdges.add(new Edge(newNode, testNodes.get(1), 50)); // 5->1

        // Add the node
        GraphMapper.addNode(newNode, incomingEdges, outgoingEdges, TEST_BASE_NAME, MLST_LENGTH);

        // Load and verify
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        assertEquals("Should have one more node", testGraph.getNumNodes() + 1, 
                    loadedGraph.getNumNodes());
        assertEquals("Should have correct edge count", 
                    testGraph.getNumEdges() + incomingEdges.size() + outgoingEdges.size(), 
                    loadedGraph.getNumEdges());

        // Verify the new node exists
        boolean newNodeFound = false;
        for (Node node : loadedGraph.getNodes()) {
            if (node.getId() == 5) {
                newNodeFound = true;
                break;
            }
        }
        assertTrue("New node should exist", newNodeFound);

        // Verify incoming edges exist
        List<Edge> loadedIncoming = GraphMapper.getIncomingEdges(TEST_BASE_NAME, 5);
        assertEquals("Should have correct incoming edges", 2, loadedIncoming.size());
    }

    @Test
    public void testAddNodeWithoutOutgoingEdges() throws IOException {
        // Save initial graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Create a new node with only incoming edges
        Node newNode = new Node(createProfile("ATCGATC"), 5);
        List<Edge> incomingEdges = new ArrayList<>();
        incomingEdges.add(new Edge(testNodes.get(0), newNode, 40));

        // Add the node
        GraphMapper.addNode(newNode, incomingEdges, TEST_BASE_NAME, MLST_LENGTH);

        // Load and verify
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        assertEquals("Should have one more node", testGraph.getNumNodes() + 1, 
                    loadedGraph.getNumNodes());
    }

    @Test
    public void testAddNodesBatch() throws IOException {
        // Save initial graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Create new nodes
        Node node5 = new Node(createProfile("ATCGATC"), 5);
        Node node6 = new Node(createProfile("GCTAGCT"), 6);
        List<Node> newNodes = new ArrayList<>();
        newNodes.add(node5);
        newNodes.add(node6);

        // Create edges for new nodes
        Map<Node, List<Edge>> nodeEdges = new HashMap<>();
        
        List<Edge> edgesForNode5 = new ArrayList<>();
        edgesForNode5.add(new Edge(testNodes.get(0), node5, 40)); // 0->5
        nodeEdges.put(node5, edgesForNode5);
        
        List<Edge> edgesForNode6 = new ArrayList<>();
        edgesForNode6.add(new Edge(testNodes.get(1), node6, 45)); // 1->6
        nodeEdges.put(node6, edgesForNode6);

        // Create edges from new nodes to existing nodes
        Map<Node, List<Edge>> existingNodeNewEdges = new HashMap<>();
        List<Edge> edgesToNode1 = new ArrayList<>();
        edgesToNode1.add(new Edge(node5, testNodes.get(1), 50)); // 5->1
        existingNodeNewEdges.put(testNodes.get(1), edgesToNode1);

        // Add nodes in batch
        GraphMapper.addNodesBatch(newNodes, nodeEdges, existingNodeNewEdges, 
                                 TEST_BASE_NAME, MLST_LENGTH);

        // Load and verify
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        assertEquals("Should have added nodes", testGraph.getNumNodes() + 2, 
                    loadedGraph.getNumNodes());
    }

    @Test
    public void testRemoveNode() throws IOException {
        // Save graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Remove node 2
        GraphMapper.removeNode(testNodes.get(2), TEST_BASE_NAME);

        // Load and verify
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        assertEquals("Should have one less node", testGraph.getNumNodes() - 1, 
                    loadedGraph.getNumNodes());

        // Verify node 2 is gone
        for (Node node : loadedGraph.getNodes()) {
            assertNotEquals("Node 2 should not exist", 2, node.getId());
        }

        // Verify edges involving node 2 are gone
        for (Edge edge : loadedGraph.getEdges()) {
            assertNotEquals("No edge should have node 2 as source", 2, 
                          edge.getSource().getId());
            assertNotEquals("No edge should have node 2 as destination", 2, 
                          edge.getDestination().getId());
        }
    }

    @Test
    public void testRemoveNodesBatch() throws IOException {
        // Save graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Remove multiple nodes
        List<Node> nodesToRemove = new ArrayList<>();
        nodesToRemove.add(testNodes.get(1));
        nodesToRemove.add(testNodes.get(3));

        GraphMapper.removeNodesBatch(nodesToRemove, TEST_BASE_NAME, MLST_LENGTH);

        // Load and verify
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        assertEquals("Should have removed nodes", testGraph.getNumNodes() - 2, 
                    loadedGraph.getNumNodes());

        // Verify removed nodes are gone
        for (Node node : loadedGraph.getNodes()) {
            assertNotEquals("Node 1 should not exist", 1, node.getId());
            assertNotEquals("Node 3 should not exist", 3, node.getId());
        }
    }

    @Test
    public void testEdgeExists() throws IOException {
        // Save graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Test existing edges
        assertTrue("Edge 0->1 should exist", 
                  GraphMapper.edgeExists(0, 1, TEST_BASE_NAME));
        assertTrue("Edge 1->2 should exist", 
                  GraphMapper.edgeExists(1, 2, TEST_BASE_NAME));

        // Test non-existing edge
        assertFalse("Edge 4->0 should not exist", 
                   GraphMapper.edgeExists(4, 0, TEST_BASE_NAME));
    }

    @Test
    public void testAddEdge() throws IOException {
        // Save graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Add a new edge
        Edge newEdge = new Edge(testNodes.get(4), testNodes.get(0), 55);
        GraphMapper.addEdge(newEdge, TEST_BASE_NAME);

        // Verify
        assertTrue("New edge should exist", 
                  GraphMapper.edgeExists(4, 0, TEST_BASE_NAME));

        // Load and check edge count
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        assertEquals("Should have one more edge", testGraph.getNumEdges() + 1, 
                    loadedGraph.getNumEdges());
    }

    @Test
    public void testRemoveEdge() throws IOException {
        // Save graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Remove an edge
        GraphMapper.removeEdge(0, 1, TEST_BASE_NAME);

        // Verify
        assertFalse("Edge should be removed", 
                   GraphMapper.edgeExists(0, 1, TEST_BASE_NAME));

        // Load and check edge count
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        assertEquals("Should have one less edge", testGraph.getNumEdges() - 1, 
                    loadedGraph.getNumEdges());
    }

    @Test
    public void testLoadIncidentEdges() throws IOException {
        // Save graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Load incident edges for node 2
        List<Edge> incidentEdges = GraphMapper.loadIncidentEdges(TEST_BASE_NAME, 2);

        // Node 2 has incoming edges from nodes 0 and 1
        assertEquals("Should have 2 incident edges", 2, incidentEdges.size());
    }

    @Test
    public void testSaveArborescence() throws IOException {
        // Create a subset of edges as phylogeny
        List<Edge> phylogeny = new ArrayList<>();
        phylogeny.add(testEdges.get(0)); // 0->1
        phylogeny.add(testEdges.get(1)); // 0->2
        phylogeny.add(testEdges.get(3)); // 2->3
        phylogeny.add(testEdges.get(4)); // 3->4

        // Save the phylogeny
        GraphMapper.saveArborescence(phylogeny, TEST_BASE_NAME);

        // Verify file exists
        assertTrue("Phylogeny file should exist", 
                  Files.exists(Path.of(TEST_BASE_NAME + "_phylogeny_edges.dat")));
    }

    @Test
    public void testGetOutgoingEdges() throws IOException {
        // Save graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Get outgoing edges from node 0 (should have 2: 0->1 and 0->2)
        List<Edge> outgoingEdges = GraphMapper.getOutgoingEdges(TEST_BASE_NAME, 0);

        assertEquals("Node 0 should have 2 outgoing edges", 2, outgoingEdges.size());
        
        // Verify both edges have source = 0
        for (Edge edge : outgoingEdges) {
            assertEquals("All edges should be from node 0", 0, edge.getSource().getId());
        }
    }

    @Test
    public void testRemoveOutgoingEdges() throws IOException {
        // Save graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Remove all outgoing edges from node 0
        GraphMapper.removeOutgoingEdges(TEST_BASE_NAME, 0);

        // Verify edges are removed
        assertFalse("Edge 0->1 should not exist", 
                   GraphMapper.edgeExists(0, 1, TEST_BASE_NAME));
        assertFalse("Edge 0->2 should not exist", 
                   GraphMapper.edgeExists(0, 2, TEST_BASE_NAME));

        // Load and verify total edge count
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        // Should have removed 2 edges (0->1 and 0->2)
        assertEquals("Should have removed outgoing edges", testGraph.getNumEdges() - 2, 
                    loadedGraph.getNumEdges());
    }

    @Test
    public void testSaveEmptyGraph() throws IOException {
        // Create empty graph
        Graph emptyGraph = new Graph(new ArrayList<>());

        // Save empty graph
        GraphMapper.saveGraph(emptyGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Load and verify
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        assertEquals("Should have no nodes", 0, loadedGraph.getNumNodes());
        assertEquals("Should have no edges", 0, loadedGraph.getNumEdges());
    }

    @Test
    public void testSaveGraphWithIsolatedNodes() throws IOException {
        // Create graph with isolated nodes (no edges)
        List<Node> isolatedNodes = new ArrayList<>();
        isolatedNodes.add(new Node(createProfile("AAAAAAA"), 0));
        isolatedNodes.add(new Node(createProfile("CCCCCCC"), 1));
        isolatedNodes.add(new Node(createProfile("GGGGGGG"), 2));
        
        Graph graphWithIsolatedNodes = new Graph(new ArrayList<>());
        for (Node node : isolatedNodes) {
            graphWithIsolatedNodes.addNode(node);
        }

        // Save and load
        GraphMapper.saveGraph(graphWithIsolatedNodes, MLST_LENGTH, TEST_BASE_NAME);
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);

        // Verify
        assertEquals("Should have all nodes", isolatedNodes.size(), 
                    loadedGraph.getNumNodes());
        assertEquals("Should have no edges", 0, loadedGraph.getNumEdges());
    }

    @Test
    public void testMultipleOperations() throws IOException {
        // Save initial graph
        GraphMapper.saveGraph(testGraph, MLST_LENGTH, TEST_BASE_NAME);

        // Add a node
        Node newNode = new Node(createProfile("ATCGATC"), 5);
        List<Edge> incomingEdges = new ArrayList<>();
        incomingEdges.add(new Edge(testNodes.get(0), newNode, 40));
        GraphMapper.addNode(newNode, incomingEdges, TEST_BASE_NAME, MLST_LENGTH);

        // Add an edge
        Edge newEdge = new Edge(testNodes.get(4), newNode, 45);
        GraphMapper.addEdge(newEdge, TEST_BASE_NAME);

        // Remove a node
        GraphMapper.removeNode(testNodes.get(3), TEST_BASE_NAME);

        // Remove an edge
        GraphMapper.removeEdge(1, 2, TEST_BASE_NAME);

        // Load and verify final state
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);
        
        // Original: 5 nodes, 6 edges
        // +1 node (5), +2 edges (0->5, 4->5)
        // -1 node (3), -edges involving 3 (2->3, 3->4)
        // -1 edge (1->2)
        // Final: 5 nodes, 5 edges
        
        assertEquals("Should have correct node count", 5, loadedGraph.getNumNodes());
        assertEquals("Should have correct edge count", 5, loadedGraph.getNumEdges());
    }

    @Test
    public void testGraphWithSelfLoops() throws IOException {
        // Create graph with self-loop
        List<Edge> edgesWithLoop = new ArrayList<>(testEdges);
        edgesWithLoop.add(new Edge(testNodes.get(2), testNodes.get(2), 100)); // 2->2
        
        Graph graphWithLoop = new Graph(edgesWithLoop);

        // Save and load
        GraphMapper.saveGraph(graphWithLoop, MLST_LENGTH, TEST_BASE_NAME);
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);

        // Verify
        assertEquals("Should have all edges including self-loop", 
                    edgesWithLoop.size(), loadedGraph.getNumEdges());
        
        // Check self-loop exists
        boolean selfLoopFound = false;
        for (Edge edge : loadedGraph.getEdges()) {
            if (edge.getSource().getId() == 2 && edge.getDestination().getId() == 2) {
                selfLoopFound = true;
                break;
            }
        }
        assertTrue("Self-loop should exist", selfLoopFound);
    }

    @Test
    public void testGraphWithParallelEdges() throws IOException {
        // Create graph with parallel edges (multiple edges with same source/dest)
        List<Edge> edgesWithParallel = new ArrayList<>(testEdges);
        edgesWithParallel.add(new Edge(testNodes.get(0), testNodes.get(1), 11)); // Another 0->1
        
        Graph graphWithParallel = new Graph(edgesWithParallel);

        // Save and load
        GraphMapper.saveGraph(graphWithParallel, MLST_LENGTH, TEST_BASE_NAME);
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);

        // Verify
        assertEquals("Should have all edges including parallel edges", 
                    edgesWithParallel.size(), loadedGraph.getNumEdges());
        
        // Count edges from 0 to 1
        int count = 0;
        for (Edge edge : loadedGraph.getEdges()) {
            if (edge.getSource().getId() == 0 && edge.getDestination().getId() == 1) {
                count++;
            }
        }
        assertEquals("Should have 2 edges from 0 to 1", 2, count);
    }

    @Test
    public void testLargeGraph() throws IOException {
        // Create a larger graph
        List<Node> largeNodes = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String sequence = String.format("A%06d", i).substring(0, MLST_LENGTH);
            largeNodes.add(new Node(createProfile(sequence), i));
        }

        List<Edge> largeEdges = new ArrayList<>();
        for (int i = 0; i < 99; i++) {
            largeEdges.add(new Edge(largeNodes.get(i), largeNodes.get(i + 1), i * 10));
        }

        Graph largeGraph = new Graph(largeEdges);

        // Save and load
        GraphMapper.saveGraph(largeGraph, MLST_LENGTH, TEST_BASE_NAME);
        Graph loadedGraph = GraphMapper.loadGraph(TEST_BASE_NAME);

        // Verify
        assertEquals("Should have all nodes", largeNodes.size(), loadedGraph.getNumNodes());
        assertEquals("Should have all edges", largeEdges.size(), loadedGraph.getNumEdges());
    }
}
