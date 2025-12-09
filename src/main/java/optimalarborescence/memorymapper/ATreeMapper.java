package optimalarborescence.memorymapper;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.inference.dynamic.ATreeNode;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * ATreeMapper provides memory-mapped file operations for storing and loading ATree forests.
 * 
 * An ATree forest consists of multiple ATree root nodes, where each ATree represents
 * a partially contracted subgraph in the fully dynamic arborescence algorithm.
 * 
 * Single File Structure ({baseName}_atree.dat):
 * 
 * Header:
 *   - num_nodes (4 bytes)
 *   - num_roots (4 bytes)
 *   - root_offsets[] (num_roots × 8 bytes)
 * 
 * Node Entry (variable length):
 *   - edge_src (4 bytes)
 *   - edge_dst (4 bytes)
 *   - edge_weight (4 bytes)
 *   - cost (4 bytes)
 *   - parent_offset (8 bytes)
 *   - num_children (4 bytes)
 *   - children_offsets[] (num_children × 8 bytes)
 *   - num_contracted_edges (4 bytes)
 *   - contracted_edges[] (num_contracted_edges × 12 bytes: src, dst, weight)
 * 
 * Design Notes:
 * - Uses offsets instead of IDs for parent references (direct O(1) access)
 * - Variable-length arrays stored inline (no separate files)
 * - Simple nodes have num_contracted_edges = 0
 * - Root nodes have parent_offset = -1
 * 
 * Lazy Loading:
 * - Supports lazy loading of ATree nodes and their children
 * - loadATreeRootsLazy() loads only root nodes without their children
 * - Children are loaded on-demand via loadChildren()
 * - All loaded nodes are kept in memory (future: could add LRU eviction policy)
 */
public class ATreeMapper {
    
    private static final int BYTES_PER_EDGE = 12; // source(4), dest(4), weight(4)
    
    // Fixed-size portion of each node (before variable-length arrays)
    private static final int NODE_FIXED_SIZE = 32; 
    // edge_src(4) + edge_dst(4) + edge_weight(4) + cost(4) + parent_offset(8) + num_children(4) + num_contracted(4)
    
    /**
     * Save an ATree forest to a single memory-mapped file.
     * 
     * @param roots List of ATree root nodes
     * @param graphNodes Map of node IDs to Node objects (for edge reconstruction)
     * @param baseName Base name for output file
     * @throws IOException if file operations fail
     */
    public static void saveATreeForest(List<ATreeNode> roots, Map<Integer, Node> graphNodes, 
                                       String baseName) throws IOException {
        String fileName = baseName + "_atree.dat";
        
        // Step 1: Collect all nodes in the forest using BFS and calculate their sizes
        List<ATreeNode> allNodes = new ArrayList<>();
        Map<ATreeNode, Long> nodeOffsets = new HashMap<>();
        Map<ATreeNode, Integer> nodeSizes = new HashMap<>();
        
        Queue<ATreeNode> queue = new LinkedList<>(roots);
        while (!queue.isEmpty()) {
            ATreeNode current = queue.poll();
            if (!nodeOffsets.containsKey(current)) {
                allNodes.add(current);
                
                // Calculate size for this node
                int nodeSize = calculateNodeSize(current);
                nodeSizes.put(current, nodeSize);
                
                @SuppressWarnings("unchecked")
                List<ATreeNode> children = (List<ATreeNode>) (List<?>) current.getChildren();
                if (children != null) {
                    queue.addAll(children);
                }
            }
        }
        
        // Step 2: Calculate offsets for all nodes
        int numRoots = roots.size();
        long headerSize = 8 + ((long) numRoots * 8);
        long currentOffset = headerSize;
        
        for (ATreeNode node : allNodes) {
            nodeOffsets.put(node, currentOffset);
            currentOffset += nodeSizes.get(node);
        }
        
        // Step 3: Write everything to the single file
        long fileSize = currentOffset;
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            
            raf.setLength(fileSize);
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
            mbb.order(ByteOrder.nativeOrder());
            
            // Write header
            mbb.putInt(allNodes.size()); // num_nodes
            mbb.putInt(numRoots);        // num_roots
            
            // Write root offsets
            for (ATreeNode root : roots) {
                mbb.putLong(nodeOffsets.get(root));
            }
            
            // Write all nodes
            for (ATreeNode node : allNodes) {
                writeNode(mbb, node, nodeOffsets);
            }
            
            mbb.force();
        }
    }
    
    /**
     * Calculate the size in bytes needed to store a node.
     */
    private static int calculateNodeSize(ATreeNode node) {
        int size = NODE_FIXED_SIZE; // Fixed part
        
        // Add space for children offsets
        @SuppressWarnings("unchecked")
        List<ATreeNode> children = (List<ATreeNode>) (List<?>) node.getChildren();
        if (children != null) {
            size += children.size() * 8; // 8 bytes per child offset
        }
        
        // Add space for contracted edges
        List<Edge> contractedEdges = node.getContractedEdges();
        if (!node.isSimpleNode() && contractedEdges != null) {
            size += contractedEdges.size() * BYTES_PER_EDGE;
        }
        
        return size;
    }
    
    /**
     * Write a single node to the buffer.
     */
    private static void writeNode(MappedByteBuffer mbb, ATreeNode node, 
                                  Map<ATreeNode, Long> nodeOffsets) {
        Edge edge = node.getEdge();
        
        // Write edge data (use -1 for null/root)
        if (edge != null) {
            mbb.putInt(edge.getSource().getID());
            mbb.putInt(edge.getDestination().getID());
            mbb.putInt(edge.getWeight());
        } else {
            mbb.putInt(-1); // source
            mbb.putInt(-1); // destination
            mbb.putInt(-1); // weight
        }
        
        // Write cost
        mbb.putInt(node.getCost());
        
        // Write parent offset
        ATreeNode parent = node.getParent();
        long parentOffset = (parent != null && nodeOffsets.containsKey(parent)) 
                          ? nodeOffsets.get(parent) : -1L;
        mbb.putLong(parentOffset);
        
        // Write children
        @SuppressWarnings("unchecked")
        List<ATreeNode> children = (List<ATreeNode>) (List<?>) node.getChildren();
        int numChildren = (children != null) ? children.size() : 0;
        mbb.putInt(numChildren);
        
        if (children != null) {
            for (ATreeNode child : children) {
                long childOffset = nodeOffsets.getOrDefault(child, -1L);
                mbb.putLong(childOffset);
            }
        }
        
        // Write contracted edges
        List<Edge> contractedEdges = node.getContractedEdges();
        int numContractedEdges = (!node.isSimpleNode() && contractedEdges != null) 
                                ? contractedEdges.size() : 0;
        mbb.putInt(numContractedEdges);
        
        if (numContractedEdges > 0) {
            for (Edge contractedEdge : contractedEdges) {
                mbb.putInt(contractedEdge.getSource().getID());
                mbb.putInt(contractedEdge.getDestination().getID());
                mbb.putInt(contractedEdge.getWeight());
            }
        }
    }
    
    /**
     * Load an ATree forest from a single memory-mapped file.
     * 
     * @param baseName Base name for input file
     * @param graphNodes Map of node IDs to Node objects (for edge reconstruction)
     * @return List of ATree root nodes
     * @throws IOException if file operations fail
     */
    public static List<ATreeNode> loadATreeForest(String baseName, Map<Integer, Node> graphNodes) 
            throws IOException {
        String fileName = baseName + "_atree.dat";
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            
            long fileSize = channel.size();
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
            mbb.order(ByteOrder.nativeOrder());
            
            // Read header
            mbb.getInt(); // numNodes - not needed for loading
            int numRoots = mbb.getInt();
            
            // Read root offsets
            List<Long> rootOffsets = new ArrayList<>();
            for (int i = 0; i < numRoots; i++) {
                rootOffsets.add(mbb.getLong());
            }
            
            // Load all nodes with offset tracking
            Map<Long, ATreeNode> loadedNodes = new HashMap<>();
            List<ATreeNode> roots = new ArrayList<>();
            
            // First pass: create all nodes without parent/children links
            for (Long rootOffset : rootOffsets) {
                loadNodesFromRoot(mbb, rootOffset, graphNodes, loadedNodes);
            }
            
            // Second pass: establish parent-child relationships for ALL nodes
            // We need to do this carefully to avoid setting parent multiple times
            for (Map.Entry<Long, ATreeNode> entry : loadedNodes.entrySet()) {
                long nodeOffset = entry.getKey();
                ATreeNode node = entry.getValue();
                linkSingleNodeRelationships(mbb, nodeOffset, node, loadedNodes);
            }
            
            // Collect roots
            for (Long rootOffset : rootOffsets) {
                ATreeNode root = loadedNodes.get(rootOffset);
                if (root != null) {
                    roots.add(root);
                }
            }
            
            return roots;
        }
    }
    
    /**
     * Load ATree roots lazily from a memory-mapped file.
     * Only the root nodes are loaded; their children will be loaded on-demand.
     * 
     * @param baseName Base name for input file
     * @param graphNodes Map of node IDs to Node objects (for edge reconstruction)
     * @return List of ATree root nodes (with children marked for lazy loading)
     * @throws IOException if file operations fail
     */
    public static List<ATreeNode> loadATreeRootsLazy(String baseName, Map<Integer, Node> graphNodes) 
            throws IOException {
        String fileName = baseName + "_atree.dat";
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            
            long fileSize = channel.size();
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
            mbb.order(ByteOrder.nativeOrder());
            
            // Read header
            mbb.getInt(); // numNodes - not needed
            int numRoots = mbb.getInt();
            
            // Read root offsets
            List<Long> rootOffsets = new ArrayList<>();
            for (int i = 0; i < numRoots; i++) {
                rootOffsets.add(mbb.getLong());
            }
            
            // Load only root nodes (without children)
            List<ATreeNode> roots = new ArrayList<>();
            for (Long rootOffset : rootOffsets) {
                ATreeNode root = loadSingleNodeLazy(mbb, rootOffset, baseName, graphNodes);
                if (root != null) {
                    roots.add(root);
                }
            }
            
            return roots;
        }
    }
    
    /**
     * Load a single node from the file without loading its children.
     * The node is configured for lazy loading of children.
     * 
     * @param mbb Memory-mapped buffer positioned at file start
     * @param offset Offset of the node to load
     * @param baseName Base name for the file (stored in node for lazy loading)
     * @param graphNodes Map of node IDs to Node objects
     * @return ATreeNode configured for lazy loading of children
     */
    private static ATreeNode loadSingleNodeLazy(MappedByteBuffer mbb, long offset,
                                                String baseName, Map<Integer, Node> graphNodes) {
        // Position at node start
        mbb.position((int) offset);
        
        // Read fixed-size portion
        int edgeSrcId = mbb.getInt();
        int edgeDstId = mbb.getInt();
        int edgeWeight = mbb.getInt();
        int cost = mbb.getInt();
        mbb.getLong(); // parentOffset - skip for roots
        int numChildren = mbb.getInt();
        
        // Skip children offsets (we'll load them lazily)
        mbb.position(mbb.position() + numChildren * 8);
        
        // Read contracted edges
        int numContractedEdges = mbb.getInt();
        List<Edge> contractedEdges = new ArrayList<>();
        
        for (int i = 0; i < numContractedEdges; i++) {
            int srcId = mbb.getInt();
            int dstId = mbb.getInt();
            int weight = mbb.getInt();
            
            Node src = graphNodes.get(srcId);
            Node dst = graphNodes.get(dstId);
            
            if (src != null && dst != null) {
                contractedEdges.add(new Edge(src, dst, weight));
            }
        }
        
        // Create edge for this node
        Edge edge = null;
        if (edgeSrcId != -1 && edgeDstId != -1) {
            Node src = graphNodes.get(edgeSrcId);
            Node dst = graphNodes.get(edgeDstId);
            if (src != null && dst != null) {
                edge = new Edge(src, dst, edgeWeight);
            }
        }
        
        // Create node with lazy loading support
        boolean isSimpleNode = (numContractedEdges == 0);
        return new ATreeNode(edge, cost, isSimpleNode,
                           contractedEdges.isEmpty() ? null : contractedEdges,
                           offset, baseName, graphNodes);
    }
    
    /**
     * Load the children of a lazy-loaded node.
     * This is called automatically when getATreeChildren() is first accessed on a lazy node.
     * 
     * @param parent The parent node whose children should be loaded
     * @param baseName Base name for the file
     * @param parentOffset Offset of the parent node in the file
     * @param graphNodes Map of node IDs to Node objects
     * @throws IOException if file operations fail
     */
    public static void loadChildren(ATreeNode parent, String baseName, long parentOffset,
                                   Map<Integer, Node> graphNodes) throws IOException {
        String fileName = baseName + "_atree.dat";
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            
            long fileSize = channel.size();
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
            mbb.order(ByteOrder.nativeOrder());
            
            // Position at parent node
            mbb.position((int) parentOffset);
            
            // Skip to children section
            mbb.position(mbb.position() + 16); // skip edge data (12) + cost (4)
            mbb.getLong(); // skip parent offset
            int numChildren = mbb.getInt();
            
            // Read child offsets
            List<Long> childOffsets = new ArrayList<>();
            for (int i = 0; i < numChildren; i++) {
                childOffsets.add(mbb.getLong());
            }
            
            // Load each child as a lazy node
            List<ATreeNode> children = new ArrayList<>();
            for (Long childOffset : childOffsets) {
                ATreeNode child = loadSingleNodeLazy(mbb, childOffset, baseName, graphNodes);
                if (child != null) {
                    child.setParent(parent);
                    children.add(child);
                }
            }
            
            // Set children on parent
            parent.setChildren(children);
        }
    }
    
    /**
     * Find the offset of a node in the loaded nodes map.
     */
    private static long findNodeOffset(Map<Long, ATreeNode> loadedNodes, ATreeNode node) {
        for (Map.Entry<Long, ATreeNode> entry : loadedNodes.entrySet()) {
            if (entry.getValue() == node) {
                return entry.getKey();
            }
        }
        return -1;
    }
    
    /**
     * Recursively load all nodes reachable from a root, creating node objects without links.
     */
    private static void loadNodesFromRoot(MappedByteBuffer mbb, long offset, 
                                         Map<Integer, Node> graphNodes,
                                         Map<Long, ATreeNode> loadedNodes) {
        if (loadedNodes.containsKey(offset)) {
            return; // Already loaded
        }
        
        // Position at node start
        mbb.position((int) offset);
        
        // Read fixed-size portion
        int edgeSrcId = mbb.getInt();
        int edgeDstId = mbb.getInt();
        int edgeWeight = mbb.getInt();
        int cost = mbb.getInt();
        mbb.getLong(); // parentOffset - not used here, we link separately
        int numChildren = mbb.getInt();
        
        // Read children offsets
        List<Long> childOffsets = new ArrayList<>();
        for (int i = 0; i < numChildren; i++) {
            childOffsets.add(mbb.getLong());
        }
        
        // Read contracted edges
        int numContractedEdges = mbb.getInt();
        List<Edge> contractedEdges = new ArrayList<>();
        
        for (int i = 0; i < numContractedEdges; i++) {
            int srcId = mbb.getInt();
            int dstId = mbb.getInt();
            int weight = mbb.getInt();
            
            Node src = graphNodes.get(srcId);
            Node dst = graphNodes.get(dstId);
            
            if (src != null && dst != null) {
                contractedEdges.add(new Edge(src, dst, weight));
            }
        }
        
        // Create edge for this node
        Edge edge = null;
        if (edgeSrcId != -1 && edgeDstId != -1) {
            Node src = graphNodes.get(edgeSrcId);
            Node dst = graphNodes.get(edgeDstId);
            if (src != null && dst != null) {
                edge = new Edge(src, dst, edgeWeight);
            }
        }
        
        // Create node (without parent and children initially)
        boolean isSimpleNode = (numContractedEdges == 0);
        ATreeNode node = new ATreeNode(edge, cost, isSimpleNode, 
                                      contractedEdges.isEmpty() ? null : contractedEdges);
        
        // Store in cache
        loadedNodes.put(offset, node);
        
        // Recursively load children
        for (Long childOffset : childOffsets) {
            loadNodesFromRoot(mbb, childOffset, graphNodes, loadedNodes);
        }
    }
    
    /**
     * Link parent-child relationships for a single node (non-recursive).
     */
    private static void linkSingleNodeRelationships(MappedByteBuffer mbb, long offset,
                                                   ATreeNode node,
                                                   Map<Long, ATreeNode> loadedNodes) {
        if (node == null) {
            return;
        }
        
        // Position at node start
        mbb.position((int) offset);
        
        // Skip to parent offset field (skip edge data + cost)
        mbb.position(mbb.position() + 16); // skip edge_src, edge_dst, edge_weight, cost
        
        long parentOffset = mbb.getLong();
        int numChildren = mbb.getInt();
        
        // Set parent ONLY if not already set (avoid UnsupportedOperationException)
        if (parentOffset != -1 && node.getParent() == null) {
            ATreeNode parent = loadedNodes.get(parentOffset);
            if (parent != null) {
                node.setParent(parent);
            }
        }
        
        // Read and set children
        List<ATreeNode> children = new ArrayList<>();
        for (int i = 0; i < numChildren; i++) {
            long childOffset = mbb.getLong();
            ATreeNode child = loadedNodes.get(childOffset);
            if (child != null) {
                children.add(child);
                // Set child's parent to this node ONLY if not already set
                if (child.getParent() == null) {
                    child.setParent(node);
                }
            }
        }
        node.setChildren(children);
    }
}