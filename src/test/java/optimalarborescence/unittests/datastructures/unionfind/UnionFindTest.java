package optimalarborescence.unittests.datastructures.unionfind;

import optimalarborescence.datastructure.UnionFind;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

public class UnionFindTest {
    
    private UnionFind uf;
    
    @Before
    public void setUp() {
        uf = new UnionFind(10); // Create a UnionFind with 10 elements (1-10)
    }
    
    @Test
    public void testConstructor() {
        UnionFind newUf = new UnionFind(5);
        assertEquals(6, newUf.getSize()); // Size should be n+1 (0-5)
    }
    
    @Test
    public void testGetSize() {
        assertEquals(11, uf.getSize()); // 10 + 1 = 11
    }
    
    @Test
    public void testFindInitialState() {
        // Initially, each element should be its own root
        for (int i = 1; i <= 10; i++) {
            assertEquals(i, uf.find(i));
        }
    }
    
    @Test
    public void testRecursiveFindInitialState() {
        // Initially, each element should be its own root
        for (int i = 1; i <= 10; i++) {
            assertEquals(i, uf.recursiveFind(i));
        }
    }
    
    @Test
    public void testUnionTwoElements() {
        uf.union(1, 2);
        // After union, 1 and 2 should have the same root
        assertEquals(uf.find(1), uf.find(2));
    }
    
    @Test
    public void testUnionMultipleElements() {
        uf.union(1, 2);
        uf.union(2, 3);
        uf.union(3, 4);
        
        // All elements 1, 2, 3, 4 should have the same root
        int root = uf.find(1);
        assertEquals(root, uf.find(2));
        assertEquals(root, uf.find(3));
        assertEquals(root, uf.find(4));
    }
    
    @Test
    public void testUnionSeparateComponents() {
        uf.union(1, 2);
        uf.union(3, 4);
        
        // 1 and 2 should have the same root
        assertEquals(uf.find(1), uf.find(2));
        
        // 3 and 4 should have the same root
        assertEquals(uf.find(3), uf.find(4));
        
        // But 1 and 3 should have different roots
        assertNotEquals(uf.find(1), uf.find(3));
    }
    
    @Test
    public void testUnionThenConnectComponents() {
        uf.union(1, 2);
        uf.union(3, 4);
        
        // Initially two separate components
        assertNotEquals(uf.find(1), uf.find(3));
        
        // Connect the two components
        uf.union(2, 3);
        
        // Now all should have the same root
        int root = uf.find(1);
        assertEquals(root, uf.find(2));
        assertEquals(root, uf.find(3));
        assertEquals(root, uf.find(4));
    }
    
    @Test
    public void testUnionSameElement() {
        int rootBefore = uf.find(5);
        uf.union(5, 5);
        int rootAfter = uf.find(5);
        
        // Union of element with itself should not change anything
        assertEquals(rootBefore, rootAfter);
    }
    
    @Test
    public void testUnionAlreadyConnected() {
        uf.union(1, 2);
        int rootBefore = uf.find(1);
        
        // Union again - should not change the structure
        uf.union(1, 2);
        int rootAfter = uf.find(1);
        
        assertEquals(rootBefore, rootAfter);
    }
    
    @Test
    public void testPathCompression() {
        // Create a chain: 1 -> 2 -> 3 -> 4
        uf.union(1, 2);
        uf.union(2, 3);
        uf.union(3, 4);
        
        int root = uf.find(4);
        
        // After path compression, calling find again should be faster
        // We can't directly test performance, but we can verify correctness
        assertEquals(root, uf.find(1));
        assertEquals(root, uf.find(2));
        assertEquals(root, uf.find(3));
        assertEquals(root, uf.find(4));
    }
    
    @Test
    public void testRecursiveFindWithUnion() {
        uf.union(1, 2);
        uf.union(2, 3);
        
        // Both find methods should return the same root
        assertEquals(uf.find(1), uf.recursiveFind(1));
        assertEquals(uf.find(3), uf.recursiveFind(3));
        assertEquals(uf.recursiveFind(1), uf.recursiveFind(3));
    }
    
    @Test
    public void testComplexUnionSequence() {
        // Create two components
        uf.union(1, 2);
        uf.union(2, 3);
        uf.union(4, 5);
        uf.union(5, 6);
        
        // Verify they are separate
        assertNotEquals(uf.find(1), uf.find(4));
        
        // Create a third component
        uf.union(7, 8);
        uf.union(8, 9);
        
        // Connect first and second
        uf.union(3, 4);
        
        // First and second should now be connected
        assertEquals(uf.find(1), uf.find(6));
        
        // But third should still be separate
        assertNotEquals(uf.find(1), uf.find(7));
        
        // Connect second and third
        uf.union(6, 7);
        
        // Now all should be connected
        int root = uf.find(1);
        for (int i = 2; i <= 9; i++) {
            assertEquals(root, uf.find(i));
        }
    }
    
    @Test
    public void testUnionByRank() {
        // Union by rank should keep the tree balanced
        uf.union(1, 2);
        uf.union(3, 4);
        uf.union(5, 6);
        uf.union(7, 8);
        
        // Merge pairs
        uf.union(1, 3);
        uf.union(5, 7);
        
        // Merge groups
        uf.union(1, 5);
        
        // All elements should be in the same component
        int root = uf.find(1);
        for (int i = 2; i <= 8; i++) {
            assertEquals(root, uf.find(i));
        }
    }
    
    @Test
    public void testBoundaryElements() {
        // Test with element 0 (first element)
        assertEquals(0, uf.find(0));
        
        // Test with element 10 (last element)
        assertEquals(10, uf.find(10));
        
        // Union boundary elements
        uf.union(0, 10);
        assertEquals(uf.find(0), uf.find(10));
    }
    
    @Test
    public void testAllElementsInOneComponent() {
        // Union all elements into one component
        for (int i = 1; i < 10; i++) {
            uf.union(i, i + 1);
        }
        
        // All elements should have the same root
        int root = uf.find(1);
        for (int i = 2; i <= 10; i++) {
            assertEquals(root, uf.find(i));
        }
    }
    
    @Test
    public void testDisjointSets() {
        // Keep all elements disjoint (no unions)
        // Each element should be its own root
        for (int i = 0; i <= 10; i++) {
            assertEquals(i, uf.find(i));
            assertEquals(i, uf.recursiveFind(i));
        }
    }
}
