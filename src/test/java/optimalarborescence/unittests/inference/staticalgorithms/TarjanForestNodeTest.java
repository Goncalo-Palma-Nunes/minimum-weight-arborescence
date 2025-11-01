package optimalarborescence.unittests.inference.staticalgorithms;

import optimalarborescence.inference.TarjanForestNode;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TarjanForestNodeTest {
    
    private Node node0;
    private Node node1;
    private Node node2;
    private Node node3;
    private Node node4;
    private Edge edge01;
    private Edge edge12;
    private Edge edge23;
    private Edge edge34;
    private Edge edge02;
    
    @Before
    public void setUp() {
        // Create test nodes with 20-character MLST data
        node0 = new Node("ATCGATCGATCGATCGATCG", 0);
        node1 = new Node("GCTAGCTAGCTAGCTAGCTA", 1);
        node2 = new Node("TGACTGACTGACTGACTGAC", 2);
        node3 = new Node("CGATCGATCGATCGATCGAT", 3);
        node4 = new Node("AAAAAAAAAAAAAAAAAAAA", 4);
        
        // Create test edges
        edge01 = new Edge(node0, node1, 10);
        edge12 = new Edge(node1, node2, 20);
        edge23 = new Edge(node2, node3, 30);
        edge34 = new Edge(node3, node4, 40);
        edge02 = new Edge(node0, node2, 15);
    }
    
    @After
    public void tearDown() {
        node0 = null;
        node1 = null;
        node2 = null;
        node3 = null;
        node4 = null;
        edge01 = null;
        edge12 = null;
        edge23 = null;
        edge34 = null;
        edge02 = null;
    }
    
    /**
     * Test constructor creates node with correct edge and initializes fields.
     */
    @Test
    public void testConstructor() {
        TarjanForestNode node = new TarjanForestNode(edge01);
        
        Assert.assertNotNull("Node should not be null", node);
        Assert.assertEquals("Edge should match", edge01, node.getEdge());
        Assert.assertNull("Parent should be null initially", node.getParent());
        Assert.assertNotNull("Children list should not be null", node.getChildren());
        Assert.assertTrue("Children list should be empty initially", node.getChildren().isEmpty());
        Assert.assertTrue("New node should be a leaf", node.isLeaf());
    }
    
    /**
     * Test getEdge method returns the correct edge.
     */
    @Test
    public void testGetEdge() {
        TarjanForestNode node = new TarjanForestNode(edge01);
        
        Assert.assertEquals("getEdge should return the edge passed to constructor", edge01, node.getEdge());
    }
    
    /**
     * Test isLeaf returns true for node with no children.
     */
    @Test
    public void testIsLeafWithNoChildren() {
        TarjanForestNode node = new TarjanForestNode(edge01);
        
        Assert.assertTrue("Node with no children should be a leaf", node.isLeaf());
    }
    
    /**
     * Test isLeaf returns false for node with children.
     */
    @Test
    public void testIsLeafWithChildren() {
        TarjanForestNode parent = new TarjanForestNode(edge01);
        TarjanForestNode child = new TarjanForestNode(edge12);
        parent.addChild(child);
        
        Assert.assertFalse("Node with children should not be a leaf", parent.isLeaf());
        Assert.assertTrue("Child with no children should be a leaf", child.isLeaf());
    }
    
    /**
     * Test getChildren returns the children list.
     */
    @Test
    public void testGetChildren() {
        TarjanForestNode parent = new TarjanForestNode(edge01);
        
        Assert.assertNotNull("Children list should not be null", parent.getChildren());
        Assert.assertTrue("Children list should be empty initially", parent.getChildren().isEmpty());
    }
    
    /**
     * Test getParent returns null initially.
     */
    @Test
    public void testGetParentInitially() {
        TarjanForestNode node = new TarjanForestNode(edge01);
        
        Assert.assertNull("Parent should be null initially", node.getParent());
    }
    
    /**
     * Test addChild adds a child to the children list.
     */
    @Test
    public void testAddChild() {
        TarjanForestNode parent = new TarjanForestNode(edge01);
        TarjanForestNode child = new TarjanForestNode(edge12);
        
        parent.addChild(child);
        
        Assert.assertEquals("Parent should have 1 child", 1, parent.getChildren().size());
        Assert.assertTrue("Children list should contain the child", parent.getChildren().contains(child));
        Assert.assertFalse("Parent should not be a leaf", parent.isLeaf());
    }
    
    /**
     * Test addChild with multiple children.
     */
    @Test
    public void testAddMultipleChildren() {
        TarjanForestNode parent = new TarjanForestNode(edge01);
        TarjanForestNode child1 = new TarjanForestNode(edge12);
        TarjanForestNode child2 = new TarjanForestNode(edge23);
        TarjanForestNode child3 = new TarjanForestNode(edge34);
        
        parent.addChild(child1);
        parent.addChild(child2);
        parent.addChild(child3);
        
        Assert.assertEquals("Parent should have 3 children", 3, parent.getChildren().size());
        Assert.assertTrue("Children list should contain child1", parent.getChildren().contains(child1));
        Assert.assertTrue("Children list should contain child2", parent.getChildren().contains(child2));
        Assert.assertTrue("Children list should contain child3", parent.getChildren().contains(child3));
    }
    
    /**
     * Test setParent sets the parent and adds this node to parent's children.
     */
    @Test
    public void testSetParent() {
        TarjanForestNode parent = new TarjanForestNode(edge01);
        TarjanForestNode child = new TarjanForestNode(edge12);
        
        TarjanForestNode returnedParent = child.setParent(parent);
        
        Assert.assertEquals("setParent should return the parent", parent, returnedParent);
        Assert.assertEquals("Child's parent should be set", parent, child.getParent());
        Assert.assertTrue("Parent's children should contain the child", parent.getChildren().contains(child));
        Assert.assertEquals("Parent should have 1 child", 1, parent.getChildren().size());
    }
    
    /**
     * Test setParent with null removes parent relationship.
     */
    @Test
    public void testSetParentNull() {
        TarjanForestNode parent = new TarjanForestNode(edge01);
        TarjanForestNode child = new TarjanForestNode(edge12);
        
        child.setParent(parent);
        Assert.assertEquals("Child should have parent", parent, child.getParent());
        
        child.setParent(null);
        
        Assert.assertNull("Child's parent should be null", child.getParent());
        Assert.assertFalse("Parent's children should not contain the child", parent.getChildren().contains(child));
        Assert.assertEquals("Parent should have 0 children", 0, parent.getChildren().size());
    }
    
    /**
     * Test setParent changes parent and updates both old and new parent's children.
     */
    @Test
    public void testSetParentChangeParent() {
        TarjanForestNode oldParent = new TarjanForestNode(edge01);
        TarjanForestNode newParent = new TarjanForestNode(edge02);
        TarjanForestNode child = new TarjanForestNode(edge12);
        
        // Set initial parent
        child.setParent(oldParent);
        Assert.assertEquals("Child should have old parent", oldParent, child.getParent());
        Assert.assertTrue("Old parent should contain child", oldParent.getChildren().contains(child));
        
        // Change to new parent
        child.setParent(newParent);
        
        Assert.assertEquals("Child should have new parent", newParent, child.getParent());
        Assert.assertTrue("New parent should contain child", newParent.getChildren().contains(child));
        Assert.assertFalse("Old parent should not contain child", oldParent.getChildren().contains(child));
        Assert.assertEquals("Old parent should have 0 children", 0, oldParent.getChildren().size());
        Assert.assertEquals("New parent should have 1 child", 1, newParent.getChildren().size());
    }
    
    /**
     * Test toString returns correct format.
     */
    @Test
    public void testToString() {
        TarjanForestNode node = new TarjanForestNode(edge01);
        String expected = "(0, 1)";
        
        Assert.assertEquals("toString should return correct format", expected, node.toString());
    }
    
    /**
     * Test equals with same object.
     */
    @Test
    public void testEqualsSameObject() {
        TarjanForestNode node = new TarjanForestNode(edge01);
        
        Assert.assertTrue("Node should equal itself", node.equals(node));
    }
    
    /**
     * Test equals with equal nodes.
     */
    @Test
    public void testEqualsEqualNodes() {
        TarjanForestNode node1 = new TarjanForestNode(edge01);
        TarjanForestNode node2 = new TarjanForestNode(edge01);
        
        Assert.assertTrue("Nodes with same edge should be equal", node1.equals(node2));
    }
    
    /**
     * Test equals with different nodes.
     */
    @Test
    public void testEqualsDifferentNodes() {
        TarjanForestNode node1 = new TarjanForestNode(edge01);
        TarjanForestNode node2 = new TarjanForestNode(edge12);
        
        Assert.assertFalse("Nodes with different edges should not be equal", node1.equals(node2));
    }
    
    /**
     * Test equals with null.
     */
    @Test
    public void testEqualsNull() {
        TarjanForestNode node = new TarjanForestNode(edge01);
        
        Assert.assertFalse("Node should not equal null", node.equals(null));
    }
    
    /**
     * Test equals with different class.
     */
    @Test
    public void testEqualsDifferentClass() {
        TarjanForestNode node = new TarjanForestNode(edge01);
        String other = "not a TarjanForestNode";
        
        Assert.assertFalse("Node should not equal object of different class", node.equals(other));
    }
    
    /**
     * Test compareTo with nodes of different weights.
     */
    @Test
    public void testCompareTo() {
        TarjanForestNode node1 = new TarjanForestNode(edge01); // weight 10
        TarjanForestNode node2 = new TarjanForestNode(edge12); // weight 20
        TarjanForestNode node3 = new TarjanForestNode(edge23); // weight 30
        
        Assert.assertTrue("Node with smaller weight should compare as less", node1.compareTo(node2) < 0);
        Assert.assertTrue("Node with larger weight should compare as greater", node3.compareTo(node2) > 0);
    }
    
    /**
     * Test compareTo with nodes of equal weights.
     */
    @Test
    public void testCompareToEqualWeights() {
        Edge edge1 = new Edge(node0, node1, 10);
        Edge edge2 = new Edge(node2, node3, 10);
        TarjanForestNode node1 = new TarjanForestNode(edge1);
        TarjanForestNode node2 = new TarjanForestNode(edge2);
        
        Assert.assertEquals("Nodes with equal weights should compare as equal", 0, node1.compareTo(node2));
    }
    
    /**
     * Test LCA with null argument returns null.
     */
    @Test
    public void testLCAWithNull() {
        TarjanForestNode node = new TarjanForestNode(edge01);
        
        Assert.assertNull("LCA with null should return null", node.LCA(null));
    }
    
    /**
     * Test LCA of node with itself returns the node.
     */
    @Test
    public void testLCAWithSelf() {
        TarjanForestNode node = new TarjanForestNode(edge01);
        
        Assert.assertEquals("LCA of node with itself should be itself", node, node.LCA(node));
    }
    
    /**
     * Test LCA of parent and child returns parent.
     */
    @Test
    public void testLCAParentAndChild() {
        TarjanForestNode parent = new TarjanForestNode(edge01);
        TarjanForestNode child = new TarjanForestNode(edge12);
        child.setParent(parent);
        
        Assert.assertEquals("LCA of parent and child should be parent", parent, child.LCA(parent));
        Assert.assertEquals("LCA should be symmetric", parent, parent.LCA(child));
    }
    
    /**
     * Test LCA of siblings returns their parent.
     */
    @Test
    public void testLCASiblings() {
        TarjanForestNode parent = new TarjanForestNode(edge01);
        TarjanForestNode child1 = new TarjanForestNode(edge12);
        TarjanForestNode child2 = new TarjanForestNode(edge23);
        
        child1.setParent(parent);
        child2.setParent(parent);
        
        Assert.assertEquals("LCA of siblings should be their parent", parent, child1.LCA(child2));
        Assert.assertEquals("LCA should be symmetric", parent, child2.LCA(child1));
    }
    
    /**
     * Test LCA in a deeper tree structure.
     */
    @Test
    public void testLCADeeperTree() {
        // Create tree: root -> child1 -> grandchild1
        //                  -> child2 -> grandchild2
        TarjanForestNode root = new TarjanForestNode(edge01);
        TarjanForestNode child1 = new TarjanForestNode(edge12);
        TarjanForestNode child2 = new TarjanForestNode(edge02);
        TarjanForestNode grandchild1 = new TarjanForestNode(edge23);
        TarjanForestNode grandchild2 = new TarjanForestNode(edge34);
        
        child1.setParent(root);
        child2.setParent(root);
        grandchild1.setParent(child1);
        grandchild2.setParent(child2);
        
        Assert.assertEquals("LCA of grandchildren should be root", root, grandchild1.LCA(grandchild2));
        Assert.assertEquals("LCA should be symmetric", root, grandchild2.LCA(grandchild1));
        Assert.assertEquals("LCA of grandchild1 and child2 should be root", root, grandchild1.LCA(child2));
    }
    
    /**
     * Test LCA of nodes in same lineage returns ancestor.
     */
    @Test
    public void testLCASameLineage() {
        // Create chain: root -> child -> grandchild -> greatGrandchild
        TarjanForestNode root = new TarjanForestNode(edge01);
        TarjanForestNode child = new TarjanForestNode(edge12);
        TarjanForestNode grandchild = new TarjanForestNode(edge23);
        TarjanForestNode greatGrandchild = new TarjanForestNode(edge34);
        
        child.setParent(root);
        grandchild.setParent(child);
        greatGrandchild.setParent(grandchild);
        
        Assert.assertEquals("LCA of descendant and ancestor should be ancestor", 
                          child, greatGrandchild.LCA(child));
        Assert.assertEquals("LCA should be symmetric", 
                          child, child.LCA(greatGrandchild));
        Assert.assertEquals("LCA of great-grandchild and root should be root", 
                          root, greatGrandchild.LCA(root));
    }
    
    /**
     * Test LCA of nodes in different trees returns null.
     */
    @Test
    public void testLCADifferentTrees() {
        // Create two separate trees
        TarjanForestNode tree1Root = new TarjanForestNode(edge01);
        TarjanForestNode tree1Child = new TarjanForestNode(edge12);
        tree1Child.setParent(tree1Root);
        
        TarjanForestNode tree2Root = new TarjanForestNode(edge23);
        TarjanForestNode tree2Child = new TarjanForestNode(edge34);
        tree2Child.setParent(tree2Root);
        
        Assert.assertNull("LCA of nodes in different trees should be null", 
                        tree1Child.LCA(tree2Child));
        Assert.assertNull("LCA should be symmetric", 
                        tree2Child.LCA(tree1Child));
    }
    
    /**
     * Test LCA of two root nodes (no parents) returns null.
     */
    @Test
    public void testLCATwoRootNodes() {
        TarjanForestNode root1 = new TarjanForestNode(edge01);
        TarjanForestNode root2 = new TarjanForestNode(edge12);
        
        Assert.assertNull("LCA of two root nodes should be null", root1.LCA(root2));
    }
}
