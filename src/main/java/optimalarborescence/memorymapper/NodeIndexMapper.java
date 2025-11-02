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
 * nodes to and from a memory-mapped file.
 * <p>
 * 
 * File Format:
 * 
 * Header:
 *    [num_nodes (4 bytes), mlst_length (4 bytes)]
 * 
 * For each node (ordered by node ID from 0 to maxNodeId):
 *    [mlst_data (mlst_length bytes), incoming_edge_offset (8 bytes)]
 * 
 * Node IDs are implicit based on position in the file.
 */
public class NodeIndexMapper {

    private static final int HEADER_SIZE = 2 * Integer.BYTES; // num_nodes + mlst_length 

    /**
     * Save graph nodes to a memory-mapped file.
     * 
     * @param nodes List of nodes to save
     * @param mlstLength Fixed length of MLST data for all nodes
     * @param incomingEdgeOffsets Map of node ID to byte offset of first incoming edge
     * @param fileName Path to the output file
     * @throws IOException if file operations fail
     */
    public static void saveGraph(List<Node> nodes, int mlstLength, Map<Integer, Long> incomingEdgeOffsets, 
                                  String fileName) throws IOException {
        // Find max node ID to determine array size
        int maxNodeId = -1;
        Map<Integer, Node> nodeMap = new HashMap<>();
        for (Node node : nodes) {
            maxNodeId = Math.max(maxNodeId, node.getID());
            nodeMap.put(node.getID(), node);
        }
        
        // Calculate file size: header + entries
        int entrySize = mlstLength + Long.BYTES;
        long fileSize = HEADER_SIZE + (long) (maxNodeId + 1) * entrySize;
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            
            raf.setLength(fileSize);
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
            mbb.order(ByteOrder.nativeOrder());
            
            // Write header
            mbb.putInt(nodes.size());     // num_nodes
            mbb.putInt(mlstLength);       // mlst_length
            
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
     * Save graph using Graph object.
     * 
     * @param graph Graph to save
     * @param mlstLength Fixed length of MLST data
     * @param incomingEdgeOffsets Map of node ID to incoming edge offset
     * @param fileName Path to output file
     * @throws IOException if file operations fail
     */
    public static void saveGraph(Graph graph, int mlstLength, Map<Integer, Long> incomingEdgeOffsets,
                                  String fileName) throws IOException {
        saveGraph(graph.getNodes(), mlstLength, incomingEdgeOffsets, fileName);
    }
    

    
    /**
     * Load graph nodes from memory-mapped file.
     * 
     * @param fileName Path to the node data file
     * @return Map of node ID to Node object
     * @throws IOException if file operations fail
     */
    public static Map<Integer, Node> loadNodes(String fileName) throws IOException {
        Map<Integer, Node> nodeMap = new HashMap<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            
            long fileSize = channel.size();
            if (fileSize < HEADER_SIZE) {
                throw new IOException("Invalid file format: file too small for header");
            }
            
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
            mbb.order(ByteOrder.nativeOrder());
            
            // Read header
            mbb.getInt(); // skip num_nodes
            int mlstLength = mbb.getInt();
            
            // Calculate maxNodeId from file size
            int entrySize = mlstLength + Long.BYTES;
            int maxNodeId = (int) ((fileSize - HEADER_SIZE) / entrySize) - 1;
            
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
     * @param fileName Path to the node data file
     * @param nodeId Node ID to query
     * @return Byte offset to first incoming edge, or -1 if none
     * @throws IOException if file operations fail
     */
    public static long getIncomingEdgeOffset(String fileName, int nodeId) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            
            if (channel.size() < HEADER_SIZE) {
                throw new IOException("Invalid file format: file too small for header");
            }
            
            // Read mlstLength from header
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            headerMbb.getInt(); // skip num_nodes
            int mlstLength = headerMbb.getInt();
            
            // Calculate position of the offset field for this node
            int entrySize = mlstLength + Long.BYTES;
            long position = HEADER_SIZE + (long) nodeId * entrySize + mlstLength;
            
            if (position + Long.BYTES > channel.size()) {
                throw new IOException("Node ID " + nodeId + " out of range");
            }
            
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, position, Long.BYTES);
            mbb.order(ByteOrder.nativeOrder());
            
            return mbb.getLong();
        }
    }

    /**
     * Add a single node to the memory-mapped file.
     * Note: This appends a node entry at the end. The node ID is implicit based on position.
     * 
     * @param node The node to add
     * @param fileName Path to the node data file
     * @param mlstLength Fixed length of MLST data
     * @throws IOException if file operations fail
     */
    public static void addNode(Node node, String fileName, int mlstLength) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            
            // Update num_nodes in header
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, Integer.BYTES);
            headerMbb.order(ByteOrder.nativeOrder());
            int currentNumNodes = headerMbb.getInt();
            headerMbb.position(0);
            headerMbb.putInt(currentNumNodes + 1);
            headerMbb.force();
            
            // Append MLST data and offset at the end of file
            long position = channel.size();
            String mlstData = node.getMLSTdata();
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, position, mlstLength + Long.BYTES);
            mbb.order(ByteOrder.nativeOrder());
            
            byte[] mlstBytes = mlstData.getBytes(StandardCharsets.UTF_8);
            int bytesToWrite = Math.min(mlstBytes.length, mlstLength);
            mbb.put(mlstBytes, 0, bytesToWrite);
            
            // Pad with zeros if needed
            for (int i = bytesToWrite; i < mlstLength; i++) {
                mbb.put((byte) 0);
            }
            
            mbb.putLong(-1L); // New node has no incoming edges initially
            mbb.force();
        }
    }

    /**
     * Update the incoming edge offset for a specific node.
     * 
     * @param fileName Path to the node data file
     * @param nodeId Node ID to update
     * @param newOffset New offset value
     * @throws IOException if file operations fail
     */
    public static void updateIncomingEdgeOffset(String fileName, int nodeId, long newOffset) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {

            if (channel.size() < HEADER_SIZE) {
                throw new IOException("Invalid file format: file too small for header");
            }
            
            // Read mlstLength from header
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            headerMbb.getInt(); // skip num_nodes
            int mlstLength = headerMbb.getInt();
            
            // Calculate position of the offset field for this node
            int entrySize = mlstLength + Long.BYTES;
            long position = HEADER_SIZE + (long) nodeId * entrySize + mlstLength;

            if (position + Long.BYTES > channel.size()) { 
                throw new IOException("Node ID " + nodeId + " out of range"); 
            }

            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, position, Long.BYTES);
            mbb.order(ByteOrder.nativeOrder());
            mbb.putLong(newOffset);
            mbb.force();
        }
    }

    /**
     * Update multiple incoming edge offsets in a single operation.
     * 
     * @param fileName Path to the node data file
     * @param updatedOffsets Map of node ID to new offset value
     * @throws IOException if file operations fail
     */
    public static void updateIncomingEdgeOffsets(String fileName, Map<Integer, Long> updatedOffsets) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {

            if (channel.size() < HEADER_SIZE) {
                throw new IOException("Invalid file format: file too small for header");
            }
            
            // Read mlstLength from header once
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            headerMbb.getInt(); // skip num_nodes
            int mlstLength = headerMbb.getInt();
            
            int entrySize = mlstLength + Long.BYTES;

            for (Map.Entry<Integer, Long> entry : updatedOffsets.entrySet()) {
                int nodeId = entry.getKey();
                long newOffset = entry.getValue();
                
                long position = HEADER_SIZE + (long) nodeId * entrySize + mlstLength;

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
