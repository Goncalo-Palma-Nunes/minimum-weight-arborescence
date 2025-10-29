package optimalarborescence.memorymapper;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.inference.dynamic.ATreeNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for ATreeMapper class.
 */
public class ATreeMapperTest {

    private static final String TEST_BASE_NAME = "test_atree";
    private Map<Integer, Node> graphNodes;
    
    @Before
    public void setup() {
        // Create a set of nodes for testing
        graphNodes = new HashMap<>();
        graphNodes.put(0, new Node("AAAA", 0));
        graphNodes.put(1, new Node("CCCC", 1));
        graphNodes.put(2, new Node("GGGG", 2));
        graphNodes.put(3, new Node("TTTT", 3));
        graphNodes.put(4, new Node("ACGT", 4));
        graphNodes.put(5, new Node("TGCA", 5));
    }
    
    @After
    public void teardown() {
        // Clean up test files
        deleteTestFiles();
    }
    
    private void deleteTestFiles() {
        String[] extensions = {"_atree_index.dat", "_atree_children.dat", "_atree_contracted.dat"};
        for (String ext : extensions) {
            File file = new File(TEST_BASE_NAME + ext);
            if (file.exists()) {
                file.delete();
            }
        }
    }
    
    /**
     * Test saving and loading an empty forest.
     */
    @Test
    public void testSaveAndLoadEmptyForest() throws IOException {
        List<ATreeNode> roots = new ArrayList<>();
        
        ATreeMapper.saveATreeForest(roots, graphNodes, TEST_BASE_NAME);
        List<ATreeNode> loadedRoots = ATreeMapper.loadATreeForest(TEST_BASE_NAME, graphNodes);
        
        assertNotNull(loadedRoots);
        assertEquals(0, loadedRoots.size());
    }
    
    /**
     * Test saving and loading a forest with a single root node (no children).
     */
    @Test
    public void testSaveAndLoadSingleRootNode() throws IOException {
        // Create a root node (no edge, simple node)
        ATreeNode root = new ATreeNode(null, 0, true, null, 0);
        List<ATreeNode> roots = new ArrayList<>();
        roots.add(root);
        
        ATreeMapper.saveATreeForest(roots, graphNodes, TEST_BASE_NAME);
        List<ATreeNode> loadedRoots = ATreeMapper.loadATreeForest(TEST_BASE_NAME, graphNodes);
        
        assertNotNull(loadedRoots);
        assertEquals(1, loadedRoots.size());
        
        ATreeNode loadedRoot = loadedRoots.get(0);
        assertNull(loadedRoot.getEdge());
        assertEquals(0, loadedRoot.getCost());
        assertTrue(loadedRoot.isSimpleNode());
        assertNull(loadedRoot.getParent());
        assertTrue(loadedRoot.getChildren().isEmpty());
    }
    
    /**
     * Test saving and loading a simple tree with one root and two children.
     */
    @Test
    public void testSaveAndLoadSimpleTree() throws IOException {
        // Create edges
        Edge edge1 = new Edge(graphNodes.get(0), graphNodes.get(1), 5);
        Edge edge2 = new Edge(graphNodes.get(0), graphNodes.get(2), 3);
        
        // Create root (no edge)
        ATreeNode root = new ATreeNode(null, 0, true, null, 0);
        
        // Create children
        ATreeNode child1 = new ATreeNode(edge1, 5, root, true, null, 1);
        ATreeNode child2 = new ATreeNode(edge2, 3, root, true, null, 2);
        
        root.setChildren(List.of(child1, child2));
        
        List<ATreeNode> roots = new ArrayList<>();
        roots.add(root);
        
        // Save and load
        ATreeMapper.saveATreeForest(roots, graphNodes, TEST_BASE_NAME);
        List<ATreeNode> loadedRoots = ATreeMapper.loadATreeForest(TEST_BASE_NAME, graphNodes);
        
        assertNotNull(loadedRoots);
        assertEquals(1, loadedRoots.size());
        
        ATreeNode loadedRoot = loadedRoots.get(0);
        assertNull(loadedRoot.getEdge());
        assertEquals(2, loadedRoot.getChildren().size());
        
        // Verify children
        @SuppressWarnings("unchecked")
        List<ATreeNode> children = (List<ATreeNode>) (List<?>) loadedRoot.getChildren();
        
        ATreeNode loadedChild1 = children.get(0);
        assertEquals(5, loadedChild1.getCost());
        assertEquals(1, loadedChild1.getEdge().getDestination().getID());
        assertTrue(loadedChild1.isSimpleNode());
        assertNotNull(loadedChild1.getParent());
        assertEquals(loadedRoot, loadedChild1.getParent());
        
        ATreeNode loadedChild2 = children.get(1);
        assertEquals(3, loadedChild2.getCost());
        assertEquals(2, loadedChild2.getEdge().getDestination().getID());
        assertTrue(loadedChild2.isSimpleNode());
    }
    
    /**
     * Test saving and loading a tree with a c-node (complex node with contracted edges).
     */
    @Test
    public void testSaveAndLoadTreeWithCNode() throws IOException {
        // Create edges
        Edge edge1 = new Edge(graphNodes.get(0), graphNodes.get(1), 5);
        Edge contractedEdge1 = new Edge(graphNodes.get(2), graphNodes.get(3), 4);
        Edge contractedEdge2 = new Edge(graphNodes.get(3), graphNodes.get(4), 2);
        
        List<Edge> contractedEdges = new ArrayList<>();
        contractedEdges.add(contractedEdge1);
        contractedEdges.add(contractedEdge2);
        
        // Create root (no edge)
        ATreeNode root = new ATreeNode(null, 0, true, null, 0);
        
        // Create c-node child
        ATreeNode cNode = new ATreeNode(edge1, 5, root, false, contractedEdges, 1);
        root.setChildren(List.of(cNode));
        
        List<ATreeNode> roots = new ArrayList<>();
        roots.add(root);
        
        // Save and load
        ATreeMapper.saveATreeForest(roots, graphNodes, TEST_BASE_NAME);
        List<ATreeNode> loadedRoots = ATreeMapper.loadATreeForest(TEST_BASE_NAME, graphNodes);
        
        assertNotNull(loadedRoots);
        assertEquals(1, loadedRoots.size());
        
        ATreeNode loadedRoot = loadedRoots.get(0);
        assertEquals(1, loadedRoot.getChildren().size());
        
        @SuppressWarnings("unchecked")
        List<ATreeNode> children = (List<ATreeNode>) (List<?>) loadedRoot.getChildren();
        ATreeNode loadedCNode = children.get(0);
        
        assertFalse(loadedCNode.isSimpleNode());
        assertNotNull(loadedCNode.getContractedEdges());
        assertEquals(2, loadedCNode.getContractedEdges().size());
        
        // Verify contracted edges
        Edge loaded1 = loadedCNode.getContractedEdges().get(0);
        assertEquals(2, loaded1.getSource().getID());
        assertEquals(3, loaded1.getDestination().getID());
        assertEquals(4, loaded1.getWeight());
        
        Edge loaded2 = loadedCNode.getContractedEdges().get(1);
        assertEquals(3, loaded2.getSource().getID());
        assertEquals(4, loaded2.getDestination().getID());
        assertEquals(2, loaded2.getWeight());
    }
    
    /**
     * Test saving and loading a forest with multiple roots.
     */
    @Test
    public void testSaveAndLoadMultipleRoots() throws IOException {
        // Create first tree
        Edge edge1 = new Edge(graphNodes.get(0), graphNodes.get(1), 5);
        ATreeNode root1 = new ATreeNode(null, 0, true, null, 0);
        ATreeNode child1 = new ATreeNode(edge1, 5, root1, true, null, 1);
        root1.setChildren(List.of(child1));
        
        // Create second tree
        Edge edge2 = new Edge(graphNodes.get(2), graphNodes.get(3), 3);
        ATreeNode root2 = new ATreeNode(null, 0, true, null, 2);
        ATreeNode child2 = new ATreeNode(edge2, 3, root2, true, null, 3);
        root2.setChildren(List.of(child2));
        
        List<ATreeNode> roots = new ArrayList<>();
        roots.add(root1);
        roots.add(root2);
        
        // Save and load
        ATreeMapper.saveATreeForest(roots, graphNodes, TEST_BASE_NAME);
        List<ATreeNode> loadedRoots = ATreeMapper.loadATreeForest(TEST_BASE_NAME, graphNodes);
        
        assertNotNull(loadedRoots);
        assertEquals(2, loadedRoots.size());
        
        // Verify first tree
        ATreeNode loadedRoot1 = loadedRoots.get(0);
        assertNull(loadedRoot1.getEdge());
        assertEquals(1, loadedRoot1.getChildren().size());
        
        // Verify second tree
        ATreeNode loadedRoot2 = loadedRoots.get(1);
        assertNull(loadedRoot2.getEdge());
        assertEquals(1, loadedRoot2.getChildren().size());
    }
    
    /**
     * Test saving and loading a deep tree (multiple levels).
     */
    @Test
    public void testSaveAndLoadDeepTree() throws IOException {
        // Create edges
        Edge edge1 = new Edge(graphNodes.get(0), graphNodes.get(1), 5);
        Edge edge2 = new Edge(graphNodes.get(1), graphNodes.get(2), 3);
        Edge edge3 = new Edge(graphNodes.get(2), graphNodes.get(3), 2);
        
        // Create tree: root -> child1 -> grandchild1 -> greatGrandchild1
        ATreeNode root = new ATreeNode(null, 0, true, null, 0);
        ATreeNode child1 = new ATreeNode(edge1, 5, root, true, null, 1);
        ATreeNode grandchild1 = new ATreeNode(edge2, 3, child1, true, null, 2);
        ATreeNode greatGrandchild1 = new ATreeNode(edge3, 2, grandchild1, true, null, 3);
        
        root.setChildren(List.of(child1));
        child1.setChildren(List.of(grandchild1));
        grandchild1.setChildren(List.of(greatGrandchild1));
        
        List<ATreeNode> roots = new ArrayList<>();
        roots.add(root);
        
        // Save and load
        ATreeMapper.saveATreeForest(roots, graphNodes, TEST_BASE_NAME);
        List<ATreeNode> loadedRoots = ATreeMapper.loadATreeForest(TEST_BASE_NAME, graphNodes);
        
        assertNotNull(loadedRoots);
        assertEquals(1, loadedRoots.size());
        
        // Verify tree structure
        ATreeNode loadedRoot = loadedRoots.get(0);
        assertNull(loadedRoot.getEdge());
        assertEquals(1, loadedRoot.getChildren().size());
        
        @SuppressWarnings("unchecked")
        List<ATreeNode> children1 = (List<ATreeNode>) (List<?>) loadedRoot.getChildren();
        ATreeNode loadedChild1 = children1.get(0);
        assertEquals(5, loadedChild1.getCost());
        assertEquals(1, loadedChild1.getChildren().size());
        
        @SuppressWarnings("unchecked")
        List<ATreeNode> children2 = (List<ATreeNode>) (List<?>) loadedChild1.getChildren();
        ATreeNode loadedGrandchild1 = children2.get(0);
        assertEquals(3, loadedGrandchild1.getCost());
        assertEquals(1, loadedGrandchild1.getChildren().size());
        
        @SuppressWarnings("unchecked")
        List<ATreeNode> children3 = (List<ATreeNode>) (List<?>) loadedGrandchild1.getChildren();
        ATreeNode loadedGreatGrandchild1 = children3.get(0);
        assertEquals(2, loadedGreatGrandchild1.getCost());
        assertEquals(0, loadedGreatGrandchild1.getChildren().size());
    }
    
    /**
     * Test saving and loading a wide tree (many children).
     */
    @Test
    public void testSaveAndLoadWideTree() throws IOException {
        // Create root
        ATreeNode root = new ATreeNode(null, 0, true, null, 0);
        
        // Create many children
        List<ATreeNode> children = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Edge edge = new Edge(graphNodes.get(0), graphNodes.get(i), i);
            ATreeNode child = new ATreeNode(edge, i, root, true, null, i);
            children.add(child);
        }
        root.setChildren(children);
        
        List<ATreeNode> roots = new ArrayList<>();
        roots.add(root);
        
        // Save and load
        ATreeMapper.saveATreeForest(roots, graphNodes, TEST_BASE_NAME);
        List<ATreeNode> loadedRoots = ATreeMapper.loadATreeForest(TEST_BASE_NAME, graphNodes);
        
        assertNotNull(loadedRoots);
        assertEquals(1, loadedRoots.size());
        
        ATreeNode loadedRoot = loadedRoots.get(0);
        assertEquals(5, loadedRoot.getChildren().size());
        
        // Verify each child
        @SuppressWarnings("unchecked")
        List<ATreeNode> loadedChildren = (List<ATreeNode>) (List<?>) loadedRoot.getChildren();
        for (int i = 0; i < 5; i++) {
            ATreeNode child = loadedChildren.get(i);
            assertEquals(i + 1, child.getCost());
            assertEquals(i + 1, child.getEdge().getDestination().getID());
            assertNotNull(child.getParent());
            assertEquals(loadedRoot, child.getParent());
        }
    }
    
    /**
     * Test that file sizes are reasonable.
     */
    @Test
    public void testFileSizes() throws IOException {
        // Create a moderately complex tree
        Edge edge1 = new Edge(graphNodes.get(0), graphNodes.get(1), 5);
        Edge edge2 = new Edge(graphNodes.get(0), graphNodes.get(2), 3);
        
        ATreeNode root = new ATreeNode(null, 0, true, null, 0);
        ATreeNode child1 = new ATreeNode(edge1, 5, root, true, null, 1);
        ATreeNode child2 = new ATreeNode(edge2, 3, root, true, null, 2);
        root.setChildren(List.of(child1, child2));
        
        List<ATreeNode> roots = new ArrayList<>();
        roots.add(root);
        
        ATreeMapper.saveATreeForest(roots, graphNodes, TEST_BASE_NAME);
        
        // Check file existence and sizes
        File indexFile = new File(TEST_BASE_NAME + "_atree_index.dat");
        File childrenFile = new File(TEST_BASE_NAME + "_atree_children.dat");
        File contractedFile = new File(TEST_BASE_NAME + "_atree_contracted.dat");
        
        assertTrue(indexFile.exists());
        assertTrue(childrenFile.exists());
        assertTrue(contractedFile.exists());
        
        // Index file: header(8) + root_offset(8) + 3 nodes * 40 bytes
        assertEquals(16 + 3 * 40, indexFile.length());
        
        // Children file: root has 2 children (4 + 2*8 = 20 bytes)
        assertEquals(20, childrenFile.length());
        
        // Contracted file: no contracted edges (0 bytes)
        assertEquals(0, contractedFile.length());
    }
    
    /**
     * Test parent-child relationships are correctly preserved.
     */
    @Test
    public void testParentChildRelationships() throws IOException {
        Edge edge1 = new Edge(graphNodes.get(0), graphNodes.get(1), 5);
        Edge edge2 = new Edge(graphNodes.get(1), graphNodes.get(2), 3);
        
        ATreeNode root = new ATreeNode(null, 0, true, null, 0);
        ATreeNode child = new ATreeNode(edge1, 5, root, true, null, 1);
        ATreeNode grandchild = new ATreeNode(edge2, 3, child, true, null, 2);
        
        root.setChildren(List.of(child));
        child.setChildren(List.of(grandchild));
        
        List<ATreeNode> roots = new ArrayList<>();
        roots.add(root);
        
        ATreeMapper.saveATreeForest(roots, graphNodes, TEST_BASE_NAME);
        List<ATreeNode> loadedRoots = ATreeMapper.loadATreeForest(TEST_BASE_NAME, graphNodes);
        
        ATreeNode loadedRoot = loadedRoots.get(0);
        @SuppressWarnings("unchecked")
        List<ATreeNode> children = (List<ATreeNode>) (List<?>) loadedRoot.getChildren();
        ATreeNode loadedChild = children.get(0);
        
        @SuppressWarnings("unchecked")
        List<ATreeNode> grandchildren = (List<ATreeNode>) (List<?>) loadedChild.getChildren();
        ATreeNode loadedGrandchild = grandchildren.get(0);
        
        // Verify parent relationships
        assertNull(loadedRoot.getParent());
        assertEquals(loadedRoot, loadedChild.getParent());
        assertEquals(loadedChild, loadedGrandchild.getParent());
    }
}
