package optimalarborescence.memorymapper;

import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Node;
import optimalarborescence.sequences.AllelicProfile;
import optimalarborescence.sequences.SequenceTypingData;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The NodeIndexMapper class offers several static methods to save and load a graph's 
 * nodes to and from a memory-mapped file.
 * <p>
 * 
 * File Format:
 * 
 * Header:
 *    [num_nodes (4 bytes), mlst_length (4 bytes), sequence_type (1 byte)]
 * 
 * For each node:
 *    [node_id (4 bytes), mlst_data (variable size based on type), incoming_edge_offset (8 bytes)]
 * 
 * Sequence types:
 *    0 = AllelicProfile (1 byte per element - Character)
 *    1 = SequenceTypingData (4 bytes per element - Integer)
 * 
 * Node IDs are now explicitly stored in the file.
 */
public class NodeIndexMapper {

    private static final int HEADER_SIZE = 2 * Integer.BYTES + 1; // num_nodes + mlst_length + sequence_type
    private static final int NODE_ID_BYTES = Integer.BYTES; // 4 bytes for node ID
    private static final byte SEQUENCE_TYPE_ALLELIC_PROFILE = 0;
    private static final byte SEQUENCE_TYPE_TYPING_DATA = 1;

    /**
     * Detect sequence type from the first non-null node.
     */
    private static byte detectSequenceType(List<Node> nodes) {
        for (Node node : nodes) {
            if (node.getMLSTdata() instanceof AllelicProfile) {
                return SEQUENCE_TYPE_ALLELIC_PROFILE;
            } else if (node.getMLSTdata() instanceof SequenceTypingData) {
                return SEQUENCE_TYPE_TYPING_DATA;
            }
        }
        return SEQUENCE_TYPE_ALLELIC_PROFILE; // Default
    }

    /**
     * Helper method to convert node sequence to bytes based on its type.
     */
    private static byte[] sequenceToBytes(Node node, int mlstLength, byte sequenceType) {
        int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Integer.BYTES;
        byte[] bytes = new byte[mlstLength * bytesPerElement];
        
        if (node.getMLSTdata() instanceof AllelicProfile) {
            AllelicProfile profile = (AllelicProfile) node.getMLSTdata();
            for (int i = 0; i < Math.min(mlstLength, profile.getLength()); i++) {
                bytes[i] = (byte) profile.getElementAt(i).charValue();
            }
        } else if (node.getMLSTdata() instanceof SequenceTypingData) {
            SequenceTypingData typingData = (SequenceTypingData) node.getMLSTdata();
            for (int i = 0; i < Math.min(mlstLength, typingData.getLength()); i++) {
                int value = typingData.getElementAt(i);
                int offset = i * Integer.BYTES;
                bytes[offset] = (byte) (value >> 24);
                bytes[offset + 1] = (byte) (value >> 16);
                bytes[offset + 2] = (byte) (value >> 8);
                bytes[offset + 3] = (byte) value;
            }
        }
        
        return bytes;
    }

    /**
     * Helper method to create a node from raw byte data based on sequence type.
     */
    private static Node createNodeFromBytes(byte[] bytes, byte sequenceType, int nodeId, int mlstLength) {
        if (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) {
            // AllelicProfile: 1 byte per character
            Character[] data = new Character[mlstLength];
            for (int i = 0; i < mlstLength; i++) {
                data[i] = (char) bytes[i];
            }
            return new Node(new AllelicProfile(data, mlstLength), nodeId);
        } else {
            // SequenceTypingData: 4 bytes per integer
            int numElements = mlstLength;
            Integer[] data = new Integer[numElements];
            for (int i = 0; i < numElements; i++) {
                int offset = i * Integer.BYTES;
                data[i] = ((bytes[offset] & 0xFF) << 24) |
                         ((bytes[offset + 1] & 0xFF) << 16) |
                         ((bytes[offset + 2] & 0xFF) << 8) |
                         (bytes[offset + 3] & 0xFF);
            }
            return new Node(new SequenceTypingData(data, numElements), nodeId);
        }
    } 

    /**
     * Save graph nodes to a memory-mapped file.
     * 
     * @param nodes List of nodes to save
     * @param mlstLength Fixed length of MLST data for all nodes (number of elements)
     * @param incomingEdgeOffsets Map of node ID to byte offset of first incoming edge
     * @param fileName Path to the output file
     * @throws IOException if file operations fail
     */
    public static void saveGraph(List<Node> nodes, int mlstLength, Map<Integer, Long> incomingEdgeOffsets, 
                                  String fileName) throws IOException {
        // Detect sequence type
        byte sequenceType = detectSequenceType(nodes);
        int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Integer.BYTES;
        
        // Calculate file size: header + entries
        // Each entry: node_id (4 bytes) + mlst_data + incoming_edge_offset (8 bytes)
        int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement + Long.BYTES;
        long fileSize = HEADER_SIZE + (long) nodes.size() * entrySize;
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            
            raf.setLength(fileSize);
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
            mbb.order(ByteOrder.nativeOrder());
            
            // Write header
            mbb.putInt(nodes.size());     // num_nodes
            mbb.putInt(mlstLength);       // mlst_length (number of elements)
            mbb.put(sequenceType);        // sequence_type
            
            // Write data for each node
            for (Node node : nodes) {
                int nodeId = node.getId();
                
                // Write node ID
                mbb.putInt(nodeId);
                
                // Write MLST data
                byte[] mlstBytes = sequenceToBytes(node, mlstLength, sequenceType);
                mbb.put(mlstBytes);
                
                // Write incoming edge offset
                long offset = incomingEdgeOffsets.getOrDefault(nodeId, -1L);
                mbb.putLong(offset);
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
     * Get the number of nodes stored in the memory-mapped file.
     * 
     * @param fileName Path to the node data file
     * @return Number of nodes
     * @throws IOException if file operations fail
     */
    public static int getNumNodes(String fileName) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            
            if (channel.size() < HEADER_SIZE) {
                throw new IOException("Invalid file format: file too small for header");
            }
            
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, Integer.BYTES);
            mbb.order(ByteOrder.nativeOrder());
            return mbb.getInt();
        }
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
            int numNodes = mbb.getInt();
            int mlstLength = mbb.getInt();
            byte sequenceType = mbb.get();
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Integer.BYTES;
            
            // Read data for each node entry
            for (int i = 0; i < numNodes; i++) {
                // Read node ID
                int nodeId = mbb.getInt();
                
                // Read MLST data
                byte[] mlstBytes = new byte[mlstLength * bytesPerElement];
                mbb.get(mlstBytes);
                
                // Read incoming edge offset (we don't use it during loading, but consume it)
                mbb.getLong();
                
                // Create node from the data
                nodeMap.put(nodeId, createNodeFromBytes(mlstBytes, sequenceType, nodeId, mlstLength));
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
            
            // Read header to get mlstLength, sequenceType, and numNodes
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            int numNodes = headerMbb.getInt();
            int mlstLength = headerMbb.getInt();
            byte sequenceType = headerMbb.get();
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Integer.BYTES;
            
            // Calculate entry size: node_id + mlst_data + offset
            int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement + Long.BYTES;
            
            // Scan through entries to find the matching node ID
            long position = HEADER_SIZE;
            for (int i = 0; i < numNodes; i++) {
                // Check if we can read the entry
                if (position + entrySize > channel.size()) {
                    break;
                }
                
                // Map the node ID field
                MappedByteBuffer entryMbb = channel.map(FileChannel.MapMode.READ_ONLY, position, Integer.BYTES);
                entryMbb.order(ByteOrder.nativeOrder());
                int currentNodeId = entryMbb.getInt();
                
                if (currentNodeId == nodeId) {
                    // Found the node, read the offset field
                    long offsetPosition = position + NODE_ID_BYTES + mlstLength * bytesPerElement;
                    MappedByteBuffer offsetMbb = channel.map(FileChannel.MapMode.READ_ONLY, offsetPosition, Long.BYTES);
                    offsetMbb.order(ByteOrder.nativeOrder());
                    return offsetMbb.getLong();
                }
                
                position += entrySize;
            }
            
            // Node not found
            throw new IOException("Node ID " + nodeId + " not found in file");
        }
    }

    /**
     * Add multiple nodes to the memory-mapped file in a single batch operation.
     * This is more efficient than calling addNode() multiple times.
     * 
     * @param nodes List of nodes to add
     * @param fileName Path to the node data file
     * @param mlstLength Fixed length of MLST data
     * @throws IOException if file operations fail
     */
    public static void addNodesBatch(List<Node> nodes, String fileName, int mlstLength) throws IOException {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            
            // Update num_nodes in header
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, Integer.BYTES);
            headerMbb.order(ByteOrder.nativeOrder());
            int currentNumNodes = headerMbb.getInt();
            headerMbb.position(0);
            headerMbb.putInt(currentNumNodes + nodes.size());
            headerMbb.force();
            
            // Read sequence type from header
            MappedByteBuffer headerReadMbb = channel.map(FileChannel.MapMode.READ_ONLY, 2 * Integer.BYTES, 1);
            byte sequenceType = headerReadMbb.get();
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Integer.BYTES;
            
            // Calculate entry size and total size needed
            int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement + Long.BYTES;
            long position = channel.size();
            long totalSize = (long) nodes.size() * entrySize;
            
            // Map the entire region for all nodes at once
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, position, totalSize);
            mbb.order(ByteOrder.nativeOrder());
            
            // Write all nodes
            for (Node node : nodes) {
                mbb.putInt(node.getId());
                byte[] mlstBytes = sequenceToBytes(node, mlstLength, sequenceType);
                mbb.put(mlstBytes);
                mbb.putLong(-1L); // Initial incoming edge offset
            }
            
            mbb.force();
        }
    }
    
    /**
     * Add a single node to the memory-mapped file.
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
            
            // Read sequence type from header
            MappedByteBuffer headerReadMbb = channel.map(FileChannel.MapMode.READ_ONLY, 2 * Integer.BYTES, 1);
            byte sequenceType = headerReadMbb.get();
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Integer.BYTES;
            
            // Append node entry at the end of file: node_id + mlst_data + offset
            long position = channel.size();
            int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement + Long.BYTES;
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, position, entrySize);
            mbb.order(ByteOrder.nativeOrder());
            
            // Write node ID
            mbb.putInt(node.getId());
            
            // Write MLST data
            byte[] mlstBytes = sequenceToBytes(node, mlstLength, sequenceType);
            mbb.put(mlstBytes);
            
            // Write incoming edge offset (-1 for new nodes)
            mbb.putLong(-1L);
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
            
            // Read header to get mlstLength, sequenceType, and numNodes
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            int numNodes = headerMbb.getInt();
            int mlstLength = headerMbb.getInt();
            byte sequenceType = headerMbb.get();
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Integer.BYTES;
            
            // Calculate entry size: node_id + mlst_data + offset
            int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement + Long.BYTES;
            
            // Scan through entries to find the matching node ID
            long position = HEADER_SIZE;
            for (int i = 0; i < numNodes; i++) {
                // Check if we can read the entry
                if (position + entrySize > channel.size()) {
                    break;
                }
                
                // Map the node ID field
                MappedByteBuffer entryMbb = channel.map(FileChannel.MapMode.READ_ONLY, position, Integer.BYTES);
                entryMbb.order(ByteOrder.nativeOrder());
                int currentNodeId = entryMbb.getInt();
                
                if (currentNodeId == nodeId) {
                    // Found the node, update the offset field
                    long offsetPosition = position + NODE_ID_BYTES + mlstLength * bytesPerElement;
                    MappedByteBuffer offsetMbb = channel.map(FileChannel.MapMode.READ_WRITE, offsetPosition, Long.BYTES);
                    offsetMbb.order(ByteOrder.nativeOrder());
                    offsetMbb.putLong(newOffset);
                    offsetMbb.force();
                    return;
                }
                
                position += entrySize;
            }
            
            // Node not found
            throw new IOException("Node ID " + nodeId + " not found in file");
        }
    }

    /**
     * Update multiple incoming edge offsets in a single operation.
     * 
     * @param fileName Path to the node data file
     * @param updatedOffsets Map of node ID to new offset value
     * @throws IOException if file operations fail or if any node ID is not found
     */
    public static void updateIncomingEdgeOffsets(String fileName, Map<Integer, Long> updatedOffsets) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {

            if (channel.size() < HEADER_SIZE) {
                throw new IOException("Invalid file format: file too small for header");
            }
            
            // Read header to get mlstLength, sequenceType, and numNodes
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            int numNodes = headerMbb.getInt();
            int mlstLength = headerMbb.getInt();
            byte sequenceType = headerMbb.get();
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Integer.BYTES;
            int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement + Long.BYTES;

            // Build a map of positions for each node ID that needs updating
            Set<Integer> foundNodes = new HashSet<>();
            long position = HEADER_SIZE;
            for (int i = 0; i < numNodes; i++) {
                if (position + entrySize > channel.size()) {
                    break;
                }
                
                // Map the node ID field
                MappedByteBuffer entryMbb = channel.map(FileChannel.MapMode.READ_ONLY, position, Integer.BYTES);
                entryMbb.order(ByteOrder.nativeOrder());
                int currentNodeId = entryMbb.getInt();
                
                // Check if this node needs its offset updated
                if (updatedOffsets.containsKey(currentNodeId)) {
                    long offsetPosition = position + NODE_ID_BYTES + mlstLength * bytesPerElement;
                    MappedByteBuffer offsetMbb = channel.map(FileChannel.MapMode.READ_WRITE, offsetPosition, Long.BYTES);
                    offsetMbb.order(ByteOrder.nativeOrder());
                    offsetMbb.putLong(updatedOffsets.get(currentNodeId));
                    offsetMbb.force();
                    foundNodes.add(currentNodeId);
                }
                
                position += entrySize;
            }
            
            // Check if any requested nodes were not found
            for (Integer nodeId : updatedOffsets.keySet()) {
                if (!foundNodes.contains(nodeId)) {
                    throw new IOException("Node ID " + nodeId + " not found in file");
                }
            }
        }
    }

    /**
     * Remove a node from the memory-mapped file by replacing it with the last entry.
     * 
     * @param node The node to remove
     * @param fileName Path to the node data file
     * @throws IOException if file operations fail
     */
    public static void removeNode(Node node, String fileName) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {

            if (channel.size() < HEADER_SIZE) {
                throw new IOException("Invalid file format: file too small for header");
            }
            
            // Read header
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            int numNodes = headerMbb.getInt();
            int mlstLength = headerMbb.getInt();
            byte sequenceType = headerMbb.get();
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Integer.BYTES;
            int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement + Long.BYTES;

            int nodeIdToRemove = node.getId();
            
            // Find the position of the node to remove
            long removePosition = -1;
            long position = HEADER_SIZE;
            int entryIndex = 0;
            
            for (int i = 0; i < numNodes; i++) {
                if (position + entrySize > channel.size()) {
                    break;
                }
                
                MappedByteBuffer entryMbb = channel.map(FileChannel.MapMode.READ_ONLY, position, Integer.BYTES);
                entryMbb.order(ByteOrder.nativeOrder());
                int currentNodeId = entryMbb.getInt();
                
                if (currentNodeId == nodeIdToRemove) {
                    removePosition = position;
                    entryIndex = i;
                    break;
                }
                
                position += entrySize;
            }
            
            if (removePosition == -1) {
                throw new IOException("Node ID " + nodeIdToRemove + " not found in file");
            }

            // If not the last entry, copy the last entry into this position
            if (entryIndex != numNodes - 1) {
                long lastNodePosition = HEADER_SIZE + (long) (numNodes - 1) * entrySize;

                MappedByteBuffer lastNodeMbb = channel.map(FileChannel.MapMode.READ_ONLY, lastNodePosition, entrySize);
                MappedByteBuffer removeNodeMbb = channel.map(FileChannel.MapMode.READ_WRITE, removePosition, entrySize);

                byte[] buffer = new byte[entrySize];
                lastNodeMbb.get(buffer);
                removeNodeMbb.put(buffer);
                removeNodeMbb.force();
            }

            // Truncate the file to remove the last node entry
            channel.truncate(channel.size() - entrySize);

            // Update num_nodes in header
            headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, Integer.BYTES);
            headerMbb.order(ByteOrder.nativeOrder());
            headerMbb.position(0);
            headerMbb.putInt(numNodes - 1);
            headerMbb.force();
        }
    }
}
