package optimalarborescence.unittests.mapper;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.inference.dynamic.ATreeNode;
import optimalarborescence.memorymapper.ATreeMapper;
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
        graphNodes.put(6, new Node("ACGA", 6));
        graphNodes.put(7, new Node("CGTA", 7));
        graphNodes.put(8, new Node("GTAC", 8));
        graphNodes.put(9, new Node("TACG", 9));
    }
    
    @After
    public void teardown() {
        // Clean up test files
        deleteTestFiles();
    }
    
    private void deleteTestFiles() {
        // Clean up new single-file format
        File atreeFile = new File(TEST_BASE_NAME + "_atree.dat");
        if (atreeFile.exists()) {
            atreeFile.delete();
        }
        
        // Also clean up old format files if they exist
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
        ATreeNode root = new ATreeNode(null, 0, true, null);
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
        ATreeNode root = new ATreeNode(null, 0, true, null);
        
        // Create children
        ATreeNode child1 = new ATreeNode(edge1, 5, root, true, null);
        ATreeNode child2 = new ATreeNode(edge2, 3, root, true, null);
        
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
        ATreeNode root = new ATreeNode(null, 0, true, null);
        
        // Create c-node child
        ATreeNode cNode = new ATreeNode(edge1, 5, root, false, contractedEdges);
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
        ATreeNode root1 = new ATreeNode(null, 0, true, null);
        ATreeNode child1 = new ATreeNode(edge1, 5, root1, true, null);
        root1.setChildren(List.of(child1));
        
        // Create second tree
        Edge edge2 = new Edge(graphNodes.get(2), graphNodes.get(3), 3);
        ATreeNode root2 = new ATreeNode(null, 0, true, null);
        ATreeNode child2 = new ATreeNode(edge2, 3, root2, true, null);
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
        ATreeNode root = new ATreeNode(null, 0, true, null);
        ATreeNode child1 = new ATreeNode(edge1, 5, root, true, null);
        ATreeNode grandchild1 = new ATreeNode(edge2, 3, child1, true, null);
        ATreeNode greatGrandchild1 = new ATreeNode(edge3, 2, grandchild1, true, null);

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
        ATreeNode root = new ATreeNode(null, 0, true, null);
        
        // Create many children
        List<ATreeNode> children = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Edge edge = new Edge(graphNodes.get(0), graphNodes.get(i), i);
            ATreeNode child = new ATreeNode(edge, i, root, true, null);
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
        
        ATreeNode root = new ATreeNode(null, 0, true, null);
        ATreeNode child1 = new ATreeNode(edge1, 5, root, true, null);
        ATreeNode child2 = new ATreeNode(edge2, 3, root, true, null);
        root.setChildren(List.of(child1, child2));
        
        List<ATreeNode> roots = new ArrayList<>();
        roots.add(root);
        
        ATreeMapper.saveATreeForest(roots, graphNodes, TEST_BASE_NAME);
        
        // Check that only the single file exists (new format)
        File atreeFile = new File(TEST_BASE_NAME + "_atree.dat");
        assertTrue(atreeFile.exists());
        
        // Check old files don't exist
        File indexFile = new File(TEST_BASE_NAME + "_atree_index.dat");
        File childrenFile = new File(TEST_BASE_NAME + "_atree_children.dat");
        File contractedFile = new File(TEST_BASE_NAME + "_atree_contracted.dat");
        
        assertFalse(indexFile.exists());
        assertFalse(childrenFile.exists());
        assertFalse(contractedFile.exists());
        
        // New file format:
        // Header: num_nodes(4) + num_roots(4) + root_offset(8) = 16 bytes
        // Node 0 (root): fixed(32) + children[2](16) + contracted[0](0) = 48 bytes  
        // Node 1 (child1): fixed(32) + children[0](0) + contracted[0](0) = 32 bytes
        // Node 2 (child2): fixed(32) + children[0](0) + contracted[0](0) = 32 bytes
        // Total: 16 + 48 + 32 + 32 = 128 bytes
        assertEquals(128, atreeFile.length());
    }
    
    /**
     * Test parent-child relationships are correctly preserved.
     */
    @Test
    public void testParentChildRelationships() throws IOException {
        Edge edge1 = new Edge(graphNodes.get(0), graphNodes.get(1), 5);
        Edge edge2 = new Edge(graphNodes.get(1), graphNodes.get(2), 3);
        
        ATreeNode root = new ATreeNode(null, 0, true, null);
        ATreeNode child = new ATreeNode(edge1, 5, root, true, null);
        ATreeNode grandchild = new ATreeNode(edge2, 3, child, true, null);

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
    
    /**
     * Test saving and loading a single simple node with edge (from new tests).
     */
    @Test
    public void testSaveLoadSingleSimpleNode() throws IOException {
        // Create a simple root node
        Edge edge = new Edge(graphNodes.get(0), graphNodes.get(1), 10);
        ATreeNode root = new ATreeNode(edge, 10, true, null);
        
        List<ATreeNode> roots = List.of(root);
        
        // Save
        ATreeMapper.saveATreeForest(roots, graphNodes, TEST_BASE_NAME);
        
        // Verify file exists
        File atreeFile = new File(TEST_BASE_NAME + "_atree.dat");
        assertTrue("ATree file should be created", atreeFile.exists());
        
        // Load
        List<ATreeNode> loadedRoots = ATreeMapper.loadATreeForest(TEST_BASE_NAME, graphNodes);
        
        // Verify
        assertNotNull(loadedRoots);
        assertEquals("Should load one root", 1, loadedRoots.size());
        
        ATreeNode loadedRoot = loadedRoots.get(0);
        assertNotNull(loadedRoot);
        assertEquals(10, loadedRoot.getCost());
        assertTrue(loadedRoot.isSimpleNode());
        assertNull(loadedRoot.getParent());
        assertNotNull(loadedRoot.getChildren());
        assertTrue(loadedRoot.getChildren().isEmpty());
        
        Edge loadedEdge = loadedRoot.getEdge();
        assertNotNull(loadedEdge);
        assertEquals(0, loadedEdge.getSource().getID());
        assertEquals(1, loadedEdge.getDestination().getID());
        assertEquals(10, loadedEdge.getWeight());
    }
    
    /**
     * Test saving and loading a tree with three children.
     */
    @Test
    public void testSaveLoadTreeWith3Children() throws IOException {
        // Create a tree:
        //     root
        //    /  |  \
        //   c1  c2  c3
        Edge rootEdge = new Edge(graphNodes.get(0), graphNodes.get(1), 10);
        ATreeNode root = new ATreeNode(rootEdge, 10, true, null);
        
        Edge c1Edge = new Edge(graphNodes.get(1), graphNodes.get(2), 5);
        ATreeNode child1 = new ATreeNode(c1Edge, 5, true, null);
        child1.setParent(root);
        
        Edge c2Edge = new Edge(graphNodes.get(1), graphNodes.get(3), 7);
        ATreeNode child2 = new ATreeNode(c2Edge, 7, true, null);
        child2.setParent(root);
        
        Edge c3Edge = new Edge(graphNodes.get(1), graphNodes.get(4), 8);
        ATreeNode child3 = new ATreeNode(c3Edge, 8, true, null);
        child3.setParent(root);
        
        List<ATreeNode> children = new ArrayList<>();
        children.add(child1);
        children.add(child2);
        children.add(child3);
        root.setChildren(children);
        
        List<ATreeNode> roots = List.of(root);
        
        // Save
        ATreeMapper.saveATreeForest(roots, graphNodes, TEST_BASE_NAME);
        
        // Load
        List<ATreeNode> loadedRoots = ATreeMapper.loadATreeForest(TEST_BASE_NAME, graphNodes);
        
        // Verify
        assertEquals(1, loadedRoots.size());
        ATreeNode loadedRoot = loadedRoots.get(0);
        
        assertNotNull(loadedRoot);
        assertEquals(10, loadedRoot.getCost());
        assertEquals(3, loadedRoot.getChildren().size());
        
        // Verify children
        @SuppressWarnings("unchecked")
        List<ATreeNode> loadedChildren = (List<ATreeNode>) (List<?>) loadedRoot.getChildren();
        assertEquals(3, loadedChildren.size());
        
        // Check each child has correct parent reference
        for (ATreeNode child : loadedChildren) {
            assertNotNull(child.getParent());
            assertEquals("Child should reference loaded root as parent", loadedRoot, child.getParent());
        }
        
        // Verify costs
        List<Integer> childCosts = new ArrayList<>();
        for (ATreeNode child : loadedChildren) {
            childCosts.add(child.getCost());
        }
        assertTrue(childCosts.contains(5));
        assertTrue(childCosts.contains(7));
        assertTrue(childCosts.contains(8));
    }
    
    /**
     * Test saving and loading a complex node with three contracted edges.
     */
    @Test
    public void testSaveLoadComplexNodeWith3ContractedEdges() throws IOException {
        // Create contracted edges
        List<Edge> contractedEdges = new ArrayList<>();
        contractedEdges.add(new Edge(graphNodes.get(2), graphNodes.get(3), 15));
        contractedEdges.add(new Edge(graphNodes.get(3), graphNodes.get(4), 20));
        contractedEdges.add(new Edge(graphNodes.get(4), graphNodes.get(5), 25));
        
        // Create a complex root node
        Edge edge = new Edge(graphNodes.get(0), graphNodes.get(1), 30);
        ATreeNode root = new ATreeNode(edge, 30, false, contractedEdges);
        
        List<ATreeNode> roots = List.of(root);
        
        // Save
        ATreeMapper.saveATreeForest(roots, graphNodes, TEST_BASE_NAME);
        
        // Load
        List<ATreeNode> loadedRoots = ATreeMapper.loadATreeForest(TEST_BASE_NAME, graphNodes);
        
        // Verify
        assertEquals(1, loadedRoots.size());
        ATreeNode loadedRoot = loadedRoots.get(0);
        
        assertNotNull(loadedRoot);
        assertEquals(30, loadedRoot.getCost());
        assertFalse(loadedRoot.isSimpleNode());
        
        // Verify contracted edges
        List<Edge> loadedContractedEdges = loadedRoot.getContractedEdges();
        assertNotNull(loadedContractedEdges);
        assertEquals(3, loadedContractedEdges.size());
        
        // Check edges (order should be preserved)
        Edge edge1 = loadedContractedEdges.get(0);
        assertEquals(2, edge1.getSource().getID());
        assertEquals(3, edge1.getDestination().getID());
        assertEquals(15, edge1.getWeight());
        
        Edge edge2 = loadedContractedEdges.get(1);
        assertEquals(3, edge2.getSource().getID());
        assertEquals(4, edge2.getDestination().getID());
        assertEquals(20, edge2.getWeight());
        
        Edge edge3 = loadedContractedEdges.get(2);
        assertEquals(4, edge3.getSource().getID());
        assertEquals(5, edge3.getDestination().getID());
        assertEquals(25, edge3.getWeight());
    }
    
    /**
     * Test saving and loading a forest with three separate roots.
     */
    @Test
    public void testSaveLoadForestWith3Roots() throws IOException {
        // Create three separate trees
        Edge edge1 = new Edge(graphNodes.get(0), graphNodes.get(1), 10);
        ATreeNode root1 = new ATreeNode(edge1, 10, true, null);
        
        Edge edge2 = new Edge(graphNodes.get(2), graphNodes.get(3), 20);
        ATreeNode root2 = new ATreeNode(edge2, 20, true, null);
        
        Edge edge3 = new Edge(graphNodes.get(4), graphNodes.get(5), 30);
        ATreeNode root3 = new ATreeNode(edge3, 30, true, null);
        
        List<ATreeNode> roots = List.of(root1, root2, root3);
        
        // Save
        ATreeMapper.saveATreeForest(roots, graphNodes, TEST_BASE_NAME);
        
        // Load
        List<ATreeNode> loadedRoots = ATreeMapper.loadATreeForest(TEST_BASE_NAME, graphNodes);
        
        // Verify
        assertNotNull(loadedRoots);
        assertEquals(3, loadedRoots.size());
        
        // Verify costs
        List<Integer> rootCosts = new ArrayList<>();
        for (ATreeNode root : loadedRoots) {
            rootCosts.add(root.getCost());
        }
        assertTrue(rootCosts.contains(10));
        assertTrue(rootCosts.contains(20));
        assertTrue(rootCosts.contains(30));
    }
    
    /**
     * Test saving and loading a complex tree with both simple and complex nodes.
     */
    @Test
    public void testSaveLoadMixedTreeStructure() throws IOException {
        // Create root (complex node)
        List<Edge> rootContracted = new ArrayList<>();
        rootContracted.add(new Edge(graphNodes.get(0), graphNodes.get(1), 5));
        rootContracted.add(new Edge(graphNodes.get(1), graphNodes.get(2), 6));
        Edge rootEdge = new Edge(graphNodes.get(0), graphNodes.get(2), 100);
        ATreeNode root = new ATreeNode(rootEdge, 100, false, rootContracted);
        
        // Create simple child
        Edge simpleChildEdge = new Edge(graphNodes.get(2), graphNodes.get(3), 50);
        ATreeNode simpleChild = new ATreeNode(simpleChildEdge, 50, true, null);
        simpleChild.setParent(root);
        
        // Create complex child with contracted edges
        List<Edge> complexContracted = new ArrayList<>();
        complexContracted.add(new Edge(graphNodes.get(4), graphNodes.get(5), 10));
        complexContracted.add(new Edge(graphNodes.get(5), graphNodes.get(6), 15));
        Edge complexChildEdge = new Edge(graphNodes.get(2), graphNodes.get(4), 75);
        ATreeNode complexChild = new ATreeNode(complexChildEdge, 75, false, complexContracted);
        complexChild.setParent(root);
        
        // Add grandchild to simple child
        Edge grandChildEdge = new Edge(graphNodes.get(3), graphNodes.get(7), 25);
        ATreeNode grandChild = new ATreeNode(grandChildEdge, 25, true, null);
        grandChild.setParent(simpleChild);
        simpleChild.setChildren(List.of(grandChild));
        
        // Set root's children
        root.setChildren(List.of(simpleChild, complexChild));
        
        List<ATreeNode> roots = List.of(root);
        
        // Save
        ATreeMapper.saveATreeForest(roots, graphNodes, TEST_BASE_NAME);
        
        // Load
        List<ATreeNode> loadedRoots = ATreeMapper.loadATreeForest(TEST_BASE_NAME, graphNodes);
        
        // Verify structure
        assertEquals(1, loadedRoots.size());
        ATreeNode loadedRoot = loadedRoots.get(0);
        
        // Verify root
        assertEquals(100, loadedRoot.getCost());
        assertFalse(loadedRoot.isSimpleNode());
        assertEquals(2, loadedRoot.getContractedEdges().size());
        assertEquals(2, loadedRoot.getChildren().size());
        
        // Verify children types
        @SuppressWarnings("unchecked")
        List<ATreeNode> loadedChildren = (List<ATreeNode>) (List<?>) loadedRoot.getChildren();
        boolean hasSimple = false;
        boolean hasComplex = false;
        
        for (ATreeNode child : loadedChildren) {
            assertEquals(loadedRoot, child.getParent());
            
            if (child.isSimpleNode()) {
                hasSimple = true;
                assertEquals(50, child.getCost());
                assertEquals(1, child.getChildren().size());
                
                // Verify grandchild
                @SuppressWarnings("unchecked")
                List<ATreeNode> grandchildren = (List<ATreeNode>) (List<?>) child.getChildren();
                ATreeNode grandchild = grandchildren.get(0);
                assertEquals(25, grandchild.getCost());
                assertEquals(child, grandchild.getParent());
            } else {
                hasComplex = true;
                assertEquals(75, child.getCost());
                assertEquals(2, child.getContractedEdges().size());
            }
        }
        
        assertTrue("Should have simple child", hasSimple);
        assertTrue("Should have complex child", hasComplex);
    }
    
    /**
     * Test that file size is correct for the new single-file format.
     */
    @Test
    public void testFileSizeIsSingleFile() throws IOException {
        // Create a simple tree
        Edge edge = new Edge(graphNodes.get(0), graphNodes.get(1), 10);
        ATreeNode root = new ATreeNode(edge, 10, true, null);
        
        // Save
        ATreeMapper.saveATreeForest(List.of(root), graphNodes, TEST_BASE_NAME);
        
        // Verify only one file exists
        File atreeFile = new File(TEST_BASE_NAME + "_atree.dat");
        assertTrue("Single ATree file should exist", atreeFile.exists());
        
        // Verify old files don't exist
        File indexFile = new File(TEST_BASE_NAME + "_atree_index.dat");
        File childrenFile = new File(TEST_BASE_NAME + "_atree_children.dat");
        File contractedFile = new File(TEST_BASE_NAME + "_atree_contracted.dat");
        
        assertFalse("Old index file should not exist", indexFile.exists());
        assertFalse("Old children file should not exist", childrenFile.exists());
        assertFalse("Old contracted file should not exist", contractedFile.exists());
        
        // Check file has reasonable size (header + one node)
        long fileSize = atreeFile.length();
        // Header: 4 (num_nodes) + 4 (num_roots) + 8 (root_offset) = 16 bytes
        // Node: 4 (src) + 4 (dst) + 4 (weight) + 4 (cost) + 8 (parent) + 4 (num_children) + 4 (num_contracted) = 32 bytes
        // Total expected: 16 + 32 = 48 bytes
        assertEquals("File size should match expected structure", 48, fileSize);
    }
}
