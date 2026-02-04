package optimalarborescence.unittests.mapper;

import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Node;
import optimalarborescence.memorymapper.NodeIndexMapper;
import optimalarborescence.sequences.AllelicProfile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for NodeIndexMapper class.
 */
public class NodeIndexMapperTest {

    private static final String TEST_FILE = "test_node_mapper.dat";
    private static final int MLST_LENGTH = 7;
    private List<Node> testNodes;

    private static AllelicProfile createProfile(String data) {
        Character[] chars = new Character[data.length()];
        for (int i = 0; i < data.length(); i++) {
            chars[i] = data.charAt(i);
        }
        return new AllelicProfile(chars, data.length());
    }

    @Before
    public void setup() {
        // Create test nodes with AllelicProfile sequences
        testNodes = new ArrayList<>();
        testNodes.add(new Node(createProfile("AAAAAAA"), 0));
        testNodes.add(new Node(createProfile("CCCCCCC"), 1));
        testNodes.add(new Node(createProfile("GGGGGGG"), 2));
        testNodes.add(new Node(createProfile("TTTTTTT"), 3));
        testNodes.add(new Node(createProfile("ACGTACG"), 4));
    }

    @After
    public void cleanup() {
        // Clean up test files
        try {
            Files.deleteIfExists(Path.of(TEST_FILE));
        } catch (IOException e) {
            System.err.println("Failed to clean up test file: " + e.getMessage());
        }
    }

    @Test
    public void testSaveAndLoadNodes() throws IOException {
        // Save nodes to file
        NodeIndexMapper.saveGraph(testNodes, MLST_LENGTH, TEST_FILE);

        // Verify file was created
        assertTrue("File should exist", new File(TEST_FILE).exists());

        // Load nodes back
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(TEST_FILE);

        // Verify count
        assertEquals("Should load same number of nodes", testNodes.size(), loadedNodes.size());

        // Verify each node
        for (Node original : testNodes) {
            assertTrue("Node ID " + original.getId() + " should exist", 
                      loadedNodes.containsKey(original.getId()));
            
            Node loaded = loadedNodes.get(original.getId());
            assertEquals("Node ID should match", original.getId(), loaded.getId());
            
            // Verify MLST data
            AllelicProfile originalProfile = (AllelicProfile) original.getMLSTdata();
            AllelicProfile loadedProfile = (AllelicProfile) loaded.getMLSTdata();
            
            assertEquals("Profile length should match", 
                        originalProfile.getLength(), loadedProfile.getLength());
            
            for (int i = 0; i < originalProfile.getLength(); i++) {
                assertEquals("Profile element " + i + " should match",
                           originalProfile.getElementAt(i), loadedProfile.getElementAt(i));
            }
        }
    }

    @Test
    public void testSaveGraphUsingGraphObject() throws IOException {
        // Create a graph from test nodes
        Graph graph = new Graph(new ArrayList<>());
        for (Node node : testNodes) {
            graph.addNode(node);
        }

        // Save using Graph object
        NodeIndexMapper.saveGraph(graph, MLST_LENGTH, TEST_FILE);

        // Load and verify
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(TEST_FILE);
        assertEquals("Should load same number of nodes", testNodes.size(), loadedNodes.size());
    }

    @Test
    public void testGetNumNodes() throws IOException {
        // Save nodes
        NodeIndexMapper.saveGraph(testNodes, MLST_LENGTH, TEST_FILE);

        // Get count
        int count = NodeIndexMapper.getNumNodes(TEST_FILE);

        assertEquals("Node count should match", testNodes.size(), count);
    }

    @Test
    public void testAddNode() throws IOException {
        // Save initial nodes
        NodeIndexMapper.saveGraph(testNodes, MLST_LENGTH, TEST_FILE);

        // Create and add a new node
        Node newNode = new Node(createProfile("ATCGATC"), 5);
        NodeIndexMapper.addNode(newNode, TEST_FILE, MLST_LENGTH);

        // Load and verify
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(TEST_FILE);
        assertEquals("Should have one more node", testNodes.size() + 1, loadedNodes.size());
        assertTrue("New node should exist", loadedNodes.containsKey(5));

        Node loaded = loadedNodes.get(5);
        AllelicProfile originalProfile = (AllelicProfile) newNode.getMLSTdata();
        AllelicProfile loadedProfile = (AllelicProfile) loaded.getMLSTdata();
        
        for (int i = 0; i < MLST_LENGTH; i++) {
            assertEquals("New node data should match",
                       originalProfile.getElementAt(i), loadedProfile.getElementAt(i));
        }
    }

    @Test
    public void testAddNodesBatch() throws IOException {
        // Save initial nodes
        NodeIndexMapper.saveGraph(testNodes, MLST_LENGTH, TEST_FILE);

        // Create new nodes to add
        List<Node> newNodes = new ArrayList<>();
        newNodes.add(new Node(createProfile("ATCGATC"), 5));
        newNodes.add(new Node(createProfile("GCTAGCT"), 6));
        newNodes.add(new Node(createProfile("TAGCTAG"), 7));

        // Add batch
        NodeIndexMapper.addNodesBatch(newNodes, TEST_FILE, MLST_LENGTH);

        // Load and verify
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(TEST_FILE);
        assertEquals("Should have all nodes", testNodes.size() + newNodes.size(), loadedNodes.size());

        // Verify new nodes exist
        for (Node newNode : newNodes) {
            assertTrue("New node " + newNode.getId() + " should exist", 
                      loadedNodes.containsKey(newNode.getId()));
        }
    }

    @Test
    public void testAddNodesBatchEmpty() throws IOException {
        // Save initial nodes
        NodeIndexMapper.saveGraph(testNodes, MLST_LENGTH, TEST_FILE);

        // Add empty list (should be no-op)
        NodeIndexMapper.addNodesBatch(new ArrayList<>(), TEST_FILE, MLST_LENGTH);

        // Verify no change
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(TEST_FILE);
        assertEquals("Node count should be unchanged", testNodes.size(), loadedNodes.size());
    }

    @Test
    public void testRemoveNode() throws IOException {
        // Save nodes
        NodeIndexMapper.saveGraph(testNodes, MLST_LENGTH, TEST_FILE);

        // Remove a node (not the last one)
        Node nodeToRemove = testNodes.get(1); // Node ID 1
        NodeIndexMapper.removeNode(nodeToRemove, TEST_FILE);

        // Load and verify
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(TEST_FILE);
        assertEquals("Should have one less node", testNodes.size() - 1, loadedNodes.size());
        assertFalse("Removed node should not exist", loadedNodes.containsKey(1));

        // Verify other nodes still exist
        for (Node node : testNodes) {
            if (node.getId() != 1) {
                assertTrue("Node " + node.getId() + " should still exist", 
                          loadedNodes.containsKey(node.getId()));
            }
        }
    }

    @Test
    public void testRemoveLastNode() throws IOException {
        // Save nodes
        NodeIndexMapper.saveGraph(testNodes, MLST_LENGTH, TEST_FILE);

        // Remove the last node
        Node lastNode = testNodes.get(testNodes.size() - 1);
        NodeIndexMapper.removeNode(lastNode, TEST_FILE);

        // Load and verify
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(TEST_FILE);
        assertEquals("Should have one less node", testNodes.size() - 1, loadedNodes.size());
        assertFalse("Removed node should not exist", 
                   loadedNodes.containsKey(lastNode.getId()));
    }

    @Test(expected = IOException.class)
    public void testRemoveNonExistentNode() throws IOException {
        // Save nodes
        NodeIndexMapper.saveGraph(testNodes, MLST_LENGTH, TEST_FILE);

        // Try to remove a node that doesn't exist
        Node nonExistentNode = new Node(createProfile("AAAAAAA"), 999);
        NodeIndexMapper.removeNode(nonExistentNode, TEST_FILE);
    }

    @Test
    public void testRemoveNodesBatch() throws IOException {
        // Save nodes
        NodeIndexMapper.saveGraph(testNodes, MLST_LENGTH, TEST_FILE);

        // Remove multiple nodes
        List<Node> nodesToRemove = new ArrayList<>();
        nodesToRemove.add(testNodes.get(1)); // Node ID 1
        nodesToRemove.add(testNodes.get(3)); // Node ID 3

        NodeIndexMapper.removeNodesBatch(nodesToRemove, TEST_FILE);

        // Load and verify
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(TEST_FILE);
        assertEquals("Should have removed nodes", testNodes.size() - 2, loadedNodes.size());
        assertFalse("Node 1 should not exist", loadedNodes.containsKey(1));
        assertFalse("Node 3 should not exist", loadedNodes.containsKey(3));

        // Verify remaining nodes
        assertTrue("Node 0 should exist", loadedNodes.containsKey(0));
        assertTrue("Node 2 should exist", loadedNodes.containsKey(2));
        assertTrue("Node 4 should exist", loadedNodes.containsKey(4));
    }

    @Test
    public void testRemoveNodesBatchEmpty() throws IOException {
        // Save nodes
        NodeIndexMapper.saveGraph(testNodes, MLST_LENGTH, TEST_FILE);

        // Remove empty list (should be no-op)
        NodeIndexMapper.removeNodesBatch(new ArrayList<>(), TEST_FILE);

        // Verify no change
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(TEST_FILE);
        assertEquals("Node count should be unchanged", testNodes.size(), loadedNodes.size());
    }

    @Test
    public void testRemoveAllNodesBatch() throws IOException {
        // Save nodes
        NodeIndexMapper.saveGraph(testNodes, MLST_LENGTH, TEST_FILE);

        // Remove all nodes
        NodeIndexMapper.removeNodesBatch(testNodes, TEST_FILE);

        // Load and verify
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(TEST_FILE);
        assertEquals("Should have no nodes", 0, loadedNodes.size());
    }

    @Test
    public void testBuildNodePositionIndex() throws IOException {
        // Save nodes
        NodeIndexMapper.saveGraph(testNodes, MLST_LENGTH, TEST_FILE);

        // Build index
        Map<Integer, Long> index = NodeIndexMapper.buildNodePositionIndex(TEST_FILE);

        // Verify index contains all nodes
        assertEquals("Index should have all node IDs", testNodes.size(), index.size());
        
        for (Node node : testNodes) {
            assertTrue("Index should contain node " + node.getId(), 
                      index.containsKey(node.getId()));
            assertNotNull("Position should not be null", index.get(node.getId()));
            assertTrue("Position should be positive", index.get(node.getId()) >= 0);
        }
    }

    @Test
    public void testSaveEmptyNodeList() throws IOException {
        // Save empty list
        List<Node> emptyList = new ArrayList<>();
        NodeIndexMapper.saveGraph(emptyList, MLST_LENGTH, TEST_FILE);

        // Load and verify
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(TEST_FILE);
        assertEquals("Should have no nodes", 0, loadedNodes.size());
        
        int count = NodeIndexMapper.getNumNodes(TEST_FILE);
        assertEquals("Count should be zero", 0, count);
    }

    @Test
    public void testSaveAndLoadSingleNode() throws IOException {
        // Save single node
        List<Node> singleNode = new ArrayList<>();
        singleNode.add(new Node(createProfile("ACGTACG"), 0));
        
        NodeIndexMapper.saveGraph(singleNode, MLST_LENGTH, TEST_FILE);

        // Load and verify
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(TEST_FILE);
        assertEquals("Should have one node", 1, loadedNodes.size());
        assertTrue("Node 0 should exist", loadedNodes.containsKey(0));
    }

    @Test
    public void testLargeNodeSet() throws IOException {
        // Create a large set of nodes
        List<Node> largeSet = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            String sequence = String.format("A%06d", i % 1000000);
            largeSet.add(new Node(createProfile(sequence.substring(0, MLST_LENGTH)), i));
        }

        // Save and load
        NodeIndexMapper.saveGraph(largeSet, MLST_LENGTH, TEST_FILE);
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(TEST_FILE);

        // Verify
        assertEquals("Should load all nodes", largeSet.size(), loadedNodes.size());
        
        // Spot check a few nodes
        assertTrue("First node should exist", loadedNodes.containsKey(0));
        assertTrue("Middle node should exist", loadedNodes.containsKey(500));
        assertTrue("Last node should exist", loadedNodes.containsKey(999));
    }

    @Test
    public void testNodeIdOrdering() throws IOException {
        // Create nodes with non-sequential IDs
        List<Node> unorderedNodes = new ArrayList<>();
        unorderedNodes.add(new Node(createProfile("AAAAAAA"), 10));
        unorderedNodes.add(new Node(createProfile("CCCCCCC"), 5));
        unorderedNodes.add(new Node(createProfile("GGGGGGG"), 15));
        unorderedNodes.add(new Node(createProfile("TTTTTTT"), 1));

        // Save and load
        NodeIndexMapper.saveGraph(unorderedNodes, MLST_LENGTH, TEST_FILE);
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(TEST_FILE);

        // Verify all IDs are preserved
        assertEquals("Should have all nodes", 4, loadedNodes.size());
        assertTrue("Node 10 should exist", loadedNodes.containsKey(10));
        assertTrue("Node 5 should exist", loadedNodes.containsKey(5));
        assertTrue("Node 15 should exist", loadedNodes.containsKey(15));
        assertTrue("Node 1 should exist", loadedNodes.containsKey(1));
    }

    @Test
    public void testMultipleAddAndRemoveOperations() throws IOException {
        // Save initial nodes
        NodeIndexMapper.saveGraph(testNodes, MLST_LENGTH, TEST_FILE);

        // Add a node
        Node newNode1 = new Node(createProfile("ATCGATC"), 5);
        NodeIndexMapper.addNode(newNode1, TEST_FILE, MLST_LENGTH);

        // Remove a node
        NodeIndexMapper.removeNode(testNodes.get(0), TEST_FILE);

        // Add another node
        Node newNode2 = new Node(createProfile("GCTAGCT"), 6);
        NodeIndexMapper.addNode(newNode2, TEST_FILE, MLST_LENGTH);

        // Verify final state
        Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes(TEST_FILE);
        assertEquals("Should have correct node count", testNodes.size() + 1, loadedNodes.size());
        assertFalse("Node 0 should not exist", loadedNodes.containsKey(0));
        assertTrue("Node 5 should exist", loadedNodes.containsKey(5));
        assertTrue("Node 6 should exist", loadedNodes.containsKey(6));
    }

    @Test(expected = IOException.class)
    public void testLoadNonExistentFile() throws IOException {
        NodeIndexMapper.loadNodes("non_existent_file.dat");
    }

    @Test(expected = IOException.class)
    public void testGetNumNodesNonExistentFile() throws IOException {
        NodeIndexMapper.getNumNodes("non_existent_file.dat");
    }
}
