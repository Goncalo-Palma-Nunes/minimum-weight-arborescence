# Chunked Memory Mapping Implementation

## Problem
Java's `MappedByteBuffer` has a size limit of `Integer.MAX_VALUE` (2,147,483,647 bytes ≈ 2GB). When attempting to memory-map files larger than this limit, an `IllegalArgumentException: Size exceeds Integer.MAX_VALUE` is thrown.

This became an issue when processing datasets with ~200,000 nodes, where the combined size of node data and edges exceeded the 2GB limit.

## Solution
Implemented chunked memory mapping that maps regions of files instead of mapping the entire file at once. This allows the code to work with arbitrarily large files by processing them in manageable chunks.

### Key Changes

#### Constants
Added a constant for the maximum safe mapping size (1.5GB to leave headroom):
```java
private static final long MAX_MAPPING_SIZE = 1_500_000_000L; // 1.5GB safe limit per mapping
```

#### Chunk Calculation Helper Methods (NodeIndexMapper)
```java
/**
 * Calculate which chunk (mapping region) a node belongs to based on entry size.
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
```

### Modified Methods

#### NodeIndexMapper.java
1. **saveGraph()** - Write nodes in chunks
   - Calculates `nodesPerChunk` based on entry size
   - Loops through nodes in batches
   - Maps only the required region for each batch
   - Forces each chunk to disk before moving to next

2. **loadNodes()** - Read nodes in chunks
   - Reads header separately
   - Processes nodes in batches matching the chunk size
   - Maps each region as needed

3. **getIncomingEdgeOffsetsBatch()** - Query node offsets in chunks
   - Iterates through file in chunks
   - Can exit early when all requested nodes are found

4. **addNodesBatch()** - Append nodes in chunks
   - Pre-allocates file space
   - Writes new nodes in batches under the 2GB limit

#### EdgeListMapper.java
1. **saveEdgesToMappedFile()** - Write edges in chunks
   - Writes header separately
   - Builds edge entries and links in memory
   - Writes edge data in chunks of ~53 million edges each (at 28 bytes per edge)

2. **loadEdgesFromMappedFile(String, Map<Integer, Node>)** - Read edges with nodeMap in chunks
   - Calculates `edgesPerChunk`
   - Processes edges in batches

3. **loadEdgesFromMappedFile(String)** - Read edges without nodeMap in chunks
   - Similar to above but creates minimal node objects on the fly

## Benefits
1. **No File Size Limit**: Can handle files of any size (limited only by disk space)
2. **Memory Efficiency**: Only maps the actively-used portion of the file
3. **Backward Compatible**: File format remains unchanged; only mapping strategy differs
4. **Transparent**: Existing code using these mappers works without modification

## Performance Considerations
- **Chunk Size**: 1.5GB provides good balance between memory usage and mapping overhead
- **Sequential Access**: The implementation is optimized for sequential reading/writing
- **Force Calls**: Each chunk is explicitly forced to disk for reliability

## Testing
All 466 existing tests pass, including:
- Graph mapping and serialization tests
- Edge list operations
- Node index operations
- Dynamic algorithm tests
- Large dataset tests

## Usage Example
The chunking is transparent to callers:
```java
// Same API as before - chunking happens automatically
NodeIndexMapper.saveGraph(nodes, mlstLength, offsets, "nodes.dat");
Map<Integer, Node> loadedNodes = NodeIndexMapper.loadNodes("nodes.dat");

EdgeListMapper.saveEdgesToMappedFile(edges, "edges.dat");
List<Edge> loadedEdges = EdgeListMapper.loadEdgesFromMappedFile("edges.dat", nodeMap);
```

## Files Modified
- `src/main/java/optimalarborescence/memorymapper/NodeIndexMapper.java`
- `src/main/java/optimalarborescence/memorymapper/EdgeListMapper.java`

## Date
2026-01-18
