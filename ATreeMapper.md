# ATreeMapper Documentation

## Overview

`ATreeMapper` provides memory-mapped file operations for storing and loading ATree (Augmented Tree) forests. An ATree forest consists of multiple root nodes, where each ATree represents a partially contracted subgraph in the fully dynamic minimum spanning arborescence algorithm.

The implementation uses an **offset-based design** with parent references stored as byte offsets rather than node IDs, enabling O(1) direct access to parent nodes without requiring ID-to-offset mapping.

## Design Decisions

### Offset-Based Architecture

The mapper uses **absolute byte offsets** instead of node IDs for several key advantages:

- **Direct Access**: O(1) parent access without maintaining an ID-to-offset lookup table
- **Space Efficiency**: Nodes stored consecutively without gaps
- **Simplicity**: Single file position value serves both as reference and file position

### Three-File Structure

The implementation uses three separate memory-mapped files:

1. **`{baseName}_atree_index.dat`**: Primary node data with metadata
2. **`{baseName}_atree_children.dat`**: Variable-length children lists
3. **`{baseName}_atree_contracted.dat`**: Variable-length contracted edge lists (for c-nodes)

This separation allows:
- Efficient partial loading (can load nodes without their children/edges)
- Better memory locality for different access patterns
- Reduced file size for simple trees with few children

## File Formats

### Index File Format

```
[Header]
- numNodes (4 bytes): Total number of nodes in forest
- numRoots (4 bytes): Number of root nodes
- root_offsets[] (8 bytes each): Array of absolute offsets to root nodes

[Node Data] (40 bytes per node)
For each node:
- edge_src (4 bytes): Source node ID (-1 for roots)
- edge_dst (4 bytes): Destination node ID (-1 for roots)
- edge_weight (4 bytes): Edge weight (-1 for roots)
- cost (4 bytes): Cost y of the edge
- parent_offset (8 bytes): Absolute offset to parent node (-1 for roots)
- children_offset (8 bytes): Offset in children file (-1 if no children)
- contracted_offset (8 bytes): Offset in contracted file (-1 for simple nodes)
```

**Header Size**: `8 + (numRoots × 8)` bytes

### Children File Format

```
For each node with children:
- numChildren (4 bytes): Number of children
- child_offsets[] (8 bytes each): Absolute offsets to child nodes in index file
```

### Contracted Edges File Format

```
For each c-node with contracted edges:
- numEdges (4 bytes): Number of contracted edges
- edges[] (12 bytes each):
  - source_id (4 bytes)
  - dest_id (4 bytes)
  - weight (4 bytes)
```

## Offset Calculation

### Relative vs Absolute Offsets

**Relative Offset**: Calculated from the start of the node data section (excludes header)
```java
relativeOffset = nodeIndex × BYTES_PER_NODE
```

**Absolute Offset**: Calculated from the start of the entire file (includes header)
```java
absoluteOffset = headerSize + relativeOffset
headerSize = 8 + (numRoots × 8)
```

**Example with 1 root**:
- Header size: 16 bytes
- Node 0 (root): absolute offset = 16
- Node 1: absolute offset = 56 (16 + 40)
- Node 2: absolute offset = 96 (16 + 80)

**Why Both?**
- Relative offsets used during BFS traversal (header size unknown initially)
- Absolute offsets required for file I/O operations (`FileChannel.map()` needs positions from file start)
- Conversion happens when writing to files (all stored offsets are absolute)

## Key Implementation Details

### Node Collection (BFS Traversal)

```java
Queue<ATreeNode> queue = new LinkedList<>(roots);
while (!queue.isEmpty()) {
    ATreeNode current = queue.poll();
    if (!nodeOffsets.containsKey(current)) {
        long offset = calculateNodeOffset(allNodes.size());
        nodeOffsets.put(current, offset);
        allNodes.add(current);
        queue.addAll(current.getChildren());
    }
}
```

### Circular Reference Prevention

Nodes are added to the `loadedNodes` cache **immediately** upon creation, before loading their parent or children. This prevents infinite recursion when:
1. Loading a root node
2. Root tries to load its children
3. Child tries to load its parent (the root)
4. Cache hit prevents re-loading the root

### Field Shadowing Issue

**Problem**: `ATreeNode` shadows fields from its parent class `TarjanForestNode`:

```java
// TarjanForestNode (package-private fields)
List<TarjanForestNode> children;

// ATreeNode (shadows parent's field)
protected List<ATreeNode> children;
```

When calling `getChildren()`, it returns the parent's field (`List<TarjanForestNode>`), but we need `List<ATreeNode>`.

**Solution**: Use type casting with `@SuppressWarnings("unchecked")`:

```java
@SuppressWarnings("unchecked")
List<ATreeNode> children = (List<ATreeNode>) (List<?>) node.getChildren();
```

This is **safe** because:
1. We control the instantiation (always add `ATreeNode` instances)
2. The parent's `children` field is kept synchronized via `setChildren()`
3. Java's type erasure means the cast only affects compile-time checking

**Alternative Solutions**:
- Make `TarjanForestNode` generic: `TarjanForestNode<T extends TarjanForestNode<T>>`
- Remove field shadowing and cast at call sites
- Use separate `getATreeChildren()` method

The `@SuppressWarnings` approach was chosen because:
- Parent class cannot be modified (used elsewhere)
- Casts are type-safe in this context
- Minimal code impact (11 locations)
- Clear documentation of intentional type conversion

### Edge Field Shadowing Fix

Similarly, `ATreeNode` shadows the `edge` field. The constructor must set **both** fields:

```java
public ATreeNode(Edge edge, ...) {
    super(edge);        // Sets parent's edge field
    this.edge = edge;   // Sets our shadowed field (used by getEdge())
    // ...
}
```

Without setting `this.edge`, `getEdge()` would return `null` even though the parent's field is set.

## Public API

### Saving a Forest

```java
public static void saveATreeForest(
    List<ATreeNode> roots,
    Map<Integer, Node> graphNodes,
    String baseName
) throws IOException
```

**Parameters**:
- `roots`: List of ATree root nodes to save
- `graphNodes`: Map of node IDs to Node objects (for edge reconstruction during load)
- `baseName`: Base name for output files (will create 3 files)

**Example**:
```java
List<ATreeNode> roots = buildForest();
Map<Integer, Node> nodeMap = buildNodeMap();
ATreeMapper.saveATreeForest(roots, nodeMap, "my_forest");
// Creates: my_forest_atree_index.dat
//          my_forest_atree_children.dat
//          my_forest_atree_contracted.dat
```

### Loading a Forest

```java
public static List<ATreeNode> loadATreeForest(
    String baseName,
    Map<Integer, Node> graphNodes
) throws IOException
```

**Parameters**:
- `baseName`: Base name for input files
- `graphNodes`: Map of node IDs to Node objects (same as used during save)

**Returns**: List of ATree root nodes

**Example**:
```java
Map<Integer, Node> nodeMap = buildNodeMap();
List<ATreeNode> roots = ATreeMapper.loadATreeForest("my_forest", nodeMap);
```

## Implementation Notes

### Memory Efficiency

- **Node size**: 40 bytes per node (fixed)
- **Children overhead**: 4 + (8 × numChildren) bytes per node with children
- **Contracted edges**: 4 + (12 × numEdges) bytes per c-node

For a forest with 1000 nodes, 500 with children (avg 2 children), 100 c-nodes (avg 3 edges):
- Index: 8 + 8 + 1000×40 = 40,016 bytes (~39 KB)
- Children: 500×(4 + 2×8) = 10,000 bytes (~10 KB)
- Contracted: 100×(4 + 3×12) = 4,000 bytes (~4 KB)
- **Total**: ~53 KB

### Error Handling

The implementation includes several validation checks:

1. **File existence and size**: Prevents mapping empty or non-existent files
2. **Offset validation**: Ensures offsets are not negative (catches corruption early)
3. **Bounds checking**: Verifies offsets don't exceed file size before mapping

### Performance Characteristics

- **Save**: O(n) where n is total number of nodes (single BFS traversal)
- **Load (full forest)**: O(n) with recursive loading and caching
- **Load (single subtree)**: O(k) where k is subtree size (partial loading)
- **Memory mapping overhead**: Minimal, OS handles paging

## Usage Example

```java
// Building a simple tree
Node n0 = new Node("AAAA", 0);
Node n1 = new Node("CCCC", 1);
Node n2 = new Node("GGGG", 2);
Map<Integer, Node> nodeMap = Map.of(0, n0, 1, n1, 2, n2);

Edge e1 = new Edge(n0, n1, 5);
Edge e2 = new Edge(n0, n2, 3);

ATreeNode root = new ATreeNode(null, 0, true, null, 0);
ATreeNode child1 = new ATreeNode(e1, 5, root, true, null, 1);
ATreeNode child2 = new ATreeNode(e2, 3, root, true, null, 2);

root.setChildren(List.of(child1, child2));

// Save forest
ATreeMapper.saveATreeForest(List.of(root), nodeMap, "example");

// Load forest
List<ATreeNode> loadedRoots = ATreeMapper.loadATreeForest("example", nodeMap);

// Verify
ATreeNode loadedRoot = loadedRoots.get(0);
assert loadedRoot.getChildren().size() == 2;
assert loadedRoot.getChildren().get(0).getCost() == 5;
```

---

## Unit Tests Summary

The `ATreeMapperTest` class provides comprehensive testing with 9 test cases covering various scenarios:

### Test Cases

#### 1. `testSaveAndLoadEmptyForest`
- **Purpose**: Verifies handling of edge case with no nodes
- **Validates**: Empty list saved and loaded correctly, no files contain data

#### 2. `testSaveAndLoadSingleRootNode`
- **Purpose**: Tests minimal tree with only a root node
- **Validates**: 
  - Root node with null edge saved/loaded correctly
  - No children list created
  - Node marked as simple node

#### 3. `testSaveAndLoadSimpleTree`
- **Purpose**: Tests basic tree structure (root + 2 children)
- **Validates**:
  - Parent-child relationships preserved
  - Edge data (source, destination, weight) correct
  - Costs preserved for all nodes
  - Children accessible via `getChildren()`

#### 4. `testSaveAndLoadTreeWithCNode`
- **Purpose**: Tests complex node (c-node) with contracted edges
- **Validates**:
  - Node marked as non-simple (c-node)
  - Contracted edges list saved and loaded
  - Multiple contracted edges preserved with correct attributes

#### 5. `testSaveAndLoadMultipleRoots`
- **Purpose**: Tests forest with multiple independent trees
- **Validates**:
  - Multiple roots loaded as separate trees
  - Each tree maintains its own structure
  - No cross-contamination between trees

#### 6. `testSaveAndLoadDeepTree`
- **Purpose**: Tests tree with 4 levels (root → child → grandchild → great-grandchild)
- **Validates**:
  - Deep hierarchies preserved
  - Each level accessible through traversal
  - Parent-child links maintained at all levels

#### 7. `testSaveAndLoadWideTree`
- **Purpose**: Tests tree with many children (1 root + 5 children)
- **Validates**:
  - Large children lists handled correctly
  - All children accessible in order
  - Parent references correct for all children

#### 8. `testFileSizes`
- **Purpose**: Validates file format correctness through size checks
- **Validates**:
  - Index file: `headerSize + (numNodes × 40)` bytes
  - Children file: `4 + (numChildren × 8)` bytes per node
  - Contracted file: `0` bytes when no contracted edges

#### 9. `testParentChildRelationships`
- **Purpose**: Focuses specifically on bidirectional relationships
- **Validates**:
  - Root has null parent
  - Children point to correct parent
  - Grandchildren point to correct parent
  - Relationships symmetric (parent knows children, children know parent)

### Test Infrastructure

**Setup** (`@Before`):
- Creates 6 test nodes with sample MLST data
- Builds node map for all tests

**Teardown** (`@After`):
- Cleans up all generated test files
- Prevents test artifacts from persisting

**Test Data Pattern**:
```java
Node(0, "AAAA")  →  Node(1, "CCCC")  (weight: 5)
Node(0, "AAAA")  →  Node(2, "GGGG")  (weight: 3)
```

### Coverage Summary

The test suite achieves:
- ✅ **Edge cases**: Empty forest, single node
- ✅ **Simple trees**: Basic parent-child relationships
- ✅ **Complex nodes**: C-nodes with contracted edges
- ✅ **Forest structures**: Multiple independent trees
- ✅ **Tree depth**: Multi-level hierarchies
- ✅ **Tree width**: Nodes with many children
- ✅ **File format**: Correct byte sizes and structure
- ✅ **Data integrity**: All attributes preserved through save/load cycle

**Total**: 9 tests, all passing ✓

### Key Assertions

Most tests follow this pattern:
1. **Build** ATree structure in memory
2. **Save** to files using `ATreeMapper.saveATreeForest()`
3. **Load** from files using `ATreeMapper.loadATreeForest()`
4. **Verify** loaded structure matches original:
   - Node count and structure
   - Edge attributes (source, destination, weight)
   - Costs (y values)
   - Parent-child relationships
   - Simple vs complex node types
   - Contracted edges (when applicable)

The tests validate both the **file format** (correct bytes written) and **data integrity** (correct data reconstructed on load).
