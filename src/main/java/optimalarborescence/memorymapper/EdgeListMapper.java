package optimalarborescence.memorymapper;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;

/**
 * EdgeListMapper provides memory-mapped file operations for edge arrays.
 * Each array file stores the edges incident to a specific node.
 * 
 * File Format:
 * Header:
 *   [num_edges (8 bytes)]
 * 
 * Each edge entry (12 bytes total):
 *   [source_id (4 bytes), destination_id (4 bytes), weight (4 bytes)]
 */
public class EdgeListMapper {
    
    public static final int HEADER_SIZE = 8; // num_edges (1 long)
    public static final int BYTES_PER_EDGE = 12; // 3 ints per edge
    public static final long NO_OFFSET = -1L;
    
    // Chunk size for batch memory mapping
    private static final long CHUNK_SIZE = 2 * 1024 * 1024; // 2MB chunks

    /**
     * Get the number of edges stored in the edge list file.
     * 
     * @param fileName Path to the edge list file
     * @return Number of edges in the file
     * @throws IOException if file operations fail
     */
    public static long getNumEdges(String fileName) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            mbb.order(ByteOrder.nativeOrder());
            return mbb.getLong();
        }
    }

    /**
     * Add a single edge to the memory-mapped edge array file.
     * <p>
     * The new edge is always appended at the end of the file.
     * 
     * @param edge The edge to add
     * @param fileName Path to the edge list file
     * @throws IOException if file operations fail
     */
    public static void addEdge(Edge edge, String fileName) throws IOException {
        Node dest = edge.getDestination();
        String nodeFileName = fileName.replace("_edges.dat", "");
        nodeFileName += "_edges_node" + dest.getId() + ".dat";
        
        try (RandomAccessFile raf = new RandomAccessFile(nodeFileName, "rw");
            FileChannel channel = raf.getChannel()) {
            
            // Read header to get edge count
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            long edgeCount = headerMbb.getLong();

            // Calculate new file size and position to append
            long appendPosition = HEADER_SIZE + edgeCount * BYTES_PER_EDGE;
            long newFileSize = appendPosition + BYTES_PER_EDGE;
            raf.setLength(newFileSize);
            MappedByteBuffer edgeMbb = channel.map(FileChannel.MapMode.READ_WRITE, appendPosition, BYTES_PER_EDGE);
            edgeMbb.order(ByteOrder.nativeOrder());

            // Write the new edge
            edgeMbb.putInt(edge.getSource().getId());
            edgeMbb.putInt(edge.getDestination().getId());
            edgeMbb.putInt(edge.getWeight());
            
            // Update header with new count
            headerMbb.position(0);
            headerMbb.putLong(edgeCount + 1);
        }
    }

    /**
     * Add multiple edges for a node to the memory-mapped edge array.
     * <p>
     * All edges are appended at the end of the file.
     *
     * @param edges The list of edges to add
     * @param node The destination node for all edges
     * @param fileName The name of the edge list file
     * @throws IOException if file operations fail
     */
    public static void addEdges(List<Edge> edges, Node node, String fileName) throws IOException {
        // Handle empty edge list - nothing to add
        if (edges == null || edges.isEmpty()) {
            return;
        }

        String nodeFileName = fileName.replace("_edges.dat", "");
        nodeFileName += "_edges_node" + node.getId() + ".dat";

        try (RandomAccessFile raf = new RandomAccessFile(nodeFileName, "rw");
             FileChannel channel = raf.getChannel()) {

            // Read header to get edge count
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            long edgeCount = headerMbb.getLong();

            // Calculate new file size and position to append
            long appendPosition = HEADER_SIZE + edgeCount * BYTES_PER_EDGE;
            long totalEdgeSize = (long) edges.size() * BYTES_PER_EDGE;
            long newFileSize = appendPosition + totalEdgeSize;
            raf.setLength(newFileSize);
            
            // Write edges in chunks to avoid exceeding 2GB mapping limit
            long currentOffset = appendPosition;
            int edgesWritten = 0;
            while (edgesWritten < edges.size()) {
                long remainingEdges = edges.size() - edgesWritten;
                long remainingBytes = newFileSize - currentOffset;
                long chunkSize = Math.min(remainingBytes, CHUNK_SIZE);
                long edgesInChunk = chunkSize / BYTES_PER_EDGE;
                if (edgesInChunk > remainingEdges) {
                    edgesInChunk = remainingEdges;
                    chunkSize = edgesInChunk * BYTES_PER_EDGE;
                }
                
                MappedByteBuffer edgeMbb = channel.map(FileChannel.MapMode.READ_WRITE, currentOffset, chunkSize);
                edgeMbb.order(ByteOrder.nativeOrder());
                
                for (int i = 0; i < edgesInChunk; i++) {
                    Edge edge = edges.get(edgesWritten);
                    edgeMbb.putInt(edge.getSource().getId());
                    edgeMbb.putInt(edge.getDestination().getId());
                    edgeMbb.putInt(edge.getWeight());
                    edgesWritten++;
                }
                
                currentOffset += chunkSize;
            }
            
            // Update header with new count
            headerMbb.position(0);
            headerMbb.putLong(edgeCount + edges.size());
        }
    }
    
    /**
     * Add multiple edges for multiple nodes in a single batch operation. An edge is
     * only added to the edge array of its destination node.
     * 
     * @param nodeEdgesMap Map of node to its list of incoming edges
     * @param fileName The name of the edge list file
     * @throws IOException if file operations fail
     */
    public static void addEdgesBatch(Map<Node, List<Edge>> nodeEdgesMap, String fileName) throws IOException {
        if (nodeEdgesMap == null || nodeEdgesMap.isEmpty()) {
            return;
        }

        for (Map.Entry<Node, List<Edge>> entry : nodeEdgesMap.entrySet()) {
            Node destNode = entry.getKey();
            List<Edge> edges = entry.getValue();
            if (edges == null || edges.isEmpty()) {
                continue; // Skip empty edge lists
            }
            addEdges(edges, destNode, fileName);
        }
    }

    /**
     * Adds edges to existing nodes' edge arrays.
     * This is used when new nodes have edges pointing TO existing nodes (e.g., in asymmetric graphs).
     * The edges are appended to the existing nodes' edge arrays.
     * 
     * @param existingNodeNewEdges Map of existing nodes to edges that should be added to their incoming edge lists
     * @param nodeFileName The name of the node index file (needed to get edge offsets)
     * @param edgeFileName The name of the edge list file
     * @throws IOException if file operations fail
     */
    public static void addEdgesToExistingNodes(Map<Node, List<Edge>> existingNodeNewEdges, 
                                               String nodeFileName, String edgeFileName) throws IOException {
        if (existingNodeNewEdges == null || existingNodeNewEdges.isEmpty()) {
            return;
        }
        
        for (Map.Entry<Node, List<Edge>> entry : existingNodeNewEdges.entrySet()) {
            Node destNode = entry.getKey();
            List<Edge> edges = entry.getValue();
            if (edges == null || edges.isEmpty()) {
                continue; // Skip empty edge lists
            }
            addEdges(edges, destNode, edgeFileName);
        }
    }

    private static List<Edge> readEdgeArrayInChunks(FileChannel channel, long numEdges, long fileSize) throws IOException {
        List<Edge> edges = new ArrayList<>((int) numEdges);
        Map<Integer, Node> nodeCache = new HashMap<>();
        
        long edgesRead = 0;
        long currentOffset = HEADER_SIZE;
        
        while (edgesRead < numEdges) {
            long remainingEdges = numEdges - edgesRead;
            long remainingBytes = fileSize - currentOffset;
            long chunkSize = Math.min(remainingBytes, CHUNK_SIZE);
            long edgesInChunk = chunkSize / BYTES_PER_EDGE;
            if (edgesInChunk > remainingEdges) {
                edgesInChunk = remainingEdges;
                chunkSize = edgesInChunk * BYTES_PER_EDGE;
            }
            
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, currentOffset, chunkSize);
            mbb.order(ByteOrder.nativeOrder());
            
            for (int i = 0; i < edgesInChunk; i++) {
                int srcId = mbb.getInt();
                int destId = mbb.getInt();
                int weight = mbb.getInt();
                
                Node src = nodeCache.computeIfAbsent(srcId, id -> new Node(id));
                Node dst = nodeCache.computeIfAbsent(destId, id -> new Node(id));
                
                edges.add((int) edgesRead, new Edge(src, dst, weight));
                edgesRead++;
            }
            
            currentOffset += chunkSize;
        }
        
        return edges;
    }

    public static List<Edge> loadEdgeArray(String filename) {
        try (RandomAccessFile raf = new RandomAccessFile(filename, "r")) {

            FileChannel channel = raf.getChannel();
            long fileSize = channel.size();
            long numEdges = (fileSize - HEADER_SIZE) / BYTES_PER_EDGE;
            
            List<Edge> edges = new ArrayList<>((int) numEdges);
            if (numEdges > Integer.MAX_VALUE) {
                throw new IOException("Edge list too large to fit in an array");
            }
            else if (numEdges == 0) {
                return edges; // empty array
            }
            else if (fileSize < HEADER_SIZE) {
                throw new IOException("Invalid edge file format");
            }
            else if ((fileSize - HEADER_SIZE) % BYTES_PER_EDGE != 0) {
                throw new IOException("Corrupted edge file: size does not align with edge record size");
            }
            return readEdgeArrayInChunks(channel, numEdges, fileSize);
        } 
        catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load edge array from file: " + filename, e);
        }
    }

    public static void writeEdgeArray(String filename, List<Edge> edges) {
        try (RandomAccessFile raf = new RandomAccessFile(filename, "rw");
             FileChannel channel = raf.getChannel()) {
            
            long totalEdges = edges.size();
            long totalSize = HEADER_SIZE + totalEdges * BYTES_PER_EDGE;
            raf.setLength(totalSize);
            
            // Write header
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            headerMbb.putLong(totalEdges);
            
            // Write edges in chunks
            long currentOffset = HEADER_SIZE;
            int edgesWritten = 0;
            while (edgesWritten < totalEdges) {
                long remainingEdges = totalEdges - edgesWritten;
                long remainingBytes = totalSize - currentOffset;
                long chunkSize = Math.min(remainingBytes, CHUNK_SIZE);
                long edgesInChunk = chunkSize / BYTES_PER_EDGE;
                if (edgesInChunk > remainingEdges) {
                    edgesInChunk = remainingEdges;
                    chunkSize = edgesInChunk * BYTES_PER_EDGE;
                }
                
                MappedByteBuffer edgeMbb = channel.map(FileChannel.MapMode.READ_WRITE, currentOffset, chunkSize);
                edgeMbb.order(ByteOrder.nativeOrder());
                
                for (int i = 0; i < edgesInChunk; i++) {
                    Edge edge = edges.get(edgesWritten);
                    edgeMbb.putInt(edge.getSource().getId());
                    edgeMbb.putInt(edge.getDestination().getId());
                    edgeMbb.putInt(edge.getWeight());
                    edgesWritten++;
                }
                
                currentOffset += chunkSize;
            }
        } 
        catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to write edge array to file: " + filename, e);
        }
    }

    /**
     * Decrement the edge count in the file header by the specified count.
     * 
     * @param filename Path to the edge list file
     * @param channel The file channel for the edge list file
     * @param count Number of edges to decrement
     * @throws IOException if file operations fail
     */
    private static void decrementHeader(String filename, FileChannel channel, long count) throws IOException {
        MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_SIZE);
        headerMbb.order(ByteOrder.nativeOrder());
        long edgeCount = headerMbb.getLong();
        headerMbb.rewind();
        headerMbb.putLong(edgeCount - count);
        headerMbb.force();
    }

    public static boolean edgeExists(String filename, int sourceId, int destId) throws IOException {
        String nodeFileName = filename.replace("_edges.dat", "");
        nodeFileName += "_edges_node" + destId + ".dat";

        try (RandomAccessFile raf = new RandomAccessFile(nodeFileName, "r");
            FileChannel channel = raf.getChannel()) {

            long fileSize = channel.size();
            long currentOffset = HEADER_SIZE;
            while (currentOffset < fileSize) {
                // Map a sizeable chunk to avoid excessive mappings
                int mappingSize = (int) Math.min(CHUNK_SIZE, fileSize - currentOffset);
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, currentOffset, mappingSize);
                mbb.order(ByteOrder.nativeOrder());

                int edgesInChunk = mappingSize / BYTES_PER_EDGE;
                for (int j = 0; j < edgesInChunk; j++) {
                    int srcId = mbb.getInt();
                    int dstId = mbb.getInt();
                    mbb.getInt(); // skip weight
                    if (srcId == sourceId && dstId == destId) {
                        return true;
                    }
                }
                currentOffset += mappingSize;
            }
        }
        return false;
    }

    public static void removeEdge(String filename, int sourceId, int destId) throws IOException {
        String nodeFileName = filename.replace("_edges.dat", "");
        nodeFileName += "_edges_node" + destId + ".dat";

        try (RandomAccessFile raf = new RandomAccessFile(nodeFileName, "rw");
            FileChannel channel = raf.getChannel()) {

            long fileSize = channel.size();
            long currentOffset = HEADER_SIZE;
            long edgeOffsetToRemove = -1;

            while (currentOffset < fileSize) {
                // Map a sizeable chunk to avoid excessive mappings
                int mappingSize = (int) Math.min(CHUNK_SIZE, fileSize - currentOffset);
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, currentOffset, mappingSize);
                mbb.order(ByteOrder.nativeOrder());

                int edgesInChunk = mappingSize / BYTES_PER_EDGE;
                for (int j = 0; j < edgesInChunk; j++) {
                    int srcId = mbb.getInt();
                    int dstId = mbb.getInt();
                    mbb.getInt(); // skip weight
                    if (srcId == sourceId && dstId == destId) {
                        edgeOffsetToRemove = currentOffset + j * BYTES_PER_EDGE;
                        
                        // If it is the last edge, truncate the file immediately
                        if (edgeOffsetToRemove + BYTES_PER_EDGE == fileSize) {
                            raf.setLength(edgeOffsetToRemove);
                        }
                        else { // Swap with the last edge and truncate file
                            long lastEdgeOffset = fileSize - BYTES_PER_EDGE;
                            MappedByteBuffer lastEdgeMbb = channel.map(FileChannel.MapMode.READ_ONLY, lastEdgeOffset, BYTES_PER_EDGE);
                            lastEdgeMbb.order(ByteOrder.nativeOrder());

                            // Read last edge data
                            int lastSrcId = lastEdgeMbb.getInt();
                            int lastDstId = lastEdgeMbb.getInt();
                            int lastWeight = lastEdgeMbb.getInt();

                            // Write last edge data to the position of the edge to remove
                            MappedByteBuffer edgeToRemoveMbb = channel.map(FileChannel.MapMode.READ_WRITE, edgeOffsetToRemove, BYTES_PER_EDGE);
                            edgeToRemoveMbb.order(ByteOrder.nativeOrder());
                            edgeToRemoveMbb.putInt(lastSrcId);
                            edgeToRemoveMbb.putInt(lastDstId);
                            edgeToRemoveMbb.putInt(lastWeight);

                            // Truncate the file
                            raf.setLength(lastEdgeOffset);
                        }
                        decrementHeader(nodeFileName, channel, 1);
                        return;
                    }
                }
                currentOffset += mappingSize;
            }
        }
    }

    private static void deleteFileIfExists(String fileName) throws IOException {
        File file = new File(fileName);
        if (file.exists()) {
            if (!file.delete()) {
                throw new IOException("Failed to delete file: " + fileName);
            }
        }
    }

    public static void removeEdges(String filename, int nodeId) throws IOException {
        String nodeFileName = filename.replace("_edges.dat", "");
        nodeFileName += "_edges" + "_node" + nodeId + ".dat";

        // Delete file
        deleteFileIfExists(nodeFileName);
    }

    /**
     * Remove all edge arrays for a set of nodes in a single batch operation.
     * 
     * @param nodeIdsToRemove Set of node IDs whose incident edges should be removed
     * @param fileName Path to the edge list file
     * @throws IOException if file operations fail
     */
    public static void removeEdgesBatch(Set<Integer> nodeIdsToRemove, String fileName) throws IOException {
        if (nodeIdsToRemove == null || nodeIdsToRemove.isEmpty()) {
            return;
        }

        // remove _edges.dat from filename
        String baseFileName = fileName.replace("_edges.dat", "");
        for (Integer nodeId : nodeIdsToRemove) {
            String nodeFileName = baseFileName + "_edges_node" + nodeId + ".dat";

            // Delete file
            try {
                deleteFileIfExists(nodeFileName);
            } catch (IOException e) {
                throw new IOException("Failed to delete node file: " + nodeFileName, e);
            }
        }
    }

    public static void removeOutgoingEdges(String filename, int sourceId) throws IOException {
        // Get node map
        Map<Integer, Node> map = NodeIndexMapper.loadNodes(filename.replace("_edges.dat", "_nodes.dat"));

        // For each node in the map, remove edges from sourceId to that node
        for (Node node : map.values()) {
            removeEdge(filename, sourceId, node.getId());
        }
    }

    public static List<Edge> getOutgoingEdges(String filename, int sourceId) throws IOException {
        // Get node map
        String edgeFile = filename;
        Map<Integer, Node> map = NodeIndexMapper.loadNodes(filename.replace("_edges.dat", "_nodes.dat"));

        List<Edge> edges = new ArrayList<>();
        // For node in the map
        for (Node node : map.values()) {
            if (node.getId() == sourceId) continue;
            String nodeFileName = edgeFile.replace("_edges.dat", "");
            nodeFileName += "_edges_node" + node.getId() + ".dat";

            List<Edge> nodeEdges = loadEdgeArray(nodeFileName);
            for (Edge edge : nodeEdges) {
                if (edge.getSource().getId() == sourceId) {
                    edges.add(edge);
                }
            }
        }
        return edges;
    }
}