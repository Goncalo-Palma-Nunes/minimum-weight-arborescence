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
 *    [num_nodes (4 bytes), mlst_length (4 bytes), sequence_type (1 byte)]
 * 
 * For each node (ordered by node ID from 0 to maxNodeId):
 *    [mlst_data (variable size based on type), incoming_edge_offset (8 bytes)]
 * 
 * Sequence types:
 *    0 = AllelicProfile (1 byte per element - Character)
 *    1 = SequenceTypingData (4 bytes per element - Integer)
 * 
 * Node IDs are implicit based on position in the file.
 */
public class NodeIndexMapper {

    private static final int HEADER_SIZE = 2 * Integer.BYTES + 1; // num_nodes + mlst_length + sequence_type
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
        // Find max node ID to determine array size
        int maxNodeId = -1;
        Map<Integer, Node> nodeMap = new HashMap<>();
        for (Node node : nodes) {
            maxNodeId = Math.max(maxNodeId, node.getId());
            nodeMap.put(node.getId(), node);
        }
        
        // Detect sequence type
        byte sequenceType = detectSequenceType(nodes);
        int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Integer.BYTES;
        
        // Calculate file size: header + entries
        int entrySize = mlstLength * bytesPerElement + Long.BYTES;
        long fileSize = HEADER_SIZE + (long) (maxNodeId + 1) * entrySize;
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            
            raf.setLength(fileSize);
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
            mbb.order(ByteOrder.nativeOrder());
            
            // Write header
            mbb.putInt(nodes.size());     // num_nodes
            mbb.putInt(mlstLength);       // mlst_length (number of elements)
            mbb.put(sequenceType);        // sequence_type
            
            // Write data for each node ID from 0 to maxNodeId
            for (int nodeId = 0; nodeId <= maxNodeId; nodeId++) {
                Node node = nodeMap.get(nodeId);
                
                // Write MLST data
                if (node != null && node.getMLSTdata() != null) {
                    byte[] mlstBytes = sequenceToBytes(node, mlstLength, sequenceType);
                    mbb.put(mlstBytes);
                } else {
                    // Write zeros for missing nodes
                    for (int i = 0; i < mlstLength * bytesPerElement; i++) {
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
            mbb.getInt(); // skip num_nodes
            int mlstLength = mbb.getInt();
            byte sequenceType = mbb.get();
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Integer.BYTES;
            
            // Calculate maxNodeId from file size
            int entrySize = mlstLength * bytesPerElement + Long.BYTES;
            int maxNodeId = (int) ((fileSize - HEADER_SIZE) / entrySize) - 1;
            
            // Read data for each node ID
            for (int nodeId = 0; nodeId <= maxNodeId; nodeId++) {
                // Read MLST data
                byte[] mlstBytes = new byte[mlstLength * bytesPerElement];
                mbb.get(mlstBytes);
                
                // Read incoming edge offset (we don't use it during loading, but consume it)
                long offset = mbb.getLong();
                
                // Check if node has data (non-zero bytes)
                boolean hasData = false;
                for (byte b : mlstBytes) {
                    if (b != 0) {
                        hasData = true;
                        break;
                    }
                }
                
                // Only create nodes that have MLST data or have incoming edges
                if (hasData || offset >= 0) {
                    nodeMap.put(nodeId, createNodeFromBytes(mlstBytes, sequenceType, nodeId, mlstLength));
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
            
            // Read sequence type from header
            MappedByteBuffer headerReadMbb = channel.map(FileChannel.MapMode.READ_ONLY, 2 * Integer.BYTES, 1);
            byte sequenceType = headerReadMbb.get();
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Integer.BYTES;
            
            // Append MLST data and offset at the end of file
            long position = channel.size();
            int dataSize = mlstLength * bytesPerElement;
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, position, dataSize + Long.BYTES);
            mbb.order(ByteOrder.nativeOrder());
            
            byte[] mlstBytes = sequenceToBytes(node, mlstLength, sequenceType);
            mbb.put(mlstBytes);
            
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
            
            // Read mlstLength and sequenceType from header
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            headerMbb.getInt(); // skip num_nodes
            int mlstLength = headerMbb.getInt();
            byte sequenceType = headerMbb.get();
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Integer.BYTES;
            
            // Calculate position of the offset field for this node
            int entrySize = mlstLength * bytesPerElement + Long.BYTES;
            long position = HEADER_SIZE + (long) nodeId * entrySize + (mlstLength * bytesPerElement);

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
            
            // Read mlstLength and sequenceType from header once
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            headerMbb.getInt(); // skip num_nodes
            int mlstLength = headerMbb.getInt();
            byte sequenceType = headerMbb.get();
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Integer.BYTES;
            int entrySize = mlstLength * bytesPerElement + Long.BYTES;

            for (Map.Entry<Integer, Long> entry : updatedOffsets.entrySet()) {
                int nodeId = entry.getKey();
                long newOffset = entry.getValue();
                
                long position = HEADER_SIZE + (long) nodeId * entrySize + (mlstLength * bytesPerElement);

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

    /**
     * Remove a node from the memory-mapped file and places the last node entry into its position.
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
            
            // Read mlstLength and sequenceType from header
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            int numNodes = headerMbb.getInt();
            int mlstLength = headerMbb.getInt();
            byte sequenceType = headerMbb.get();
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Integer.BYTES;
            int entrySize = mlstLength * bytesPerElement + Long.BYTES;
            int maxNodeId = (int) ((channel.size() - HEADER_SIZE) / entrySize) - 1;

            int nodeIdToRemove = node.getId();
            if (nodeIdToRemove < 0 || nodeIdToRemove > maxNodeId) {
                throw new IOException("Node ID " + nodeIdToRemove + " out of range");
            }

            if (nodeIdToRemove != maxNodeId) {
                // Move last node entry into the removed node's position
                long lastNodePosition = HEADER_SIZE + (long) maxNodeId * entrySize;
                long removeNodePosition = HEADER_SIZE + (long) nodeIdToRemove * entrySize;

                MappedByteBuffer lastNodeMbb = channel.map(FileChannel.MapMode.READ_ONLY, lastNodePosition, entrySize);
                MappedByteBuffer removeNodeMbb = channel.map(FileChannel.MapMode.READ_WRITE, removeNodePosition, entrySize);

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
