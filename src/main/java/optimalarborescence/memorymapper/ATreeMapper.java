package optimalarborescence.memorymapper;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.inference.dynamic.ATreeNode;

import java.io.File;
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
 * File Structure:
 * - {baseName}_atree_index.dat: ATree node data with parent/children/contracted edges offsets
 * - {baseName}_atree_children.dat: Variable-length children lists
 * - {baseName}_atree_contracted.dat: Variable-length contracted edge lists
 * 
 * Design Notes:
 * - Uses offsets instead of IDs for parent references (direct O(1) access)
 * - Nodes stored consecutively without gaps (no wasted space)
 * - Simple vs complex nodes distinguished by contracted_edges_offset (-1 = simple)
 * - Root nodes have parent_offset = -1
 */
public class ATreeMapper {
    
    private static final int BYTES_PER_NODE = 40;
    // Node format: [edge_src(4), edge_dst(4), edge_wt(4), cost(4), 
    //               parent_offset(8), children_offset(8), contracted_offset(8)]
    // Total: 12 + 4 + 8 + 8 + 8 = 40 bytes
    
    private static final int BYTES_PER_EDGE = 12; // source(4), dest(4), weight(4)
    
    /**
     * Save an ATree forest to memory-mapped files.
     * 
     * @param roots List of ATree root nodes
     * @param graphNodes Map of node IDs to Node objects (for edge reconstruction)
     * @param baseName Base name for output files
     * @throws IOException if file operations fail
     */
    public static void saveATreeForest(List<ATreeNode> roots, Map<Integer, Node> graphNodes, 
                                       String baseName) throws IOException {
        String indexFile = baseName + "_atree_index.dat";
        String childrenFile = baseName + "_atree_children.dat";
        String contractedFile = baseName + "_atree_contracted.dat";
        
        // Step 1: Collect all nodes in the forest using BFS
        List<ATreeNode> allNodes = new ArrayList<>();
        Map<ATreeNode, Long> nodeOffsets = new HashMap<>();
        
        Queue<ATreeNode> queue = new LinkedList<>(roots);
        while (!queue.isEmpty()) {
            ATreeNode current = queue.poll();
            if (!nodeOffsets.containsKey(current)) {
                long offset = calculateNodeOffset(allNodes.size());
                nodeOffsets.put(current, offset);
                allNodes.add(current);
                
                @SuppressWarnings("unchecked")
                List<ATreeNode> children = (List<ATreeNode>) (List<?>) current.getChildren();
                if (children != null) {
                    queue.addAll(children);
                }
            }
        }
        
        // Calculate header size (needed for absolute offsets)
        int headerSize = 8 + (roots.size() * 8);
        
        // Step 2: Write children lists and get offsets
        Map<ATreeNode, Long> childrenOffsets = writeChildrenLists(allNodes, nodeOffsets, 
                                                                   headerSize, childrenFile);
        
        // Step 3: Write contracted edges and get offsets
        Map<ATreeNode, Long> contractedOffsets = writeContractedEdges(allNodes, contractedFile);
        
        // Step 4: Write node data to index file
        writeNodeIndex(roots, allNodes, nodeOffsets, childrenOffsets, contractedOffsets, indexFile);
    }
    
    /**
     * Load an ATree forest from memory-mapped files.
     * 
     * @param baseName Base name for input files
     * @param graphNodes Map of node IDs to Node objects (for edge reconstruction)
     * @return List of ATree root nodes
     * @throws IOException if file operations fail
     */
    public static List<ATreeNode> loadATreeForest(String baseName, Map<Integer, Node> graphNodes) 
            throws IOException {
        String indexFile = baseName + "_atree_index.dat";
        String childrenFile = baseName + "_atree_children.dat";
        String contractedFile = baseName + "_atree_contracted.dat";
        
        // Read header to get root offsets
        List<Long> rootOffsets = readRootOffsets(indexFile);
        
        // Load all nodes and build the forest
        Map<Long, ATreeNode> loadedNodes = new HashMap<>();
        List<ATreeNode> roots = new ArrayList<>();
        
        for (Long rootOffset : rootOffsets) {
            ATreeNode root = loadATreeNode(rootOffset, indexFile, childrenFile, contractedFile, 
                                          graphNodes, loadedNodes);
            roots.add(root);
        }
        
        return roots;
    }
    
    /**
     * Calculate the byte offset for a node at the given index in the node array.
     */
    private static long calculateNodeOffset(int nodeIndex) {
        // Header size: num_nodes(4) + num_roots(4) + root_offsets(num_roots * 8)
        // We'll calculate this dynamically when writing
        // For now, return relative offset from start of node data
        return (long) nodeIndex * BYTES_PER_NODE;
    }
    
    /**
     * Write children lists to file and return offsets for each node.
     */
    private static Map<ATreeNode, Long> writeChildrenLists(List<ATreeNode> allNodes, 
                                                           Map<ATreeNode, Long> nodeOffsets,
                                                           int headerSize,
                                                           String fileName) throws IOException {
        Map<ATreeNode, Long> childrenOffsets = new HashMap<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            
            long currentOffset = 0;
            
            for (ATreeNode node : allNodes) {
                @SuppressWarnings("unchecked")
                List<ATreeNode> children = (List<ATreeNode>) (List<?>) node.getChildren();
                
                if (children == null || children.isEmpty()) {
                    childrenOffsets.put(node, -1L);
                    continue;
                }
                
                // Record offset for this node's children list
                childrenOffsets.put(node, currentOffset);
                
                // Calculate size: num_children(4) + child_offsets(n * 8)
                int numChildren = children.size();
                long listSize = 4 + ((long) numChildren * 8);
                
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, 
                                                   currentOffset, listSize);
                mbb.order(ByteOrder.nativeOrder());
                
                // Write number of children
                mbb.putInt(numChildren);
                
                // Write child offsets (convert relative to absolute)
                for (ATreeNode child : children) {
                    Long relativeChildOffset = nodeOffsets.get(child);
                    if (relativeChildOffset != null) {
                        long absoluteChildOffset = headerSize + relativeChildOffset;
                        mbb.putLong(absoluteChildOffset);
                    } else {
                        mbb.putLong(-1L);
                    }
                }
                
                mbb.force();
                currentOffset += listSize;
            }
            
            raf.setLength(currentOffset);
        }
        
        return childrenOffsets;
    }
    
    /**
     * Write contracted edges to file and return offsets for each node.
     */
    private static Map<ATreeNode, Long> writeContractedEdges(List<ATreeNode> allNodes, 
                                                             String fileName) throws IOException {
        Map<ATreeNode, Long> contractedOffsets = new HashMap<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            
            long currentOffset = 0;
            
            for (ATreeNode node : allNodes) {
                List<Edge> contractedEdges = node.getContractedEdges();
                
                if (node.isSimpleNode() || contractedEdges == null || contractedEdges.isEmpty()) {
                    contractedOffsets.put(node, -1L);
                    continue;
                }
                
                // Record offset for this node's contracted edges
                contractedOffsets.put(node, currentOffset);
                
                // Calculate size: num_edges(4) + edges(n * 12)
                int numEdges = contractedEdges.size();
                long listSize = 4 + ((long) numEdges * BYTES_PER_EDGE);
                
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, 
                                                   currentOffset, listSize);
                mbb.order(ByteOrder.nativeOrder());
                
                // Write number of edges
                mbb.putInt(numEdges);
                
                // Write edges
                for (Edge edge : contractedEdges) {
                    mbb.putInt(edge.getSource().getID());
                    mbb.putInt(edge.getDestination().getID());
                    mbb.putInt(edge.getWeight());
                }
                
                mbb.force();
                currentOffset += listSize;
            }
            
            raf.setLength(currentOffset);
        }
        
        return contractedOffsets;
    }
    
    /**
     * Write node index file with all node data.
     */
    private static void writeNodeIndex(List<ATreeNode> roots, List<ATreeNode> allNodes, 
                                       Map<ATreeNode, Long> nodeOffsets,
                                       Map<ATreeNode, Long> childrenOffsets,
                                       Map<ATreeNode, Long> contractedOffsets,
                                       String fileName) throws IOException {
        
        int numNodes = allNodes.size();
        int numRoots = roots.size();
        int headerSize = 8 + (numRoots * 8);
        long fileSize = headerSize + ((long) numNodes * BYTES_PER_NODE);
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            
            raf.setLength(fileSize);
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
            mbb.order(ByteOrder.nativeOrder());
            
            // Write header
            mbb.putInt(numNodes);
            mbb.putInt(numRoots);
            
            // Write root offsets (adjusted for header)
            for (ATreeNode root : roots) {
                long relativeOffset = nodeOffsets.get(root);
                long absoluteOffset = headerSize + relativeOffset;
                mbb.putLong(absoluteOffset);
            }
            
            // Write node data
            for (int i = 0; i < allNodes.size(); i++) {
                ATreeNode node = allNodes.get(i);
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
                long parentOffset = -1L;
                ATreeNode parent = node.getParent();
                if (parent != null) {
                    Long relativeParentOffset = nodeOffsets.get(parent);
                    if (relativeParentOffset != null) {
                        parentOffset = headerSize + relativeParentOffset;
                    }
                }
                mbb.putLong(parentOffset);
                
                // Write children offset
                Long childrenOffset = childrenOffsets.get(node);
                mbb.putLong(childrenOffset != null ? childrenOffset : -1L);
                
                // Write contracted edges offset
                Long contractedOffset = contractedOffsets.get(node);
                mbb.putLong(contractedOffset != null ? contractedOffset : -1L);
            }
            
            mbb.force();
        }
    }
    
    /**
     * Read root offsets from the index file header.
     */
    private static List<Long> readRootOffsets(String fileName) throws IOException {
        List<Long> rootOffsets = new ArrayList<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            
            // Read header
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, 8);
            mbb.order(ByteOrder.nativeOrder());
            
            mbb.getInt(); // numNodes - not needed here
            int numRoots = mbb.getInt();
            
            // Read root offsets
            if (numRoots > 0) {
                mbb = channel.map(FileChannel.MapMode.READ_ONLY, 8, (long) numRoots * 8);
                mbb.order(ByteOrder.nativeOrder());
                
                for (int i = 0; i < numRoots; i++) {
                    rootOffsets.add(mbb.getLong());
                }
            }
        }
        
        return rootOffsets;
    }
    
    /**
     * Load a single ATree node from file at the given offset.
     */
    private static ATreeNode loadATreeNode(long offset, String indexFile, String childrenFile,
                                          String contractedFile, Map<Integer, Node> graphNodes,
                                          Map<Long, ATreeNode> loadedNodes) throws IOException {
        
        // Check if already loaded
        if (loadedNodes.containsKey(offset)) {
            return loadedNodes.get(offset);
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(indexFile, "r");
             FileChannel channel = raf.getChannel()) {
            
            // Read node data
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, offset, BYTES_PER_NODE);
            mbb.order(ByteOrder.nativeOrder());
            
            // Read edge data
            int edgeSrcId = mbb.getInt();
            int edgeDstId = mbb.getInt();
            int edgeWeight = mbb.getInt();
            
            Edge edge = null;
            if (edgeSrcId != -1 && edgeDstId != -1) {
                Node src = graphNodes.get(edgeSrcId);
                Node dst = graphNodes.get(edgeDstId);
                if (src != null && dst != null) {
                    edge = new Edge(src, dst, edgeWeight);
                }
            }
            
            // Read cost
            int cost = mbb.getInt();
            
            // Read parent offset
            long parentOffset = mbb.getLong();
            
            // Read children offset
            long childrenOffset = mbb.getLong();
            
            // Read contracted edges offset
            long contractedOffset = mbb.getLong();
            
            // Validate offsets to catch corruption
            if (parentOffset < -1L || childrenOffset < -1L || contractedOffset < -1L) {
                throw new IOException(String.format(
                    "Invalid offsets at position %d: parent=%d, children=%d, contracted=%d",
                    offset, parentOffset, childrenOffset, contractedOffset));
            }
            
            // Determine if simple node
            boolean isSimpleNode = (contractedOffset == -1L);
            
            // Load contracted edges if this is a complex node
            List<Edge> contractedEdges = null;
            if (!isSimpleNode && contractedOffset >= 0) {
                contractedEdges = loadContractedEdges(contractedOffset, contractedFile, graphNodes);
            }
            
            // Create the node (without parent and children initially)
            // Use a dummy ID since it's not used algorithmically
            ATreeNode node = new ATreeNode(edge, cost, isSimpleNode, contractedEdges, 0);
            
            // Store in cache IMMEDIATELY to prevent infinite recursion
            loadedNodes.put(offset, node);
            
            // Load and set parent (may recursively load, but will find this node in cache)
            if (parentOffset != -1L && parentOffset >= 0) {
                ATreeNode parentNode = loadATreeNode(parentOffset, indexFile, childrenFile, 
                                                contractedFile, graphNodes, loadedNodes);
                node.setParent(parentNode);
            }
            
            // Load and set children (may recursively load, but will find this node in cache)
            if (childrenOffset != -1L) {
                List<ATreeNode> children = loadChildren(childrenOffset, childrenFile, indexFile, 
                                                       contractedFile, graphNodes, loadedNodes);
                node.setChildren(children);
            } else {
                node.setChildren(new ArrayList<>());
            }
            
            return node;
        }
    }
    
    /**
     * Load children list from file.
     */
    private static List<ATreeNode> loadChildren(long offset, String childrenFile, String indexFile,
                                                String contractedFile, Map<Integer, Node> graphNodes,
                                                Map<Long, ATreeNode> loadedNodes) throws IOException {
        List<ATreeNode> children = new ArrayList<>();
        
        File file = new File(childrenFile);
        if (!file.exists() || file.length() == 0) {
            return children; // Empty file means no children
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(childrenFile, "r");
             FileChannel channel = raf.getChannel()) {
            
            long fileSize = channel.size();
            if (offset >= fileSize) {
                return children; // Offset beyond file size
            }
            
            // Read number of children
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, offset, Math.min(4, fileSize - offset));
            mbb.order(ByteOrder.nativeOrder());
            int numChildren = mbb.getInt();
            
            if (numChildren > 0) {
                // Read child offsets
                mbb = channel.map(FileChannel.MapMode.READ_ONLY, offset + 4, 
                                 (long) numChildren * 8);
                mbb.order(ByteOrder.nativeOrder());
                
                for (int i = 0; i < numChildren; i++) {
                    long childOffset = mbb.getLong();
                    if (childOffset >= 0) {
                        ATreeNode child = loadATreeNode(childOffset, indexFile, childrenFile,
                                                       contractedFile, graphNodes, loadedNodes);
                        children.add(child);
                    }
                }
            }
        }
        
        return children;
    }
    
    /**
     * Load contracted edges list from file.
     */
    private static List<Edge> loadContractedEdges(long offset, String fileName, 
                                                  Map<Integer, Node> graphNodes) throws IOException {
        List<Edge> edges = new ArrayList<>();
        
        File file = new File(fileName);
        if (!file.exists() || file.length() == 0) {
            return edges; // Empty file means no contracted edges
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            
            long fileSize = channel.size();
            if (offset >= fileSize) {
                return edges; // Offset beyond file size
            }
            
            // Read number of edges
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, offset, 4);
            mbb.order(ByteOrder.nativeOrder());
            int numEdges = mbb.getInt();
            
            if (numEdges > 0) {
                // Read edges
                mbb = channel.map(FileChannel.MapMode.READ_ONLY, offset + 4, 
                                 (long) numEdges * BYTES_PER_EDGE);
                mbb.order(ByteOrder.nativeOrder());
                
                for (int i = 0; i < numEdges; i++) {
                    int srcId = mbb.getInt();
                    int dstId = mbb.getInt();
                    int weight = mbb.getInt();
                    
                    Node src = graphNodes.get(srcId);
                    Node dst = graphNodes.get(dstId);
                    
                    if (src != null && dst != null) {
                        edges.add(new Edge(src, dst, weight));
                    }
                }
            }
        }
        
        return edges;
    }
}
