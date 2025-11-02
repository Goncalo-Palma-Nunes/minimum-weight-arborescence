package optimalarborescence.memorymapper;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * EdgeListMapper provides memory-mapped file operations for storing and loading edge lists.
 * Edges are stored sorted by destination node ID to enable efficient offset tracking
 * for incoming edges.
 * 
 * File Format:
 * Each edge is stored as 3 consecutive integers (12 bytes total):
 * [source_id (4 bytes), destination_id (4 bytes), weight (4 bytes)]
 */
public class EdgeListMapper {
    
    private static final int BYTES_PER_EDGE = 12; // 3 ints per edge
    
    /**
     * Save edges to a memory-mapped file, sorted by destination node ID.
     * 
     * @param edges List of edges to save
     * @param fileName Path to the output file
     * @return Map of destination node ID to byte offset in the file (for incoming edges)
     * @throws IOException if file operations fail
     */
    public static Map<Integer, Long> saveEdgesToMappedFile(List<Edge> edges, String fileName) throws IOException {
        // Sort edges by destination node ID
        List<Edge> sortedEdges = new ArrayList<>(edges);
        sortedEdges.sort(Comparator.comparingInt(edge -> edge.getDestination().getID()));
        
        // Calculate file size
        long size = (long) sortedEdges.size() * BYTES_PER_EDGE;
        
        // Track offsets for incoming edges (by destination)
        Map<Integer, Long> incomingEdgeOffsets = new HashMap<>();
        long currentOffset = 0;
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            
            raf.setLength(size);
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, size);
            mbb.order(ByteOrder.nativeOrder());
            
            for (Edge edge : sortedEdges) {
                int destId = edge.getDestination().getID();
                
                // Track the first occurrence of each destination
                if (!incomingEdgeOffsets.containsKey(destId)) {
                    incomingEdgeOffsets.put(destId, currentOffset);
                }
                
                // Write edge data
                mbb.putInt(edge.getSource().getID());
                mbb.putInt(edge.getDestination().getID());
                mbb.putInt(edge.getWeight());
                
                currentOffset += BYTES_PER_EDGE;
            }
            
            mbb.force();
        }
        
        return incomingEdgeOffsets;
    }
    
    /**
     * Load edges from a memory-mapped file.
     * 
     * @param fileName Path to the input file
     * @param nodeMap Map of node IDs to Node objects (for reconstructing edges)
     * @return List of edges loaded from the file
     * @throws IOException if file operations fail
     */
    public static List<Edge> loadEdgesFromMappedFile(String fileName, Map<Integer, Node> nodeMap) throws IOException {
        List<Edge> edges = new ArrayList<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            
            long size = channel.size();
            if (size % BYTES_PER_EDGE != 0) {
                throw new IOException("Edge file size is not a multiple of edge size (12 bytes)");
            }
            
            int edgeCount = (int) (size / BYTES_PER_EDGE);
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
            mbb.order(ByteOrder.nativeOrder());
            
            for (int i = 0; i < edgeCount; i++) {
                int srcId = mbb.getInt();
                int dstId = mbb.getInt();
                int weight = mbb.getInt();
                
                Node src = nodeMap.get(srcId);
                Node dst = nodeMap.get(dstId);
                
                if (src != null && dst != null) {
                    edges.add(new Edge(src, dst, weight));
                }
            }
        }
        
        return edges;
    }
    
    /**
     * Load edges from a memory-mapped file, creating minimal Node objects without MLST data.
     * Use this method when you only need the graph structure and don't care about MLST data.
     * 
     * @param fileName Path to the input file
     * @return List of edges loaded from the file with minimal Node objects (MLST data = "CGCG")
     * @throws IOException if file operations fail
     */
    public static List<Edge> loadEdgesFromMappedFile(String fileName) throws IOException {
        List<Edge> edges = new ArrayList<>();
        Map<Integer, Node> nodeCache = new HashMap<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            
            long size = channel.size();
            if (size % BYTES_PER_EDGE != 0) {
                throw new IOException("Edge file size is not a multiple of edge size (12 bytes)");
            }
            
            int edgeCount = (int) (size / BYTES_PER_EDGE);
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
            mbb.order(ByteOrder.nativeOrder());
            
            for (int i = 0; i < edgeCount; i++) {
                int srcId = mbb.getInt();
                int dstId = mbb.getInt();
                int weight = mbb.getInt();
                
                // Create minimal nodes on-the-fly with dummy MLST data (reuse same instance for same ID)
                // Using "CGCG" as placeholder since Point constructor requires non-empty sequence
                // Note: MLST sequences are only relevant for the nearest neighbour algorithms
                // and distance computations. It does not matter when we just want to load edges 
                Node src = nodeCache.computeIfAbsent(srcId, id -> new Node("CGCG", id));
                Node dst = nodeCache.computeIfAbsent(dstId, id -> new Node("CGCG", id));
                
                edges.add(new Edge(src, dst, weight));
            }
        }
        
        return edges;
    }
    
    /**
     * Read a specific edge from the file at a given byte offset.
     * 
     * @param fileName Path to the edge list file
     * @param offset Byte offset in the file
     * @param nodeMap Map of node IDs to Node objects
     * @return The edge at the specified offset, or null if invalid
     * @throws IOException if file operations fail
     */
    public static Edge readEdgeAtOffset(String fileName, long offset, Map<Integer, Node> nodeMap) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            
            if (offset < 0 || offset + BYTES_PER_EDGE > channel.size()) {
                return null;
            }
            
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, offset, BYTES_PER_EDGE);
            mbb.order(ByteOrder.nativeOrder());
            
            int srcId = mbb.getInt();
            int dstId = mbb.getInt();
            int weight = mbb.getInt();
            
            Node src = nodeMap.get(srcId);
            Node dst = nodeMap.get(dstId);
            
            if (src != null && dst != null) {
                return new Edge(src, dst, weight);
            }
        }
        
        return null;
    }
    
    /**
     * Read a specific edge from the file at a given byte offset, creating minimal Node objects.
     * Use this method when you only need the graph structure and don't care about MLST data.
     * 
     * @param fileName Path to the edge list file
     * @param offset Byte offset in the file
     * @return The edge at the specified offset with minimal Node objects (MLST data = "CGCG"), or null if invalid
     * @throws IOException if file operations fail
     */
    public static Edge readEdgeAtOffset(String fileName, long offset) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            
            if (offset < 0 || offset + BYTES_PER_EDGE > channel.size()) {
                return null;
            }
            
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, offset, BYTES_PER_EDGE);
            mbb.order(ByteOrder.nativeOrder());
            
            int srcId = mbb.getInt();
            int dstId = mbb.getInt();
            int weight = mbb.getInt();
            
            // Create minimal nodes with dummy MLST data
            // Using "CGCG" as placeholder since Point constructor requires non-empty sequence
            Node src = new Node("CGCG", srcId);
            Node dst = new Node("CGCG", dstId);
            
            return new Edge(src, dst, weight);
        }
    }

    /**
     * Add a single edge to the memory-mapped edge list file, maintaining sorted order by destination ID.
     * <p>
     * If the destination node has no incoming edges yet, the edge is appended at the end. Otherwise,
     * the edge is inserted at the appropriate position to maintain order (by shifting subsequent edges, and
     * update the respective offsets in the MLST index file).
     * 
     * @param edge
     * @param fileName
     * @throws IOException
     */
    public static void addEdge(Edge edge, String fileName) throws IOException {
        Node dest = edge.getDestination();
        

        long offset = NodeIndexMapper.getIncomingEdgeOffset(fileName.replace("_edges.dat", "_nodes.dat"), dest.getID());
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
            FileChannel channel = raf.getChannel()) {
            long fileSize = channel.size();
            
            if (offset < 0) { // If the node has no incoming edges yet
                // Append at the end
                channel.position(fileSize);
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, fileSize, BYTES_PER_EDGE);
                mbb.order(ByteOrder.nativeOrder());
                
                writeEdge(mbb, edge);
                
                mbb.force();

                // update offset in node index file
                NodeIndexMapper.updateIncomingEdgeOffset(
                    fileName.replace("_edges.dat", "_nodes.dat"),
                    dest.getID(),
                    fileSize
                );
            } 
            else {    // Insert at the specified offset (shifting subsequent edges)

                // seek to offset and read edges until we find the right position to insert
                channel.position(offset);
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, offset, fileSize - offset);
                mbb.order(ByteOrder.nativeOrder());

                // Read edges until we find the first one with a different destination
                long insertPosition = offset;
                boolean foundPosition = false;
                while (mbb.remaining() >= BYTES_PER_EDGE && !foundPosition) {
                    int currentSrcId = mbb.getInt();
                    int currentDstId = mbb.getInt();
                    int currentWeight = mbb.getInt();

                    if (currentDstId != dest.getID()) { foundPosition = true; }
                    else { insertPosition += BYTES_PER_EDGE; }
                }

                long shiftBy = BYTES_PER_EDGE;
                Map<Integer, Long> updatedOffsets = shiftEdges(channel, insertPosition, shiftBy);
                writeEdgeAtPosition(channel, insertPosition, edge);
                NodeIndexMapper.updateIncomingEdgeOffsets(fileName.replace("_edges.dat", "_nodes.dat"), updatedOffsets);
            }
        }
    }


    /**
     * Shift edges in the file starting from a given offset by a specified number of bytes.
     * This is used to make space for inserting new edges while maintaining sorted order.
     * <p>
     * Returns a map of destination node IDs to their new offsets after the shift.
     *
     * @param channel The file channel for the edge list
     * @param startOffset The offset to start shifting from
     * @param shiftBy The number of bytes to shift
     * @return A map of destination node IDs to their new offsets
     * @throws IOException
     */
    private static Map<Integer, Long> shiftEdges(FileChannel channel, long startOffset, long shiftBy) throws IOException {
        Map<Integer, Long> updatedOffsets = new HashMap<>();

        long fileSize = channel.size();
        long readPosition = fileSize - BYTES_PER_EDGE; // Start by shifting at the end to avoid overwriting data
        long writePosition = fileSize - BYTES_PER_EDGE + shiftBy;
        ByteOrder order = ByteOrder.nativeOrder();
        channel.position(readPosition);
        MappedByteBuffer readMbb = channel.map(FileChannel.MapMode.READ_ONLY, readPosition, BYTES_PER_EDGE);
        readMbb.order(order);
        MappedByteBuffer writeMbb = channel.map(FileChannel.MapMode.READ_WRITE, writePosition, BYTES_PER_EDGE);
        writeMbb.order(order);

        while (readPosition >= startOffset) {
            // Read edge
            int srcId = readMbb.getInt();
            int dstId = readMbb.getInt();
            int weight = readMbb.getInt();

            // Write edge at new position
            writeEdge(writeMbb, srcId, dstId, weight);

            // Update offset for destination node
            updatedOffsets.put(dstId, writePosition); // Since we read from the end, the final offset will be of the last edge we read

            // Move to previous edge
            readPosition -= BYTES_PER_EDGE;
            writePosition -= BYTES_PER_EDGE;
            if (readPosition >= startOffset) {
                channel.position(readPosition);
                readMbb = channel.map(FileChannel.MapMode.READ_ONLY, readPosition, BYTES_PER_EDGE);
                readMbb.order(order);
                channel.position(writePosition);
                writeMbb = channel.map(FileChannel.MapMode.READ_WRITE, writePosition, BYTES_PER_EDGE);
                writeMbb.order(order);
            }
        }

        return updatedOffsets;
    }

    /**
     * Write an edge at a specific position in the file.
     *
     * @param channel The file channel for the edge list
     * @param position The byte position to write the edge at
     * @param edge The edge to write
     * @throws IOException
     */
    private static void writeEdgeAtPosition(FileChannel channel, long position, Edge edge) throws IOException {
        MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, position, BYTES_PER_EDGE);
        mbb.order(ByteOrder.nativeOrder());

        writeEdge(mbb, edge);

        mbb.force();
    }

    /**
     * Write multiple edges starting at a specific position in the file.
     *
     * @param channel The file channel for the edge list
     * @param position The byte position to start writing edges at
     * @param edges The list of edges to write
     * @throws IOException
     */
    private static void writeEdges(FileChannel channel, long position, List<Edge> edges) throws IOException {
        MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, position, (long) edges.size() * BYTES_PER_EDGE);
        mbb.order(ByteOrder.nativeOrder());

        for (Edge edge : edges) {
            writeEdge(mbb, edge);
        }

        mbb.force();
    }

    /**
     * Write a single edge to the given MappedByteBuffer.
     *
     * @param mbb The MappedByteBuffer to write to
     * @param edge The edge to write
     */
    private static void writeEdge(MappedByteBuffer mbb, Edge edge) {
        mbb.putInt(edge.getSource().getID());
        mbb.putInt(edge.getDestination().getID());
        mbb.putInt(edge.getWeight());
    }

    /**
     * Write a single edge to the given MappedByteBuffer using individual components.
     *
     * @param mbb The MappedByteBuffer to write to
     * @param srcId Source node ID
     * @param dstId Destination node ID
     * @param weight Edge weight
     */
    private static void writeEdge(MappedByteBuffer mbb, int srcId, int dstId, int weight) {
        mbb.putInt(srcId);
        mbb.putInt(dstId);
        mbb.putInt(weight);
    }

    /**
     * Add multiple edges for a node to the memory-mapped edge list file, maintaining sorted order by destination ID.
     * <p>
     * If the destination node has no incoming edges yet, the edges are appended at the end. Otherwise,
     * the edges are inserted at the appropriate position to maintain order (by shifting subsequent edges, and
     * update the respective offsets in the MLST index file).
     *
     * @param edges The list of edges to add
     * @param node The destination node for all edges
     * @param fileName The name of the edge list file
     * @throws IOException
     */
    public static void addEdges(List<Edge> edges, Node node, String fileName) throws IOException {
        // Handle empty edge list - nothing to add
        if (edges == null || edges.isEmpty()) {
            return;
        }
        
        Node dest = node;
        

        long offset = NodeIndexMapper.getIncomingEdgeOffset(fileName.replace("_edges.dat", "_nodes.dat"), dest.getID());
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
            FileChannel channel = raf.getChannel()) {
            long fileSize = channel.size();

            if (offset < 0) { // If the node has no incoming edges yet
                // TODO - refactor para appendEdge
                // Append at the end
                channel.position(fileSize);
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, fileSize, edges.size() * BYTES_PER_EDGE);
                mbb.order(ByteOrder.nativeOrder());
                
                for (Edge edge : edges) {
                    writeEdge(mbb, edge);
                }
                
                mbb.force();

                // update offset in node index file
                NodeIndexMapper.updateIncomingEdgeOffset(
                    fileName.replace("_edges.dat", "_nodes.dat"),
                    dest.getID(),
                    fileSize
                );
            } 
            else {    // Insert at the specified offset (shifting subsequent edges)
                // If offset equals or exceeds fileSize, there are no edges after this position, so just append
                if (offset >= fileSize) {
                    channel.position(fileSize);
                    MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, fileSize, edges.size() * BYTES_PER_EDGE);
                    mbb.order(ByteOrder.nativeOrder());
                    
                    for (Edge edge : edges) {
                        writeEdge(mbb, edge);
                    }
                    
                    mbb.force();
                    return; // No need to update offset since it's already set
                }

                // seek to offset and read edges until we find the right position to insert
                channel.position(offset);
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, offset, fileSize - offset);
                mbb.order(ByteOrder.nativeOrder());

                // Read edges until we find the first one with a different destination
                long insertPosition = offset;
                boolean foundPosition = false;
                while (mbb.remaining() >= BYTES_PER_EDGE && !foundPosition) {
                    int currentSrcId = mbb.getInt();
                    int currentDstId = mbb.getInt();
                    int currentWeight = mbb.getInt();

                    if (currentDstId != dest.getID()) { foundPosition = true; }
                    else { insertPosition += BYTES_PER_EDGE; }
                }

                long shiftBy = (long) edges.size() * BYTES_PER_EDGE;
                Map<Integer, Long> updatedOffsets = shiftEdges(channel, insertPosition, shiftBy);
                writeEdges(channel, insertPosition, edges);
                NodeIndexMapper.updateIncomingEdgeOffsets(fileName.replace("_edges.dat", "_nodes.dat"), updatedOffsets);
            }
        }
    }
}
