# Memory-Mapped Graph Storage Implementation

## Overview

This implementation provides memory-mapped file storage for graphs using three separate files:

1. **Edge List File** (`*_edges.dat`): Edges sorted by destination node
2. **Node Index File** (`*_nodes.dat`): Node metadata and IDs
3. **MLST Data File** (`*_mlst.dat`): MLST sequences and incoming edge offsets

## File Formats

### Edge List File (`*_edges.dat`)

Edges are stored sorted by destination node ID to enable efficient incoming edge queries.

```
Format: [source_id, dest_id, weight] repeated
Bytes:  [4 bytes,   4 bytes, 4 bytes] = 12 bytes per edge
```

**Example** (from test output):
```
Offset  | Source | Dest | Weight | Description
--------|--------|------|--------|------------------
0x00    |   3    |  0   |   6    | Edge 3→0, weight 6
0x0C    |   0    |  1   |   5    | Edge 0→1, weight 5
0x18    |   0    |  2   |   3    | Edge 0→2, weight 3
0x24    |   1    |  2   |   2    | Edge 1→2, weight 2
0x30    |   1    |  3   |   4    | Edge 1→3, weight 4
0x3C    |   2    |  3   |   1    | Edge 2→3, weight 1
```

### Node Index File (`*_nodes.dat`)

Stores metadata and node IDs as an integer array.

```
Format: [num_nodes, mlst_length, node_id_0, node_id_1, ...]
Bytes:  [4 bytes,   4 bytes,     4 bytes,   4 bytes,  ...]
```

**Example**:
```
Offset  | Value | Description
--------|-------|-------------------------
0x00    |   4   | Number of nodes
0x04    |  20   | MLST data length (bytes)
0x08    |   0   | Node ID 0
0x0C    |   1   | Node ID 1
0x10    |   2   | Node ID 2
0x14    |   3   | Node ID 3
```

### MLST Data File (`*_mlst.dat`)

Stores MLST sequences (fixed length) and incoming edge offsets for each node.

```
Format per node: [mlst_data (padded), incoming_edge_offset]
Bytes:           [mlst_length bytes,  8 bytes]
```

**Example** (mlst_length = 20):
```
Node | MLST Data          | Offset | Incoming Edges
-----|--------------------|---------|-----------------
  0  | "ATCG" + padding   |  0x00  | 3→0
  1  | "GCTA" + padding   |  0x0C  | 0→1
  2  | "TGAC" + padding   |  0x18  | 0→2, 1→2
  3  | "CGAT" + padding   |  0x30  | 1→3, 2→3
```

## Implementation Components

### 1. EdgeListMapper

**Key Methods:**
- `saveEdgesToMappedFile()`: Saves edges sorted by destination, returns incoming edge offset map
- `loadEdgesFromMappedFile()`: Loads all edges from file
- `readEdgeAtOffset()`: Reads a single edge at specific byte offset

**Usage:**
```java
// Save edges
Map<Integer, Long> offsets = EdgeListMapper.saveEdgesToMappedFile(edges, "graph_edges.dat");

// Load edges
Map<Integer, Node> nodeMap = ...; // Load nodes first
List<Edge> edges = EdgeListMapper.loadEdgesFromMappedFile("graph_edges.dat", nodeMap);
```

### 2. NodeIndexMapper

**Key Methods:**
- `saveGraph()`: Saves node index array and MLST data with offsets
- `loadNodes()`: Loads all nodes from files
- `getIncomingEdgeOffset()`: Gets offset for specific node's incoming edges

**Usage:**
```java
// Save nodes
NodeIndexMapper.saveGraph(graph, mlstLength, incomingEdgeOffsets, 
                         "graph_nodes.dat", "graph_mlst.dat");

// Load nodes
Map<Integer, Node> nodes = NodeIndexMapper.loadNodes("graph_nodes.dat", "graph_mlst.dat");

// Get offset for node 2
long offset = NodeIndexMapper.getIncomingEdgeOffset("graph_mlst.dat", 2, mlstLength);
```

### 3. GraphMapper (High-Level API)

**Key Methods:**
- `saveGraph()`: Saves complete graph to three files
- `loadGraph()`: Loads complete graph from three files
- `getIncomingEdges()`: Efficiently retrieves incoming edges for a specific node

**Usage:**
```java
// Save complete graph
GraphMapper.saveGraph(graph, mlstLength, "mygraph");
// Creates: mygraph_edges.dat, mygraph_nodes.dat, mygraph_mlst.dat

// Load complete graph
Graph loadedGraph = GraphMapper.loadGraph("mygraph");

// Query incoming edges for node 2
Map<Integer, Node> nodeMap = NodeIndexMapper.loadNodes(...);
List<Edge> incoming = GraphMapper.getIncomingEdges("mygraph", 2, mlstLength, nodeMap);
```

## Key Design Decisions

### Why sort by destination?

Edges are sorted by destination to enable efficient incoming edge queries:
- All incoming edges for node N are stored contiguously in the file
- The MLST data file stores the byte offset to the first incoming edge
- Can read all incoming edges with a single sequential read

### Why fixed MLST length?

Fixed MLST length enables direct indexing:
- Entry for node N is at position: `N * (mlstLength + 8)`
- Can seek directly to any node's data without scanning
- Simplifies memory mapping and offset calculations

### Why separate MLST data file?

Separation enables:
- Independent access to node metadata vs. graph structure
- Efficient updates to MLST data without rewriting edges
- Smaller memory footprint when only indices are needed

## Performance Characteristics

### Space Complexity
- Edge file: `12 * num_edges` bytes
- Node index file: `4 * (num_nodes + 2)` bytes
- MLST data file: `(mlstLength + 8) * (maxNodeId + 1)` bytes

### Time Complexity
- Save graph: O(E log E) for sorting + O(E + N) for writing
- Load graph: O(E + N) for reading
- Query incoming edges for node: O(d) where d is in-degree
- Random access to node data: O(1)

## Replacing Graph.exportEdgeListAndIndex()

The old `Graph.exportEdgeListAndIndex()` method used `DataOutputStream` and sorted by source.
This implementation:

✓ Uses memory-mapped files (MappedByteBuffer) for better performance
✓ Sorts by destination for incoming edge tracking
✓ Separates concerns (edges, indices, MLST data)
✓ Provides both high-level (GraphMapper) and low-level (EdgeListMapper, NodeIndexMapper) APIs
✓ Supports efficient random access and partial graph queries

## Testing

Run the test with:
```bash
mvn compile
java -cp target/classes optimalarborescence.memorymapper.TestMemoryMappedGraph
```

The test creates a 4-node, 6-edge graph and verifies:
- ✓ Save/load cycle preserves all data
- ✓ Edges are sorted by destination
- ✓ Incoming edge offsets are correct
- ✓ Individual node queries work correctly
- ✓ MLST data is preserved with proper padding

## Binary File Inspection

Use hexdump to inspect the binary files:
```bash
hexdump -C test_graph_edges.dat  # View edge data
hexdump -C test_graph_nodes.dat  # View node indices
hexdump -C test_graph_mlst.dat   # View MLST data and offsets
```
