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
 * The NodeIndexMapper class offers several methods to save and load a graph's 
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
 *    1 = SequenceTypingData (8 bytes per element - Long)
 */
public class NodeIndexMapper {

    private static final int HEADER_SIZE = 2 * Integer.BYTES + 1; // num_nodes + mlst_length + sequence_type
    private static final int NODE_ID_BYTES = Integer.BYTES; // 4 bytes for node ID
    private static final byte SEQUENCE_TYPE_ALLELIC_PROFILE = 0;
    private static final byte SEQUENCE_TYPE_TYPING_DATA = 1;
    
    // Chunked mapping constants to support files > 2GB (Java MappedByteBuffer limit)
    private static final long MAX_MAPPING_SIZE = 1_500_000_000L; // 1.5GB safe limit per mapping
    
    /**
     * Calculate which region a node belongs to based on entry size.
     */
    private static long getNodePosition(int nodeIndex, int entrySize) {
        return HEADER_SIZE + (long)nodeIndex * entrySize;
    }
    
    /**
     * Calculate the start position and size for mapping a chunk of nodes.
     */
    private static long[] getChunkBounds(int startNodeIndex, int numNodesToMap, int entrySize, long fileSize) {
        long startPos = getNodePosition(startNodeIndex, entrySize);
        long maxSize = (long)numNodesToMap * entrySize;
        long actualSize = Math.min(maxSize, fileSize - startPos);
        return new long[]{startPos, actualSize};
    }

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
     * Helper method to convert node sequence to bytes based on its sequence type.
     */
    private static byte[] sequenceToBytes(Node node, int mlstLength, byte sequenceType) {
        int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Long.BYTES;
        byte[] bytes = new byte[mlstLength * bytesPerElement];
        
        if (node.getMLSTdata() instanceof AllelicProfile) {
            AllelicProfile profile = (AllelicProfile) node.getMLSTdata();
            for (int i = 0; i < Math.min(mlstLength, profile.getLength()); i++) {
                bytes[i] = (byte) profile.getElementAt(i).charValue();
            }
        } else if (node.getMLSTdata() instanceof SequenceTypingData) {
            SequenceTypingData typingData = (SequenceTypingData) node.getMLSTdata();
            for (int i = 0; i < Math.min(mlstLength, typingData.getLength()); i++) {
                long value = typingData.getElementAt(i);
                int offset = i * Long.BYTES;
                bytes[offset] = (byte) (value >> 56);
                bytes[offset + 1] = (byte) (value >> 48);
                bytes[offset + 2] = (byte) (value >> 40);
                bytes[offset + 3] = (byte) (value >> 32);
                bytes[offset + 4] = (byte) (value >> 24);
                bytes[offset + 5] = (byte) (value >> 16);
                bytes[offset + 6] = (byte) (value >> 8);
                bytes[offset + 7] = (byte) value;
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
            // SequenceTypingData: 8 bytes per long
            int numElements = mlstLength;
            Long[] data = new Long[numElements];
            for (int i = 0; i < numElements; i++) {
                int offset = i * Long.BYTES;
                data[i] = ((long)(bytes[offset] & 0xFF) << 56) |
                         ((long)(bytes[offset + 1] & 0xFF) << 48) |
                         ((long)(bytes[offset + 2] & 0xFF) << 40) |
                         ((long)(bytes[offset + 3] & 0xFF) << 32) |
                         ((long)(bytes[offset + 4] & 0xFF) << 24) |
                         ((long)(bytes[offset + 5] & 0xFF) << 16) |
                         ((long)(bytes[offset + 6] & 0xFF) << 8) |
                         (long)(bytes[offset + 7] & 0xFF);
            }
            return new Node(new SequenceTypingData(data, numElements), nodeId);
        }
    } 

    /**
     * Save graph's nodes to a memory-mapped file.
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
        int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Long.BYTES;
        
        // Calculate file size: header + entries
        // Each entry: node_id (4 bytes) + mlst_data + incoming_edge_offset (8 bytes)
        int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement + Long.BYTES;
        long fileSize = HEADER_SIZE + (long) nodes.size() * entrySize;
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            
            raf.setLength(fileSize);
            
            // Write header
            MappedByteBuffer headerBuf = channel.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_SIZE);
            headerBuf.order(ByteOrder.nativeOrder());
            headerBuf.putInt(nodes.size());     // num_nodes
            headerBuf.putInt(mlstLength);       // mlst_length (number of elements)
            headerBuf.put(sequenceType);        // sequence_type
            headerBuf.force();
            
            // Write nodes in chunks to avoid exceeding 2GB limit
            int nodesPerChunk = (int)(MAX_MAPPING_SIZE / entrySize);
            if (nodesPerChunk == 0) nodesPerChunk = 1; // Handle extremely large entries
            
            for (int i = 0; i < nodes.size(); i += nodesPerChunk) {
                int endIdx = Math.min(i + nodesPerChunk, nodes.size());
                int chunkSize = endIdx - i;
                
                long[] bounds = getChunkBounds(i, chunkSize, entrySize, fileSize);
                long position = bounds[0];
                long size = bounds[1];
                
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, position, size);
                mbb.order(ByteOrder.nativeOrder());
                
                // Write data for nodes in this chunk
                for (int j = i; j < endIdx; j++) {
                    Node node = nodes.get(j);
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
    }

    /**
     * Save graph using Graph object.
     * 
     * Deprecated method kept around for the unit tests.
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
            
            // Read header
            MappedByteBuffer headerBuf = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerBuf.order(ByteOrder.nativeOrder());
            int numNodes = headerBuf.getInt();
            int mlstLength = headerBuf.getInt();
            byte sequenceType = headerBuf.get();
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Long.BYTES;
            int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement + Long.BYTES;
            
            // Read nodes in chunks to avoid exceeding 2GB limit
            int nodesPerChunk = (int)(MAX_MAPPING_SIZE / entrySize);
            if (nodesPerChunk == 0) nodesPerChunk = 1;
            
            for (int i = 0; i < numNodes; i += nodesPerChunk) {
                int endIdx = Math.min(i + nodesPerChunk, numNodes);
                int chunkSize = endIdx - i;
                
                long[] bounds = getChunkBounds(i, chunkSize, entrySize, fileSize);
                long position = bounds[0];
                long size = bounds[1];
                
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, position, size);
                mbb.order(ByteOrder.nativeOrder());
                
                // Read data for each node entry in this chunk
                for (int j = i; j < endIdx; j++) {
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
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Long.BYTES;
            
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
     * Get incoming edge offsets for multiple nodes in a single batch operation.
     * 
     * @param fileName Path to the node data file
     * @param nodeIds Set of node IDs to query
     * @return Map of node ID to incoming edge offset (-1 if node has no incoming edges)
     * @throws IOException if file operations fail
     */
    public static Map<Integer, Long> getIncomingEdgeOffsetsBatch(String fileName, Set<Integer> nodeIds) throws IOException {
        Map<Integer, Long> result = new HashMap<>();
        
        if (nodeIds == null || nodeIds.isEmpty()) {
            return result;
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
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
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Long.BYTES;
            int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement + Long.BYTES;
            
            // Read nodes in chunks to avoid exceeding 2GB limit per mapping
            int nodesPerChunk = (int)(MAX_MAPPING_SIZE / entrySize);
            if (nodesPerChunk == 0) nodesPerChunk = 1;
            
            long fileSize = channel.size();
            
            // Scan through and collect offsets for requested nodes
            for (int i = 0; i < numNodes; i += nodesPerChunk) {
                int endIdx = Math.min(i + nodesPerChunk, numNodes);
                int chunkSize = endIdx - i;
                
                long[] bounds = getChunkBounds(i, chunkSize, entrySize, fileSize);
                long position = bounds[0];
                long size = bounds[1];
                
                MappedByteBuffer dataMbb = channel.map(FileChannel.MapMode.READ_ONLY, position, size);
                dataMbb.order(ByteOrder.nativeOrder());
                
                // Process nodes in this chunk
                for (int j = i; j < endIdx; j++) {
                    int offsetInBuffer = (j - i) * entrySize;
                    int currentNodeId = dataMbb.getInt(offsetInBuffer);
                    
                    if (nodeIds.contains(currentNodeId)) {
                        int offsetFieldPos = offsetInBuffer + NODE_ID_BYTES + mlstLength * bytesPerElement;
                        long edgeOffset = dataMbb.getLong(offsetFieldPos);
                        result.put(currentNodeId, edgeOffset);
                        
                        // Early exit if we found all requested nodes
                        if (result.size() == nodeIds.size()) {
                            return result;
                        }
                    }
                }
            }
        }
        
        return result;
    }

    /**
     * Add multiple nodes to the memory-mapped file in a single batch operation.
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
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Long.BYTES;
            
            // Calculate entry size and total size needed
            int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement + Long.BYTES;
            long position = channel.size();
            long totalSize = (long) nodes.size() * entrySize;
            
            // Pre-allocate file space to avoid filesystem reallocation overhead
            raf.setLength(position + totalSize);
            
            // Write nodes in chunks to avoid exceeding 2GB limit
            int nodesPerChunk = (int)(MAX_MAPPING_SIZE / entrySize);
            if (nodesPerChunk == 0) nodesPerChunk = 1;
            
            for (int i = 0; i < nodes.size(); i += nodesPerChunk) {
                int endIdx = Math.min(i + nodesPerChunk, nodes.size());
                int chunkSize = endIdx - i;
                long chunkBytes = (long)chunkSize * entrySize;
                
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, 
                                                    position + (long)i * entrySize, 
                                                    chunkBytes);
                mbb.order(ByteOrder.nativeOrder());
                
                // Write nodes in this chunk
                for (int j = i; j < endIdx; j++) {
                    Node node = nodes.get(j);
                    mbb.putInt(node.getId());
                    byte[] mlstBytes = sequenceToBytes(node, mlstLength, sequenceType);
                    mbb.put(mlstBytes);
                    mbb.putLong(-1L); // Initial incoming edge offset
                }
                
                mbb.force();
            }
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
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Long.BYTES;
            
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
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Long.BYTES;
            
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
     * Build a complete in-memory index of node ID to file position (byte offset).
     * This index can be reused for multiple update operations to avoid repeated file scans.
     * 
     * @param fileName Path to the node data file
     * @return Map of node ID to byte position in file where that node's data starts
     * @throws IOException if file operations fail
     */
    public static Map<Integer, Long> buildNodePositionIndex(String fileName) throws IOException {
        Map<Integer, Long> index = new HashMap<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
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
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Long.BYTES;
            int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement + Long.BYTES;
            
            // Read nodes in chunks to avoid exceeding 2GB limit
            int nodesPerChunk = (int)(MAX_MAPPING_SIZE / entrySize);
            if (nodesPerChunk == 0) nodesPerChunk = 1;
            
            long fileSize = channel.size();
            
            for (int i = 0; i < numNodes; i += nodesPerChunk) {
                int endIdx = Math.min(i + nodesPerChunk, numNodes);
                int chunkSize = endIdx - i;
                
                long[] bounds = getChunkBounds(i, chunkSize, entrySize, fileSize);
                long chunkStartPosition = bounds[0];
                long size = bounds[1];
                
                MappedByteBuffer dataMbb = channel.map(FileChannel.MapMode.READ_ONLY, chunkStartPosition, size);
                dataMbb.order(ByteOrder.nativeOrder());
                
                // Read node IDs and build index
                for (int j = i; j < endIdx; j++) {
                    int offsetInBuffer = (j - i) * entrySize;
                    int nodeId = dataMbb.getInt(offsetInBuffer);
                    long filePosition = chunkStartPosition + offsetInBuffer;
                    index.put(nodeId, filePosition);
                }
            }
        }
        
        return index;
    }

    /**
     * Update multiple incoming edge offsets using a position index (buildNodePositionIndex()).
     * 
     * @param fileName Path to the node data file
     * @param updatedOffsets Map of node ID to new offset value
     * @param positionIndex Pre-built index from buildNodePositionIndex (or null to build on-the-fly)
     * @throws IOException if file operations fail or if any node ID is not found
     */
    public static void updateIncomingEdgeOffsetsWithIndex(String fileName, Map<Integer, Long> updatedOffsets, 
                                                           Map<Integer, Long> positionIndex) throws IOException {
        if (updatedOffsets == null || updatedOffsets.isEmpty()) {
            return;
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {

            if (channel.size() < HEADER_SIZE) {
                throw new IOException("Invalid file format: file too small for header");
            }
            
            // Read header to get mlstLength and sequenceType
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            headerMbb.getInt(); // skip numNodes
            int mlstLength = headerMbb.getInt();
            byte sequenceType = headerMbb.get();
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Long.BYTES;
            int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement + Long.BYTES;
            
            // Build index if not provided
            Map<Integer, Long> index = positionIndex;
            if (index == null) {
                index = buildNodePositionIndex(fileName);
            }
            
            // Use direct lookups to update each node's offset
            for (Map.Entry<Integer, Long> entry : updatedOffsets.entrySet()) {
                int nodeId = entry.getKey();
                long newOffset = entry.getValue();
                
                Long nodePosition = index.get(nodeId);
                if (nodePosition == null) {
                    throw new IOException("Node ID " + nodeId + " not found in file");
                }
                
                // Calculate offset field position: node position + node_id + mlst_data
                long offsetFieldPosition = nodePosition + NODE_ID_BYTES + mlstLength * bytesPerElement;
                
                // Update the offset
                MappedByteBuffer offsetMbb = channel.map(FileChannel.MapMode.READ_WRITE, offsetFieldPosition, Long.BYTES);
                offsetMbb.order(ByteOrder.nativeOrder());
                offsetMbb.putLong(newOffset);
                offsetMbb.force();
            }
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
        if (updatedOffsets == null || updatedOffsets.isEmpty()) {
            return;
        }
        
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
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Long.BYTES;
            int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement + Long.BYTES;


            int numToCheck = Math.min(updatedOffsets.size() + 1000, numNodes); // +1000 buffer for safety
            int startIndex = Math.max(0, numNodes - numToCheck);
            
            long startPosition = HEADER_SIZE + (long) startIndex * entrySize;
            long regionSize = (long) numToCheck * entrySize;
            
            if (startPosition + regionSize > channel.size()) {
                regionSize = channel.size() - startPosition;
            }
            
            MappedByteBuffer regionMbb = channel.map(FileChannel.MapMode.READ_WRITE, startPosition, regionSize);
            regionMbb.order(ByteOrder.nativeOrder());
            
            // Scan through the region and update matching nodes
            Set<Integer> foundNodes = new HashSet<>();
            int offsetInRegion = 0;
            
            for (int i = 0; i < numToCheck && offsetInRegion + entrySize <= regionSize; i++) {
                // Read node ID
                int currentNodeId = regionMbb.getInt(offsetInRegion);
                
                // Check if this node needs its offset updated
                if (updatedOffsets.containsKey(currentNodeId)) {
                    long newOffset = updatedOffsets.get(currentNodeId);
                    
                    // Update the offset field (skip node_id and mlst_data)
                    int offsetFieldPosition = offsetInRegion + NODE_ID_BYTES + mlstLength * bytesPerElement;
                    regionMbb.putLong(offsetFieldPosition, newOffset);
                    
                    foundNodes.add(currentNodeId);
                    
                    // Early exit if we found all nodes
                    if (foundNodes.size() == updatedOffsets.size()) {
                        break;
                    }
                }
                
                offsetInRegion += entrySize;
            }
            
            regionMbb.force();
            
            // If we didn't find all nodes in the previous region, use indexed approach
            if (foundNodes.size() < updatedOffsets.size()) {
                System.out.println(String.format(
                    "  NodeIndexMapper: Found only %d/%d nodes in last %d entries, using indexed lookup",
                    foundNodes.size(), updatedOffsets.size(), numToCheck));
                
                long indexBuildStart = System.currentTimeMillis();
                Map<Integer, Long> positionIndex = buildNodePositionIndex(fileName);
                long indexBuildTime = System.currentTimeMillis() - indexBuildStart;
                System.out.println(String.format("  Built position index for %d nodes in %d ms", 
                                                  positionIndex.size(), indexBuildTime));
                
                // Update remaining nodes using direct lookups
                Map<Integer, Long> remainingUpdates = new HashMap<>();
                for (Map.Entry<Integer, Long> entry : updatedOffsets.entrySet()) {
                    if (!foundNodes.contains(entry.getKey())) {
                        remainingUpdates.put(entry.getKey(), entry.getValue());
                    }
                }
                
                long updateStart = System.currentTimeMillis();
                for (Map.Entry<Integer, Long> entry : remainingUpdates.entrySet()) {
                    int nodeId = entry.getKey();
                    long newOffset = entry.getValue();
                    
                    Long nodePosition = positionIndex.get(nodeId);
                    if (nodePosition == null) {
                        throw new IOException("Node ID " + nodeId + " not found in file");
                    }
                    
                    // Calculate offset field position
                    long offsetFieldPosition = nodePosition + NODE_ID_BYTES + mlstLength * bytesPerElement;
                    
                    // Update the offset
                    MappedByteBuffer offsetMbb = channel.map(FileChannel.MapMode.READ_WRITE, offsetFieldPosition, Long.BYTES);
                    offsetMbb.order(ByteOrder.nativeOrder());
                    offsetMbb.putLong(newOffset);
                    offsetMbb.force();
                }
                long updateTime = System.currentTimeMillis() - updateStart;
                System.out.println(String.format("  Updated %d nodes using index in %d ms", 
                                                  remainingUpdates.size(), updateTime));
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
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Long.BYTES;
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

    /**
     * Remove multiple nodes from the memory-mapped file in a single batch operation.
     * 
     * @param nodes List of nodes to remove
     * @param fileName Path to the node data file
     * @throws IOException if file operations fail
     */
    public static void removeNodesBatch(List<Node> nodes, String fileName) throws IOException {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        
        // Create a set of node IDs for faster lookup
        Set<Integer> nodeIdsToRemove = new HashSet<>();
        for (Node node : nodes) {
            nodeIdsToRemove.add(node.getId());
        }
        
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
            
            int bytesPerElement = (sequenceType == SEQUENCE_TYPE_ALLELIC_PROFILE) ? 1 : Long.BYTES;
            int entrySize = NODE_ID_BYTES + mlstLength * bytesPerElement + Long.BYTES;
            
            // Calculate total data size
            long dataSize = channel.size() - HEADER_SIZE;
            
            // Map the entire data region
            MappedByteBuffer dataMbb = channel.map(FileChannel.MapMode.READ_WRITE, HEADER_SIZE, dataSize);
            dataMbb.order(ByteOrder.nativeOrder());
            
            // Compact the file by moving entries that should be kept
            int writeIndex = 0;  // Index in the buffer where we write the next kept entry
            int removedCount = 0;
            
            byte[] entryBuffer = new byte[entrySize];
            
            for (int i = 0; i < numNodes; i++) {
                int readOffset = i * entrySize;
                
                // Read the node ID
                dataMbb.position(readOffset);
                int nodeId = dataMbb.getInt();
                
                // Check if this node should be removed
                if (nodeIdsToRemove.contains(nodeId)) {
                    removedCount++;
                    continue;  // Skip this entry
                }
                
                // If write position differs from read position, we need to move the entry
                if (writeIndex != i) {
                    // Read the entire entry
                    dataMbb.position(readOffset);
                    dataMbb.get(entryBuffer);
                    
                    // Write it to the correct position
                    dataMbb.position(writeIndex * entrySize);
                    dataMbb.put(entryBuffer);
                }
                
                writeIndex++;
            }
            
            dataMbb.force();
            
            // Truncate the file to remove unused space
            long newSize = HEADER_SIZE + (long) (numNodes - removedCount) * entrySize;
            channel.truncate(newSize);
            
            // Update num_nodes in header
            headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, Integer.BYTES);
            headerMbb.order(ByteOrder.nativeOrder());
            headerMbb.position(0);
            headerMbb.putInt(numNodes - removedCount);
            headerMbb.force();
        }
    }
}
