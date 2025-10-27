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
}
