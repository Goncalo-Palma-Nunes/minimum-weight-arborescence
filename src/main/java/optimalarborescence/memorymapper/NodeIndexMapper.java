package optimalarborescence.memorymapper;

import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Node;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The NodeIndexMapper class offers several static methods to save and load a graph's 
 * nodes to and from memory-mapped files.
 * <p>
 * 
 * The node index file represents an array of node indices, where the first element
 * indicates the number of nodes, the second element the MLST data length,
 * and the remaining elements represent the node IDs.
 * <p>
 * 
 * Additionally, the NodeIndexMapper saves each node's MLST data and an
 * offset to the start of its incoming edges in a separate memory-mapped file.
 * 
 * File Formats:
 * 
 * 1. Node Index File (using IntArrayMapper):
 *    [num_nodes (4 bytes), mlst_length (4 bytes), node_id_0 (4 bytes), node_id_1 (4 bytes), ...]
 * 
 * 2. MLST Data File:
 *    For each node (ordered by node ID from 0 to maxNodeId):
 *    [mlst_data (mlst_length bytes), incoming_edge_offset (8 bytes)]
 */
public class NodeIndexMapper {

    private static final int META_DATA_SIZE = 2; 

    /**
     * Save graph nodes to memory-mapped files.
     * 
     * @param nodes List of nodes to save
     * @param mlstLength Fixed length of MLST data for all nodes
     * @param incomingEdgeOffsets Map of node ID to byte offset of first incoming edge
     * @param nodeIndexFile Path to the node index file
     * @param mlstDataFile Path to the MLST data file
     * @throws IOException if file operations fail
     */
    public static void saveGraph(List<Node> nodes, int mlstLength, Map<Integer, Long> incomingEdgeOffsets, 
                                  String nodeIndexFile, String mlstDataFile) throws IOException {
        // Find max node ID to determine array size
        int maxNodeId = -1;
        Map<Integer, Node> nodeMap = new HashMap<>();
        for (Node node : nodes) {
            maxNodeId = Math.max(maxNodeId, node.getID());
            nodeMap.put(node.getID(), node);
        }
        
        // Save node index array (metadata + node IDs)
        int[] nodeIndices = new int[maxNodeId + 1 + META_DATA_SIZE];
        nodeIndices[0] = nodes.size();
        nodeIndices[1] = mlstLength;
        
        // Populate with node IDs (using -1 for missing nodes)
        for (int i = 0; i <= maxNodeId; i++) {
            nodeIndices[i + META_DATA_SIZE] = nodeMap.containsKey(i) ? i : -1;
        }
        
        IntArrayMapper.saveArrayToMappedFile(nodeIndices, nodeIndexFile);
        
        // Save MLST data and offsets
        saveMlstDataToMappedFile(nodeMap, maxNodeId, mlstLength, incomingEdgeOffsets, mlstDataFile);
    }

    /**
     * Save graph using Graph object.
     * 
     * @param graph Graph to save
     * @param mlstLength Fixed length of MLST data
     * @param incomingEdgeOffsets Map of node ID to incoming edge offset
     * @param nodeIndexFile Path to node index file
     * @param mlstDataFile Path to MLST data file
     * @throws IOException if file operations fail
     */
    public static void saveGraph(Graph graph, int mlstLength, Map<Integer, Long> incomingEdgeOffsets,
                                  String nodeIndexFile, String mlstDataFile) throws IOException {
        saveGraph(graph.getNodes(), mlstLength, incomingEdgeOffsets, nodeIndexFile, mlstDataFile);
    }
    
    /**
     * Save MLST data and incoming edge offsets to a memory-mapped file.
     * Each node gets a fixed-size entry: mlst_data (mlstLength bytes) + offset (8 bytes)
     * 
     * @param nodeMap Map of node ID to Node
     * @param maxNodeId Maximum node ID
     * @param mlstLength Fixed length for MLST data
     * @param incomingEdgeOffsets Map of node ID to incoming edge offset
     * @param fileName Path to output file
     * @throws IOException if file operations fail
     */
    private static void saveMlstDataToMappedFile(Map<Integer, Node> nodeMap, int maxNodeId, int mlstLength,
                                                  Map<Integer, Long> incomingEdgeOffsets, String fileName) throws IOException {
        // Calculate entry size: mlst_data + offset
        int entrySize = mlstLength + Long.BYTES;
        long fileSize = (long) (maxNodeId + 1) * entrySize;
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            
            raf.setLength(fileSize);
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
            mbb.order(ByteOrder.nativeOrder());
            
            // Write data for each node ID from 0 to maxNodeId
            for (int nodeId = 0; nodeId <= maxNodeId; nodeId++) {
                Node node = nodeMap.get(nodeId);
                
                // Write MLST data (padded to mlstLength)
                if (node != null && node.getMLSTdata() != null) {
                    byte[] mlstBytes = node.getMLSTdata().getBytes(StandardCharsets.UTF_8);
                    
                    // Write the MLST data
                    int bytesToWrite = Math.min(mlstBytes.length, mlstLength);
                    mbb.put(mlstBytes, 0, bytesToWrite);
                    
                    // Pad with zeros if needed
                    for (int i = bytesToWrite; i < mlstLength; i++) {
                        mbb.put((byte) 0);
                    }
                } else {
                    // Write zeros for missing nodes
                    for (int i = 0; i < mlstLength; i++) {
                        mbb.put((byte) 0);
                    }
                }
                
                // Write incoming edge offset (-1 if no incoming edges)
                Long offset = incomingEdgeOffsets.get(nodeId);
                mbb.putLong(offset != null ? offset : -1L);
            }
            
            mbb.force();
        }
    }
    
    /**
     * Load graph nodes from memory-mapped files.
     * 
     * @param nodeIndexFile Path to node index file
     * @param mlstDataFile Path to MLST data file
     * @return Map of node ID to Node object
     * @throws IOException if file operations fail
     */
    public static Map<Integer, Node> loadNodes(String nodeIndexFile, String mlstDataFile) throws IOException {
        // Load node indices
        int[] nodeIndices = IntArrayMapper.loadArrayFromMappedFile(nodeIndexFile);
        
        if (nodeIndices.length < META_DATA_SIZE) {
            throw new IOException("Invalid node index file format");
        }
        
        int mlstLength = nodeIndices[1];
        int maxNodeId = nodeIndices.length - META_DATA_SIZE - 1;
        
        // Load MLST data and offsets
        return loadMlstDataFromMappedFile(mlstDataFile, maxNodeId, mlstLength);
    }
    
    /**
     * Load MLST data and incoming edge offsets from memory-mapped file.
     * 
     * @param fileName Path to MLST data file
     * @param maxNodeId Maximum node ID
     * @param mlstLength Fixed length of MLST data
     * @return Map of node ID to Node object
     * @throws IOException if file operations fail
     */
    private static Map<Integer, Node> loadMlstDataFromMappedFile(String fileName, int maxNodeId, int mlstLength) throws IOException {
        Map<Integer, Node> nodeMap = new HashMap<>();
        
        int entrySize = mlstLength + Long.BYTES;
        long expectedSize = (long) (maxNodeId + 1) * entrySize;
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            
            long fileSize = channel.size();
            if (fileSize != expectedSize) {
                throw new IOException("MLST data file size mismatch. Expected: " + expectedSize + ", got: " + fileSize);
            }
            
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
            mbb.order(ByteOrder.nativeOrder());
            
            // Read data for each node ID
            for (int nodeId = 0; nodeId <= maxNodeId; nodeId++) {
                // Read MLST data
                byte[] mlstBytes = new byte[mlstLength];
                mbb.get(mlstBytes);
                
                // Read incoming edge offset (we don't use it during loading, but consume it)
                long offset = mbb.getLong();
                
                // Convert MLST bytes to string (trim trailing zeros)
                String mlstData = new String(mlstBytes, StandardCharsets.UTF_8).trim();
                if (mlstData.isEmpty()) {
                    mlstData = null;
                }
                
                // Only create nodes that have MLST data or have incoming edges
                if (mlstData != null || offset >= 0) {
                    if (mlstData == null) {
                        mlstData = String.valueOf(nodeId); // Default to node ID
                    }
                    nodeMap.put(nodeId, new Node(mlstData, nodeId));
                }
            }
        }
        
        return nodeMap;
    }
    
    /**
     * Get the incoming edge offset for a specific node.
     * 
     * @param mlstDataFile Path to MLST data file
     * @param nodeId Node ID to query
     * @param mlstLength Fixed length of MLST data
     * @return Byte offset to first incoming edge, or -1 if none
     * @throws IOException if file operations fail
     */
    public static long getIncomingEdgeOffset(String mlstDataFile, int nodeId, int mlstLength) throws IOException {
        int entrySize = mlstLength + Long.BYTES;
        long position = (long) nodeId * entrySize + mlstLength; // Skip to offset field
        
        try (RandomAccessFile raf = new RandomAccessFile(mlstDataFile, "r");
             FileChannel channel = raf.getChannel()) {
            
            if (position + Long.BYTES > channel.size()) {
                throw new IOException("Node ID " + nodeId + " out of range");
            }
            
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, position, Long.BYTES);
            mbb.order(ByteOrder.nativeOrder());
            
            return mbb.getLong();
        }
    }

    /**
     * Add a single node to the memory-mapped files.
     * 
     * @param node
     * @param nodeIndexFile
     * @param mlstDataFile
     * @param mlstLength
     * @throws IOException
     */
    public static void addNode(Node node, String nodeIndexFile, String mlstDataFile, int mlstLength) throws IOException {
        // First, update the num_nodes metadata in the node index file
        int[] currentIndices = IntArrayMapper.loadArrayFromMappedFile(nodeIndexFile);
        int currentNumNodes = currentIndices[0];
        IntArrayMapper.saveElementToFileAtPosition(nodeIndexFile, currentNumNodes + 1, 0);
        
        // Append the new node ID
        IntArrayMapper.appendElementToFile(nodeIndexFile, node.getID());

        // Append MLST data and offset
        try (RandomAccessFile raf = new RandomAccessFile(mlstDataFile, "rw");
            FileChannel channel = raf.getChannel()) {
            long position = channel.size();
            String mlstData = node.getMLSTdata();
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, position, mlstLength + Long.BYTES);
            mbb.order(ByteOrder.nativeOrder());
            mbb.put(mlstData.getBytes(StandardCharsets.UTF_8));
            mbb.putLong(-1L); // New node has no incoming edges initially
            mbb.force();
        }
    }

    /**
     * Update the incoming edge offset for a specific node.
     * 
     * @param mlstDataFile
     * @param nodeId
     * @param mlstLength
     * @param newOffset
     * @throws IOException
     */
    public static void updateIncomingEdgeOffset(String mlstDataFile, int nodeId, int mlstLength, long newOffset) throws IOException {
        int entrySize = mlstLength + Long.BYTES;
        long position = (long) nodeId * entrySize + mlstLength; // Skip to offset field

        try (RandomAccessFile raf = new RandomAccessFile(mlstDataFile, "rw");
             FileChannel channel = raf.getChannel()) {

            if (position + Long.BYTES > channel.size()) { 
                throw new IOException("Node ID " + nodeId + " out of range"); 
            }

            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, position, Long.BYTES);
            mbb.order(ByteOrder.nativeOrder());
            mbb.putLong(newOffset);
            mbb.force();
        }
    }

    public static void updateIncomingEdgeOffsets(String mlstDataFile, int mlstLength, Map<Integer, Long> updatedOffsets) throws IOException {
        
        try (RandomAccessFile raf = new RandomAccessFile(mlstDataFile, "rw");
            FileChannel channel = raf.getChannel()) {

            for (Map.Entry<Integer, Long> entry : updatedOffsets.entrySet()) {
                int nodeId = entry.getKey();
                long newOffset = entry.getValue();
                
                int entrySize = mlstLength + Long.BYTES;
                long position = (long) nodeId * entrySize + mlstLength; // Skip to offset field

                if (position + Long.BYTES > channel.size()) { 
                    throw new IOException("Node ID " + nodeId + " out of range"); 
                }

                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, position, Long.BYTES);
                mbb.order(ByteOrder.nativeOrder());
                mbb.putLong(newOffset);
                mbb.force();
            }
        }
    }
}
