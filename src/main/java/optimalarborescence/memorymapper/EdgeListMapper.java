package optimalarborescence.memorymapper;

import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EdgeListMapper provides memory-mapped file operations for storing and loading edge lists.
 * Uses a linked list structure within the file for efficient traversal of edges by destination.
 * 
 * File Format:
 * Header:
 *   [num_edges (4 bytes)]
 * 
 * Each edge entry (28 bytes total):
 *   [source_id (4 bytes), destination_id (4 bytes), weight (4 bytes),
 *    next_offset (8 bytes), prev_offset (8 bytes)]
 * 
 * The next_offset and prev_offset form a doubly-linked list of edges with the same destination.
 * Offsets are set to -1 when there is no next or previous edge.
 */
public class EdgeListMapper {
    
    public static final int HEADER_SIZE = 4; // num_edges (1 int)
    public static final int BYTES_PER_EDGE = 28; // 3 ints + 2 longs per edge
    public static final long NO_OFFSET = -1L;
    
    /**
     * Save edges to a memory-mapped file with linked list structure.
     * Edges with the same destination are linked together via next/prev offsets.
     * 
     * @param edges List of edges to save
     * @param fileName Path to the output file
     * @return Map of destination node ID to byte offset of the first edge (for incoming edges)
     * @throws IOException if file operations fail
     */
    public static Map<Integer, Long> saveEdgesToMappedFile(List<Edge> edges, String fileName) throws IOException {
        // Calculate file size: header + all edges
        long fileSize = HEADER_SIZE + (long) edges.size() * BYTES_PER_EDGE;
        
        // Track offsets for incoming edges (by destination - points to first edge)
        Map<Integer, Long> incomingEdgeOffsets = new HashMap<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            
            raf.setLength(fileSize);
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
            mbb.order(ByteOrder.nativeOrder());
            
            // Write header
            mbb.putInt(edges.size());
            
            // Create EdgeEntry objects and assign offsets
            long currentOffset = HEADER_SIZE;
            List<EdgeEntry> allEntries = new ArrayList<>();
            Map<Integer, List<EdgeEntry>> edgesByDest = new HashMap<>();
            
            for (Edge edge : edges) {
                EdgeEntry entry = new EdgeEntry(edge);
                entry.offset = currentOffset;
                allEntries.add(entry);
                
                // Group by destination for linking
                int destId = edge.getDestination().getId();
                edgesByDest.computeIfAbsent(destId, k -> new ArrayList<>()).add(entry);
                
                currentOffset += BYTES_PER_EDGE;
            }
            
            // Build linked lists for edges with same destination
            for (Map.Entry<Integer, List<EdgeEntry>> destGroup : edgesByDest.entrySet()) {
                int destId = destGroup.getKey();
                List<EdgeEntry> destEdges = destGroup.getValue();
                
                if (!destEdges.isEmpty()) {
                    // Record the first edge offset for this destination
                    incomingEdgeOffsets.put(destId, destEdges.get(0).offset);
                    
                    // Link the edges in the list
                    for (int i = 0; i < destEdges.size(); i++) {
                        EdgeEntry entry = destEdges.get(i);
                        entry.nextOffset = (i < destEdges.size() - 1) ? destEdges.get(i + 1).offset : NO_OFFSET;
                        entry.prevOffset = (i > 0) ? destEdges.get(i - 1).offset : NO_OFFSET;
                    }
                }
            }
            
            // Write all edges with their link pointers
            for (EdgeEntry entry : allEntries) {
                writeEdgeEntry(mbb, entry);
            }
            
            mbb.force();
        }
        
        return incomingEdgeOffsets;
    }
    
    /**
     * Helper class to track edge data with file offsets and link pointers.
     */
    private static class EdgeEntry {
        Edge edge;
        long offset;
        long nextOffset;
        long prevOffset;
        
        EdgeEntry(Edge edge) {
            this.edge = edge;
            this.offset = -1;
            this.nextOffset = -1;
            this.prevOffset = -1;
        }
    }
    
    /**
     * Write an edge entry with its link pointers to the buffer.
     * 
     * @param mbb The mapped byte buffer
     * @param entry The edge entry to write
     */
    private static void writeEdgeEntry(MappedByteBuffer mbb, EdgeEntry entry) {
        mbb.putInt(entry.edge.getSource().getId());
        mbb.putInt(entry.edge.getDestination().getId());
        mbb.putInt(entry.edge.getWeight());
        mbb.putLong(entry.nextOffset);
        mbb.putLong(entry.prevOffset);
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
            if (size % BYTES_PER_EDGE != HEADER_SIZE) {
                throw new IOException("Edge file size is not a multiple of edge size (28 bytes)");
            }
            
            int edgeCount = (int) (size / BYTES_PER_EDGE);
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, HEADER_SIZE, size - HEADER_SIZE);
            mbb.order(ByteOrder.nativeOrder());
            
            for (int i = 0; i < edgeCount; i++) {
                int srcId = mbb.getInt();
                int dstId = mbb.getInt();
                int weight = mbb.getInt();
                mbb.getLong(); // skip nextOffset
                mbb.getLong(); // skip prevOffset

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
            if (size % BYTES_PER_EDGE != HEADER_SIZE) {
                throw new IOException("Edge file size is not a multiple of edge size (28 bytes)");
            }
            
            int edgeCount = (int) (size / BYTES_PER_EDGE);
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, HEADER_SIZE, size - HEADER_SIZE);
            mbb.order(ByteOrder.nativeOrder());
            
            for (int i = 0; i < edgeCount; i++) {
                int srcId = mbb.getInt();
                int dstId = mbb.getInt();
                int weight = mbb.getInt();
                mbb.getLong(); // skip nextOffset
                mbb.getLong(); // skip prevOffset

                
                // Create minimal nodes on-the-fly without Sequence data / Point data (reuse same instance for same ID)
                // Note: MLST sequences are only relevant for the nearest neighbour algorithms
                // and distance computations. It does not matter when we just want to load edges 
                Node src = nodeCache.computeIfAbsent(srcId, id -> new Node(id));
                Node dst = nodeCache.computeIfAbsent(dstId, id -> new Node(id));
                
                edges.add(new Edge(src, dst, weight));
            }
        }
        
        return edges;
    }

    /**
     * Get the number of edges stored in the edge list file.
     * 
     * @param fileName Path to the edge list file
     * @return Number of edges in the file
     * @throws IOException if file operations fail
     */
    public static int getNumEdges(String fileName) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "r");
             FileChannel channel = raf.getChannel()) {
            
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE);
            mbb.order(ByteOrder.nativeOrder());
            return mbb.getInt();
        }
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
            
            // Create minimal nodes without Sequence data / Point data
            Node src = new Node(srcId);
            Node dst = new Node(dstId);
            
            return new Edge(src, dst, weight);
        }
    }

    /**
     * Add a single edge to the memory-mapped edge list file using linked list structure.
     * <p>
     * The new edge is always appended at the end of the file. If the destination node already
     * has incoming edges, the new edge is linked to the existing chain by updating the
     * previous last edge's next pointer and setting this edge's prev pointer.
     * 
     * @param edge The edge to add
     * @param fileName Path to the edge list file
     * @throws IOException if file operations fail
     */
    public static void addEdge(Edge edge, String fileName) throws IOException {
        Node dest = edge.getDestination();
        String nodeFileName = fileName.replace("_edges.dat", "_nodes.dat");
        
        long firstEdgeOffset = NodeIndexMapper.getIncomingEdgeOffset(nodeFileName, dest.getId());
        
        try (RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
             FileChannel channel = raf.getChannel()) {
            
            long fileSize = channel.size();
            
            // Read header to get edge count
            MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_SIZE);
            headerMbb.order(ByteOrder.nativeOrder());
            int edgeCount = headerMbb.getInt();
            
            // Calculate new edge offset (at end of file)
            long newEdgeOffset = fileSize;
            
            if (firstEdgeOffset < 0) {
                // No existing edges for this destination - this will be the first
                // Write new edge with no links
                raf.setLength(fileSize + BYTES_PER_EDGE);
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, newEdgeOffset, BYTES_PER_EDGE);
                mbb.order(ByteOrder.nativeOrder());
                
                mbb.putInt(edge.getSource().getId());
                mbb.putInt(edge.getDestination().getId());
                mbb.putInt(edge.getWeight());
                mbb.putLong(NO_OFFSET);  // No next edge
                mbb.putLong(NO_OFFSET);  // No previous edge
                
                mbb.force();
                
                // Update node index to point to this first edge
                NodeIndexMapper.updateIncomingEdgeOffset(nodeFileName, dest.getId(), newEdgeOffset);
            } else {
                // Find the last edge in the chain for this destination
                long currentOffset = firstEdgeOffset;
                long lastEdgeOffset = currentOffset;
                
                while (currentOffset >= 0) {
                    MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, currentOffset, BYTES_PER_EDGE);
                    mbb.order(ByteOrder.nativeOrder());
                    
                    mbb.getInt();  // Skip source ID
                    mbb.getInt();  // Skip dest ID
                    mbb.getInt();  // Skip weight
                    long nextOffset = mbb.getLong();
                    
                    if (nextOffset < 0) {
                        // This is the last edge in the chain
                        lastEdgeOffset = currentOffset;
                        break;
                    }
                    currentOffset = nextOffset;
                }
                
                // Write new edge at end of file
                raf.setLength(fileSize + BYTES_PER_EDGE);
                MappedByteBuffer newEdgeMbb = channel.map(FileChannel.MapMode.READ_WRITE, newEdgeOffset, BYTES_PER_EDGE);
                newEdgeMbb.order(ByteOrder.nativeOrder());
                
                newEdgeMbb.putInt(edge.getSource().getId());
                newEdgeMbb.putInt(edge.getDestination().getId());
                newEdgeMbb.putInt(edge.getWeight());
                newEdgeMbb.putLong(NO_OFFSET);  // No next edge
                newEdgeMbb.putLong(lastEdgeOffset);  // Link back to previous edge
                
                newEdgeMbb.force();
                
                // Update the previous last edge to point to this new edge
                MappedByteBuffer lastEdgeMbb = channel.map(FileChannel.MapMode.READ_WRITE, 
                    lastEdgeOffset + 12 + 8, 8);  // Position at nextOffset field (skip 3 ints + prev long)
                lastEdgeMbb.order(ByteOrder.nativeOrder());
                lastEdgeMbb.putLong(newEdgeOffset);
                lastEdgeMbb.force();
            }
            
            // Update edge count in header
            headerMbb.rewind();
            headerMbb.putInt(edgeCount + 1);
            headerMbb.force();
        }
    }

    /**
     * Add multiple edges for a node to the memory-mapped edge list file using linked list structure.
     * <p>
     * All edges are appended at the end of the file and linked together in a chain for the
     * specified destination node.
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
        
        // Simply add each edge one by one - they will be linked automatically
        for (Edge edge : edges) {
            addEdge(edge, fileName);
        }
    }

    /**
     * Load a linked list of edges starting from a given offset in the file.
     * Follows the next pointers to retrieve all edges in the list.
     * 
     * @param filename Path to the edge list file
     * @param offset Byte offset of the first edge in the linked list
     * @return List of edges in the linked list
     */
    public static List<Edge> loadLinkedList(String filename, long offset) {
        List<Edge> edges = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(filename, "r")) {
            FileChannel channel = raf.getChannel();
            long fileSize = channel.size();

            long currentOffset = offset;
            while (currentOffset >= 0) {
                // Bounds check: ensure we don't try to map beyond file size
                if (currentOffset < 0 || currentOffset + BYTES_PER_EDGE > fileSize) {
                    System.err.println("Warning: Invalid offset " + currentOffset + 
                                     " in edge list (file size: " + fileSize + ")");
                    break;
                }
                
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, currentOffset, BYTES_PER_EDGE);
                mbb.order(ByteOrder.nativeOrder());

                int sourceId = mbb.getInt();
                int destId = mbb.getInt();
                int weight = mbb.getInt();
                long nextOffset = mbb.getLong();
                mbb.getLong();

                edges.add(new Edge(new Node(sourceId), new Node(destId), weight));
                currentOffset = nextOffset;
            }
        } 
        catch (IOException e) {
            e.printStackTrace();
        }
        return edges;
    }

    /**
     * Update the linked list pointers for the previous and next edges.
     * 
     * @param filename Path to the edge list file
     * @param prev Offset of the previous edge in the list (-1 if none)
     * @param next Offset of the next edge in the list (-1 if none)
     * @param channel The file channel for the edge list file
     * @throws IOException if file operations fail
     */
    private static void updateLinkedList(String filename, long prev, long next, FileChannel channel) throws IOException {
        if (prev >= 0) {
            MappedByteBuffer prevMbb = channel.map(FileChannel.MapMode.READ_WRITE, prev + 12, 8);
            prevMbb.order(ByteOrder.nativeOrder());
            prevMbb.putLong(next);
            prevMbb.force();
        }
        if (next >= 0) {
            MappedByteBuffer nextMbb = channel.map(FileChannel.MapMode.READ_WRITE, next + 20, 8);
            nextMbb.order(ByteOrder.nativeOrder());
            nextMbb.putLong(prev);
            nextMbb.force();
        }
    }

    // private static void updatePrevAndNextOffsets(FileChannel channel, long offset, long newNext, long newPrev) throws IOException {
    //     MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_WRITE, offset, BYTES_PER_EDGE);
    //     mbb.order(ByteOrder.nativeOrder());

    //     mbb.getInt(); // skip source ID
    //     mbb.getInt(); // skip dest ID
    //     mbb.getInt(); // skip weight
    //     if (newNext != NO_CHANGE) {
    //         mbb.putLong(newNext);
    //     } else {
    //         mbb.getLong(); // skip next
    //     }
    //     if (newPrev != NO_CHANGE) {
    //         mbb.putLong(newPrev);
    //     } else {
    //         mbb.getLong(); // skip prev
    //     }
    //     mbb.force();
    // }

    // private static void maintainTargetListOffsetAndPointers(String filename, long targetOffset, int destId,
    //                                                 long prevOffset, FileChannel channel) throws IOException {
    //     if (prevOffset < 0) {
    //         return; // Only one edge in the list
    //     }
    //     String nodeFileName = filename.replace("_edges.dat", "_nodes.dat");
    //     long listHeadOffset = NodeIndexMapper.getIncomingEdgeOffset(nodeFileName, destId);
    //     System.out.println("Current list head offset for node " + destId + " is " + listHeadOffset);
    //     if (listHeadOffset > targetOffset) {
    //         // New head of list
    //         NodeIndexMapper.updateIncomingEdgeOffset(nodeFileName, destId, targetOffset);
    //         System.out.println("Updated incoming edge offset for node " + destId + " to " + targetOffset);

    //         System.out.println("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
    //         updatePrevAndNextOffsets(channel, targetOffset, listHeadOffset, NO_OFFSET);
    //         updatePrevAndNextOffsets(channel, listHeadOffset, NO_CHANGE, targetOffset);
    //         updatePrevAndNextOffsets(channel, prevOffset, NO_OFFSET, NO_CHANGE);
    //     }
    // }

    /**
     * Maintain the target list offset and pointers when compacting the edge list file.
     * If the list head was pointing to the edge being moved (lastEdgeOffset),
     * update it to point to the new location (targetOffset).
     * @param filename Path to the edge list file
     * @param lastEdgeOffset Byte offset of the last edge being moved
     * @param destId Destination node ID of the edge
     * @param targetOffset Byte offset of the target location
     * @param channel The file channel for the edge list file
     * @throws IOException
     */
    private static void maintainTargetListOffsetAndPointers(String filename, long lastEdgeOffset, int destId,
                                                    long targetOffset, FileChannel channel) throws IOException {
        String nodeFileName = filename.replace("_edges.dat", "_nodes.dat");
        long listHeadOffset = NodeIndexMapper.getIncomingEdgeOffset(nodeFileName, destId);
        
        // If the list head was pointing to the edge being moved (lastEdgeOffset),
        // update it to point to the new location (targetOffset)
        if (listHeadOffset == lastEdgeOffset) {
            NodeIndexMapper.updateIncomingEdgeOffset(nodeFileName, destId, targetOffset);
        }
    }

    /**
     * Invalidate an edge entry at the given offset and compact the file by moving
     * the last edge into the removed edge's position.
     * 
     * @param filename Path to the edge list file
     * @param offset Byte offset of the edge to invalidate
     * @param channel The file channel for the edge list file
     * @throws IOException if file operations fail
     */
    private static void invalidateEntryAndCompactFile(String filename, long offset, FileChannel channel) throws IOException {
        // get the last edge in the file and write it at offset
        long fileSize = channel.size();
        long lastEdgeOffset = fileSize - BYTES_PER_EDGE;

        if (lastEdgeOffset != offset) {
            MappedByteBuffer lastMbb = channel.map(FileChannel.MapMode.READ_ONLY, lastEdgeOffset, BYTES_PER_EDGE);
            lastMbb.order(ByteOrder.nativeOrder());

            int srcId = lastMbb.getInt();
            int destId = lastMbb.getInt();
            int weight = lastMbb.getInt();
            long next = lastMbb.getLong();
            long prev = lastMbb.getLong();

            // Write last edge data to the removed edge's position
            MappedByteBuffer targetMbb = channel.map(FileChannel.MapMode.READ_WRITE, offset, BYTES_PER_EDGE);
            targetMbb.order(ByteOrder.nativeOrder());
            targetMbb.putInt(srcId);
            targetMbb.putInt(destId);
            targetMbb.putInt(weight);
            targetMbb.putLong(next);
            targetMbb.putLong(prev);
            targetMbb.force();

            maintainTargetListOffsetAndPointers(filename, lastEdgeOffset, destId, offset, channel);
            
            // Update the linked list pointers of adjacent edges
            // If there's a previous edge, update its next pointer to point to the new location
            if (prev >= 0) {
                MappedByteBuffer prevMbb = channel.map(FileChannel.MapMode.READ_WRITE, prev + 12, 8);
                prevMbb.order(ByteOrder.nativeOrder());
                prevMbb.putLong(offset);
                prevMbb.force();
            }
            
            // If there's a next edge, update its prev pointer to point to the new location
            if (next >= 0) {
                MappedByteBuffer nextMbb = channel.map(FileChannel.MapMode.READ_WRITE, next + 20, 8);
                nextMbb.order(ByteOrder.nativeOrder());
                nextMbb.putLong(offset);
                nextMbb.force();
            }
        }
        // Truncate the file to remove the last edge
        channel.truncate(fileSize - BYTES_PER_EDGE);
    }

    /**
     * Decrement the edge count in the file header by the specified count.
     * 
     * @param filename Path to the edge list file
     * @param channel The file channel for the edge list file
     * @param count Number of edges to decrement
     * @throws IOException if file operations fail
     */
    private static void decrementHeader(String filename, FileChannel channel, int count) throws IOException {
        MappedByteBuffer headerMbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_SIZE);
        headerMbb.order(ByteOrder.nativeOrder());
        int edgeCount = headerMbb.getInt();
        headerMbb.rewind();
        headerMbb.putInt(edgeCount - count);
        headerMbb.force();
    }

    /**
     * Remove a single edge at the specified offset from the edge list file.
     * Updates linked list pointers and node index as necessary.
     * 
     * @param filename Path to the edge list file
     * @param offset Byte offset of the edge to remove
     * @throws IOException if file operations fail
     */
    public static void removeEdgeAtOffset(String filename, long offset) throws IOException {
        if (offset < 0 || offset % BYTES_PER_EDGE != HEADER_SIZE ||
            getNumEdges(filename) == 0) {
            return; // Nothing to remove
        }
        if (offset > getNumEdges(filename) * BYTES_PER_EDGE + HEADER_SIZE - BYTES_PER_EDGE) {
            throw new IOException("Invalid offset: " + offset);
        }

        try (RandomAccessFile raf = new RandomAccessFile(filename, "rw")) {
            FileChannel channel = raf.getChannel();

            if (offset > channel.size() - BYTES_PER_EDGE || offset % BYTES_PER_EDGE != HEADER_SIZE) {
                throw new IOException("Invalid offset: " + offset);
            }

            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, offset, BYTES_PER_EDGE);
            mbb.order(ByteOrder.nativeOrder());

            mbb.getInt();
            int destId = mbb.getInt();
            mbb.getInt();
            long next = mbb.getLong();
            long prev = mbb.getLong();

            updateLinkedList(filename, prev, next, channel);
            invalidateEntryAndCompactFile(filename, offset, channel);

            if (prev == NO_OFFSET) {
                // If this was the only edge for the destination, update node index
                String nodeFileName = filename.replace("_edges.dat", "_nodes.dat");
                NodeIndexMapper.updateIncomingEdgeOffset(nodeFileName, destId, next);
            }
            decrementHeader(filename, channel, 1);
        }
    }

    /**
     * Remove a single edge at the specified offset from the edge list file.
     * Updates linked list pointers and node index as necessary.
     *
     * @param filename Path to the edge list file
     * @param offset Byte offset of the edge to remove
     * @throws IOException if file operations fail
     */
    public static void removeLinkedList(String filename, long offset) throws IOException {
        if (offset < 0 || offset % BYTES_PER_EDGE != HEADER_SIZE ||
            getNumEdges(filename) == 0) {
            return; // Nothing to remove
        }
        if (offset > getNumEdges(filename) * BYTES_PER_EDGE + HEADER_SIZE - BYTES_PER_EDGE) {
            throw new IOException("Invalid offset: " + offset);
        }

        try (RandomAccessFile raf = new RandomAccessFile(filename, "rw")) {
            FileChannel channel = raf.getChannel();

            if (offset > channel.size() - BYTES_PER_EDGE || offset % BYTES_PER_EDGE != HEADER_SIZE) {
                throw new IOException("Invalid offset: " + offset);
            }

            long currentOffset = offset;
            int count = 0;
            int destId = -1;
            while (currentOffset >= 0) {
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, currentOffset, BYTES_PER_EDGE);
                mbb.order(ByteOrder.nativeOrder());

                mbb.getInt();
                destId = mbb.getInt();
                mbb.getInt();
                long next = mbb.getLong();
                long prev = mbb.getLong();

                updateLinkedList(filename, prev, next, channel);
                invalidateEntryAndCompactFile(filename, currentOffset, channel);

                currentOffset = next;
                count++;
            }
            decrementHeader(filename, channel, count);
            NodeIndexMapper.updateIncomingEdgeOffset(filename.replace("_edges.dat", "_nodes.dat"), destId, NO_OFFSET);
        }
    }

    public static List<Long> getOutgoingEdgeOffsets(String filename, int sourceId) throws IOException {
        List<Long> offsets = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(filename, "r");
             FileChannel channel = raf.getChannel()) {

            long size = channel.size();
            if (size % BYTES_PER_EDGE != HEADER_SIZE) {
                throw new IOException("Edge file size is not a multiple of edge size (28 bytes)");
            }

            int edgeCount = (int) (size / BYTES_PER_EDGE);
            MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, HEADER_SIZE, size - HEADER_SIZE);
            mbb.order(ByteOrder.nativeOrder());

            for (int i = 0; i < edgeCount; i++) {
                long edgeOffset = HEADER_SIZE + (long) i * BYTES_PER_EDGE;
                int srcId = mbb.getInt();
                mbb.getInt();  // Skip dest ID
                mbb.getInt();  // Skip weight
                mbb.getLong(); // Skip nextOffset
                mbb.getLong(); // Skip prevOffset

                if (srcId == sourceId) {
                    offsets.add(edgeOffset);
                }
            }
        }
        return offsets;
    }

    public static boolean edgeExists(String filename, int sourceId, int destId, long linkedListOffset) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(filename, "r");
             FileChannel channel = raf.getChannel()) {

            long currentOffset = linkedListOffset;
            while (currentOffset >= 0) {
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, currentOffset, BYTES_PER_EDGE);
                mbb.order(ByteOrder.nativeOrder());

                int srcId = mbb.getInt();
                int dstId = mbb.getInt();
                mbb.getInt(); // skip weight
                long nextOffset = mbb.getLong();
                mbb.getLong(); // skip prevOffset

                if (srcId == sourceId &&
                    dstId == destId) {
                    return true;
                }
                currentOffset = nextOffset;
            }
        }
        return false;
    }

    public static void removeEdge(String filename, int sourceId, int destId, long linkedListOffset) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(filename, "r");
             FileChannel channel = raf.getChannel()) {

            long currentOffset = linkedListOffset;
            while (currentOffset >= 0) {
                MappedByteBuffer mbb = channel.map(FileChannel.MapMode.READ_ONLY, currentOffset, BYTES_PER_EDGE);
                mbb.order(ByteOrder.nativeOrder());

                int srcId = mbb.getInt();
                int dstId = mbb.getInt();
                mbb.getInt(); // skip weight
                long nextOffset = mbb.getLong();
                mbb.getLong(); // skip prevOffset

                if (srcId == sourceId &&
                    dstId == destId) {
                    // Found the edge to remove
                    removeEdgeAtOffset(filename, currentOffset);
                    return;
                }
                currentOffset = nextOffset;
            }
        }
    }
}
