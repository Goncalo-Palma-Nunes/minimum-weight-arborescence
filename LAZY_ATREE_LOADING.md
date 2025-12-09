# Lazy ATree Loading Implementation

## Overview

This document describes the implementation of lazy ATree loading for the Serializable Dynamic Tarjan Arborescence algorithm. This feature reduces memory consumption during consecutive dynamic operations by loading ATree nodes on-demand rather than loading the entire forest upfront.

## Architecture

The implementation follows a **two-level lazy loading strategy**:

1. **Edge Lazy-Loading** (existing):
   - Modified graph edges stored in memory-mapped files
   - Edge queues initialized on-demand via `getQueue()` override
   - Reduces memory for large graphs during algorithm execution

2. **ATree Lazy-Loading** (new):
   - ATree forest stored in memory-mapped files
   - Roots loaded without children via `loadATreeRootsLazy()`
   - Children loaded on-demand when `getATreeChildren()` is first accessed
   - Reduces memory for deep/wide ATree forests during dynamic operations

## Design Pattern

- **Opt-in**: Enabled via specific constructor
- **Transparent**: Automatic loading on first access (no manual loading calls)
- **Hybrid**: State stored in domain objects (`ATreeNode`), file I/O in mapper (`ATreeMapper`)
- **Memory**: All loaded nodes kept in memory (future enhancement: LRU eviction)

## Modified Classes

### 1. ATreeNode (Domain Class)

**Added lazy state fields** (lines 54-63):
```java
private boolean childrenLoaded = true;  // Default true for in-memory nodes
private Long nodeOffset;                 // File position for lazy nodes
private String baseName;                 // File name for lazy nodes  
private java.util.Map<Integer, optimalarborescence.graph.Node> graphNodes;
```

**New constructor for lazy nodes** (lines 78-100):
- Takes offset, baseName, graphNodes parameters
- Sets `childrenLoaded = false`

**Overridden getATreeChildren()** (lines 143-171):
- Checks if children need loading
- Calls `ATreeMapper.loadChildren()` on first access
- Sets `childrenLoaded = true` after loading
- Returns children transparently

**Helper methods** (lines 173-181):
- `isLazyLoadable()`: Checks if node is configured for lazy loading
- `areChildrenLoaded()`: Returns loading status

### 2. ATreeMapper (File I/O Mapper)

**Updated class JavaDoc** (lines 20-37):
- Added lazy loading documentation
- Noted future LRU eviction option

**loadATreeRootsLazy()** (lines 263-288):
```java
public static List<ATreeNode> loadATreeRootsLazy(String baseName, 
                                                  Map<Integer, Node> graphNodes)
```
- Reads header to get root offsets
- Loads only root nodes (children marked for lazy loading)
- Returns list of roots with `childrenLoaded = false`

**loadSingleNodeLazy()** (lines 290-326, private):
- Loads one node from file at given offset
- Skips children offsets (to be loaded later)
- Creates ATreeNode with lazy constructor

**loadChildren()** (lines 328-369):
```java
public static void loadChildren(ATreeNode parent, String baseName, 
                               long parentOffset, Map<Integer, Node> graphNodes)
```
- Called by `getATreeChildren()` when children needed
- Reads child offsets from file
- Loads each child as lazy node
- Sets parent-child relationships

### 3. SerializableDynamicTarjanArborescence (Orchestrator)

**Updated class JavaDoc** (lines 1-45):
- Documents both lazy-loading modes
- Notes performance characteristics
- Mentions future LRU option

**New lazy ATree constructor** (lines 108-132):
```java
public SerializableDynamicTarjanArborescence(String baseName, int mlstLength, 
                                            Graph originalGraph)
```
- Takes baseName, mlstLength, originalGraph
- Calls `loadATreeRootsLazy()` internally
- Delegates to existing 5-parameter constructor

**loadATreeRootsLazy() helper** (lines 133-139):
- Loads node map from modified graph (**IMPORTANT**: uses modified graph nodes, not original)
- Calls `ATreeMapper.loadATreeRootsLazy()`

**Updated setBaseName()** (lines 179-195):
- Now saves both modified graph AND ATree forest
- Calls new `saveATreeForest()` method

**saveATreeForest() helper** (lines 197-206):
- Builds graphNodes map from modified graph
- Calls `ATreeMapper.saveATreeForest()`

## Usage Patterns

### Option 1: In-Memory with Edge Lazy-Loading
```java
List<ATreeNode> roots = new ArrayList<>();
SerializableDynamicTarjanArborescence camerini = 
    new SerializableDynamicTarjanArborescence(
        roots, new ArrayList<>(), new HashMap<>(), graph);
SerializableFullyDynamicArborescence algo = 
    new SerializableFullyDynamicArborescence(graph, roots, camerini);

algo.inferPhylogeny(graph);

// Enable lazy edge loading
camerini.setBaseName("project", mlstLength);

// Perform dynamic operations (edges load lazily)
algo.removeEdge(edge);
```

### Option 2: Lazy ATree Loading from Saved State
```java
// Load with lazy ATrees
SerializableDynamicTarjanArborescence camerini = 
    new SerializableDynamicTarjanArborescence("project", mlstLength, graph);

// ATrees load lazily during dynamic operations
// No need to call inferPhylogeny() - state already saved
```

### Option 3: Combined Lazy Loading (Edges + ATrees)
```java
// Phase 1: Initial computation and save
List<ATreeNode> roots = new ArrayList<>();
SerializableDynamicTarjanArborescence camerini1 = 
    new SerializableDynamicTarjanArborescence(
        roots, new ArrayList<>(), new HashMap<>(), graph);
SerializableFullyDynamicArborescence algo1 = 
    new SerializableFullyDynamicArborescence(graph, roots, camerini1);

algo1.inferPhylogeny(graph);
camerini1.setBaseName("project", mlstLength); // Saves graph + ATrees

// Phase 2: Load for consecutive operations
SerializableDynamicTarjanArborescence camerini2 = 
    new SerializableDynamicTarjanArborescence("project", mlstLength, graph);
SerializableFullyDynamicArborescence algo2 = 
    new SerializableFullyDynamicArborescence(graph, camerini2.getATreeRoots(), camerini2);

// Both edges and ATrees load lazily
algo2.removeEdge(edge1);
algo2.removeEdge(edge2);
```

## File Format

### ATree File Structure (`{baseName}_atree.dat`)

```
Header:
  - num_nodes (4 bytes): Total number of nodes in forest
  - num_roots (4 bytes): Number of root nodes
  - root_offsets (8 bytes × num_roots): File offsets for each root

Node Record (variable size):
  - edge_source (4 bytes): Source node ID (-1 if null)
  - edge_dest (4 bytes): Destination node ID (-1 if null)
  - edge_weight (4 bytes): Edge weight (-1 if null)
  - cost (4 bytes): Node cost (y value)
  - parent_offset (8 bytes): Offset of parent node (-1 if root)
  - is_simple (1 byte): Boolean flag
  - num_children (4 bytes): Number of children
  - child_offsets (8 bytes × num_children): Offsets of children
  - num_contracted (4 bytes): Number of contracted edges
  - contracted_edges (12 bytes × num_contracted): Edge data
```

## Critical Design Decision

**Why load from modified graph, not original graph?**

When saving ATrees via `setBaseName()`, the nodes reference IDs from the **modified graph** (after contractions), not the original graph. Therefore, when loading lazily, we MUST use `GraphMapper.loadNodeMap(baseName)` to get the modified graph's nodes, not `originalGraph.getNodes()`.

This was the root cause of initial test failures - mismatched node IDs between save and load.

## Test Coverage

**LazyATreeLoadingTest** provides comprehensive coverage:

1. **testLazyATreeLoadingAfterSave**: Verifies roots load correctly with lazy configuration
2. **testLazyChildrenLoading**: Confirms children load on first access
3. **testDynamicOperationsWithLazyATrees**: Tests dynamic operations trigger lazy loading
4. **testInMemoryVsLazyATreeConsistency**: Ensures lazy and in-memory produce identical results

All tests pass (4/4). All existing serializable tests still pass (11/11).

## Performance Characteristics

**Memory Usage**:
- **Without lazy loading**: O(n) where n = total nodes in ATree forest
- **With lazy loading**: O(r + a) where r = roots, a = accessed nodes
- **Best case** (shallow access): Significant reduction for deep forests
- **Worst case** (full traversal): Similar to non-lazy (but deferred)

**Time Complexity**:
- **First access to children**: +O(k) where k = number of children (file I/O)
- **Subsequent access**: O(1) (in-memory)
- **Trade-off**: Lower memory footprint for slightly higher access time on first touch

## Future Enhancements

1. **LRU Cache Eviction**: Automatically unload least recently used nodes when memory pressure increases
2. **Prefetching**: Load children of likely-to-be-accessed nodes in background
3. **Compression**: Compress ATree file to reduce disk space and I/O time
4. **Statistics**: Track cache hit/miss rates and access patterns

## Backward Compatibility

✅ **Fully backward compatible**: All existing constructors and methods work unchanged  
✅ **Opt-in**: Lazy loading only enabled via new constructor  
✅ **No breaking changes**: All 11 existing serializable tests pass without modification
