# Performance Optimizations for Static Camerini Algorithm

This document details the performance optimizations implemented to improve the static algorithm's execution time on large MLST datasets.

## Problem Statement

The static algorithm was taking an extremely long time to complete on exact graphs with large datasets. While graph construction was fast, the inference phase (specifically the lazy loading mechanism in `SerializableCameriniForest`) was the bottleneck.

---

## Optimization 1: Chunked Memory Mapping

### Problem
**Original Implementation** (`EdgeListMapper.loadLinkedList()`):
- Created a new 28-byte `MappedByteBuffer` for **every single edge** in a linked list
- For a node with 500 incoming edges: 500 separate memory mapping operations
- Each memory mapping operation has significant OS overhead

**Impact**: Memory mapping overhead dominated I/O time, making edge loading extremely slow.

### Solution
Implemented chunked memory mapping with 2MB buffers:
- Map large 2MB chunks instead of 28-byte segments
- Reuse the same buffer to read multiple edges
- Only remap when the next edge is outside the current chunk

**File**: `EdgeListMapper.java`

**Key Changes**:
```java
// Added constant
private static final long CHUNK_SIZE = 2 * 1024 * 1024; // 2MB chunks

// Updated loadLinkedList() to:
// 1. Track current chunk boundaries (currentChunkStart, currentChunkEnd)
// 2. Only remap when offset is outside current chunk
// 3. Use buffer positioning to read edges within chunk
```

### Performance Improvement
- **Before**: 500 edges = 500 memory map operations
- **After**: 500 edges = ~1-2 memory map operations (depending on layout)
- **Result**: 2x speedup observed

---

## Optimization 2: EdgeLoader Helper Class

### Problem
**Original Implementation**:
- Each call to `loadLinkedList()` opened and closed the edge file
- During inference, edges are loaded for hundreds/thousands of nodes
- File open/close overhead repeated for every node

**Impact**: While chunked mapping helped, file lifecycle overhead remained.

### Solution
Created `EdgeLoader` helper class that keeps the file channel open:
- Implements `AutoCloseable` for proper resource management
- Opened once at the start of `inferPhylogeny()`
- Reused across all lazy queue initializations
- Automatically closed when inference completes

**Files**: 
- `EdgeListMapper.java` - Added `EdgeLoader` inner class
- `GraphMapper.java` - Added overload accepting `EdgeLoader`
- `SerializableCameriniForest.java` - Integrated `EdgeLoader` lifecycle

**Key Changes**:
```java
// EdgeLoader class in EdgeListMapper
public static class EdgeLoader implements AutoCloseable {
    private final RandomAccessFile raf;
    private final FileChannel channel;
    
    public List<Edge> loadLinkedList(long offset, Map<Integer, Node> nodeMap) {
        // Uses shared channel instead of opening new file
    }
}

// In SerializableCameriniForest.inferPhylogeny()
try (EdgeListMapper.EdgeLoader loader = new EdgeListMapper.EdgeLoader(edgeFile)) {
    this.edgeLoader = loader;
    return super.inferPhylogeny(graph);
}
```

### Performance Improvement
- **Before**: 1000 nodes = 1000 file open/close operations
- **After**: 1000 nodes = 1 file open/close operation
- **Expected speedup**: 2-5x
- **Actual result**: No noticeable improvement (bottleneck was elsewhere)

---

## Optimization 3: Node Object Reuse

### Problem
**Original Implementation**:
```java
edges.add(new Edge(new Node(sourceId), new Node(destId), weight));
```
- Created brand new `Node` objects for every edge loaded
- Nodes already existed in `nodeMap`
- Unnecessary object allocation and GC pressure

### Solution
Reuse existing `Node` objects from `nodeMap`:

**File**: `EdgeListMapper.EdgeLoader.loadLinkedList()`

**Key Changes**:
```java
// Reuse Node objects from nodeMap if available
Node source = nodeMap != null ? nodeMap.get(sourceId) : null;
Node dest = nodeMap != null ? nodeMap.get(destId) : null;
if (source == null) source = new Node(sourceId);
if (dest == null) dest = new Node(destId);

edges.add(new Edge(source, dest, weight));
```

### Performance Improvement
- Reduces object allocation
- Reduces Garbage Collector pressure
- Ensures Node identity consistency
- **Bundled with other optimizations**

---

## Optimization 4: Preload Node Offsets (The Real Fix)

### Problem Identified
**Root Cause Analysis**: Neither EdgeLoader nor chunked mapping provided significant speedup because the real bottleneck was:

**`NodeIndexMapper.getIncomingEdgeOffset()`**:
```java
public static long getIncomingEdgeOffset(String fileName, int nodeId) throws IOException {
    try (RandomAccessFile raf = new RandomAccessFile(fileName, "r")) {
        // Opens node file EVERY TIME it's called
        // ...
    }
}
```

During lazy loading:
1. `initializeQueueForNode()` called for each node
2. Calls `GraphMapper.getIncomingEdges()` 
3. Which calls `NodeIndexMapper.getIncomingEdgeOffset()` - **opens node file**
4. Then loads edges via EdgeLoader - **edge file already open**

**Impact**: 
- 1000 nodes = 1000 node file opens/closes
- EdgeLoader optimization only fixed edge file, not node file
- Node file I/O was the actual bottleneck

### Solution
Preload all node offsets once at initialization:

**Files**: 
- `SerializableCameriniForest.java` - Added offset caching

**Key Changes**:
```java
// Added field
private Map<Integer, Long> nodeOffsetCache;

// Added preload method
private Map<Integer, Long> preloadNodeOffsets(String baseName) throws IOException {
    String nodeFile = baseName + "_nodes.dat";
    Set<Integer> allNodeIds = nodeMap.keySet();
    
    // Load ALL offsets in ONE batch operation
    return NodeIndexMapper.getIncomingEdgeOffsetsBatch(nodeFile, allNodeIds);
}

// Called in constructors
this.nodeOffsetCache = preloadNodeOffsets(baseName);

// Updated initializeQueueForNode() to use cache
private void initializeQueueForNode(Node v) throws IOException {
    // Get offset from preloaded cache (no file I/O!)
    Long offset = nodeOffsetCache.get(v.getId());
    
    if (offset == null || offset < 0) {
        return; // No incoming edges
    }
    
    // Load edges via EdgeLoader (already open)
    List<Edge> incomingEdges = edgeLoader.loadLinkedList(offset, nodeMap);
    // ...
}
```

### Performance Improvement
- **Before**: 1000 nodes = 1000 node file opens + 1000 edge file opens
- **After**: 1000 nodes = 1 node file open + 1 edge file open
- All lazy loading is now file-open-free
- **Practically removed any I/O overhead seen with memory mapping for a graph with ~2.4k nodes **

---

## Combined Architecture

### Lazy Loading Flow (After All Optimizations)

**Initialization** (once):
1. Load `nodeMap` from node file
2. Preload `nodeOffsetCache` from node file (batch operation)
3. Create empty queues marked as uninitialized

**During `inferPhylogeny()`** (once):
1. Open `EdgeLoader` for edge file
2. Run Camerini algorithm

**During Lazy Queue Loading** (per node, as needed):
1. `getQueue(node)` called
2. Check if queue initialized
3. If not:
   - Get offset from `nodeOffsetCache` (in-memory lookup, no I/O)
   - Load edges via `EdgeLoader.loadLinkedList()` (shared channel, chunked mapping)
   - Insert edges into queue
4. Return queue

### File Operations Summary

| Operation | Before Optimizations | After Optimizations |
|-----------|---------------------|---------------------|
| Node file opens | N (# of nodes) | 1 (preload batch) |
| Edge file opens | N (# of nodes) | 1 (EdgeLoader) |
| Memory mappings per edge | 1 (28 bytes) | ~1/70,000 (2MB chunks) |
| Node object allocations | M (# of edges) | 0 (reused from nodeMap) |

Where N = number of nodes, M = number of edges

---

## Expected Overall Performance

### Theoretical Improvements
- **Chunked mapping**
- **EdgeLoader**
- **Node reuse**
- **Offset caching**

---

## Files Modified

1. **`EdgeListMapper.java`**
   - Added `CHUNK_SIZE` constant
   - Added `EdgeLoader` inner class with chunked loading
   - Updated `loadLinkedList()` with chunked memory mapping
   - Incorporated node object reuse

2. **`GraphMapper.java`**
   - Added `getIncomingEdges()` overload accepting `EdgeLoader`

3. **`SerializableCameriniForest.java`**
   - Added `edgeLoader` field
   - Added `nodeOffsetCache` field
   - Added `preloadNodeOffsets()` method
   - Updated constructors to preload offsets
   - Updated `initializeQueueForNode()` to use cache and EdgeLoader
   - Overridden `inferPhylogeny()` to manage EdgeLoader lifecycle
   - Updated `setBaseName()` to initialize cache

---

## Testing & Validation

Run the static algorithm with your MLST dataset:
```bash
MAVEN_OPTS="-Xms2g -Xmx6g -verbose:gc" mvn exec:java \
  -Dexec.mainClass="optimalarborescence.Main" \
  -Dexec.args="mlst /path/to/input /path/to/output add"
```

Monitor:
- Overall execution time (should be significantly faster)
- GC activity (should be reduced with node reuse)
- File descriptor usage (should be minimal during inference)

---

## Backward Compatibility

All optimizations maintain backward compatibility:
- In-memory mode unchanged (uses `useMemoryMappedFiles` flag)
- Original `loadLinkedList()` method still available
- Fallback paths for edge cases
- Existing tests continue to pass

---

## Future Optimization Opportunities

If further speedup is needed:

1. **Parallel Queue Initialization**: Load multiple queues concurrently
3. **Edge List Compression**: Reduce file size for faster I/O
4. **Custom Memory Allocator**: Pool Node objects for reuse
5. **Profile-Guided Optimization**: Use profiling to identify remaining hotspots
