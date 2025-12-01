# Next Steps for Serializable Lazy-Loading Implementation

## Context

This document outlines the remaining work for the serializable version of the dynamic arborescence algorithms. The goal is to enable lazy-loading of graph edges from memory-mapped files to reduce memory footprint for large graphs.

## Architecture Overview

### What Gets Persisted
1. **Original Graph** (via GraphMapper): For reference/debugging - node indices + full edge list
2. **Modified Graph** (via GraphMapper): The graph with reduced costs - THIS is what the algorithm uses
3. **ATree Forest** (via ATreeMapper): Partial contraction state
4. **Current Arborescence** (TODO): The inference solution (selected edges)

### What Stays In-Memory
1. **Queue State**: Initialized lazily during algorithm execution
2. **Algorithm State**: Current data structures, union-find, etc.

### Key Design Decision
The **modified graph with reduced costs MUST be persisted**. This is what the algorithm actually runs on.
The modified graph typically has the same edges as the original, but with adjusted weights from ATree contractions.
By loading edges from the modified graph, we ensure the algorithm sees the correct reduced costs.

## Current Implementation Status

### ✅ Completed
- **SerializableDynamicTarjanArborescence**: Extends `DynamicTarjanArborescence` with lazy queue initialization
  - Overrides `getQueue(Node v)` to load edges on-demand from memory-mapped files
  - Overrides `initializeDataStructures()` to prevent eager loading
  - Supports two modes: in-memory (backward compatible) and file-based operation
  - Location: `src/main/java/optimalarborescence/inference/dynamic/SerializableDynamicTarjanArborescence.java`

- **SerializableFullyDynamicArborescence**: Simplified wrapper class
  - Ensures `SerializableDynamicTarjanArborescence` is used
  - Location: `src/main/java/optimalarborescence/inference/dynamic/SerializableFullyDynamicArborescence.java`

- **CameriniForest Modifications**: Made fields and methods accessible to subclasses
  - Changed `queues` field from `private` to `protected`
  - Changed `maxDisjointCmp` field from `private` to `protected`
  - Changed `getQueue(Node v)` method from `private` to `protected`
  - Changed `initializeDataStructures()` method from `private` to `protected`
  - Location: `src/main/java/optimalarborescence/inference/CameriniForest.java`

### How the Lazy Loading Works
```
1. Pre-requisite: Modified graph (with reduced costs) is saved to files using GraphMapper.saveGraph()

2. SerializableDynamicTarjanArborescence constructor:
   - Receives baseName parameter (points to saved MODIFIED graph)
   - Loads node map from modified graph files via GraphMapper.loadNodeMap()
   - Clears all queues and marks them as uninitialized
   
3. During algorithm execution:
   - When getQueue(node) is called:
     - If queue not initialized: Load incoming edges from MODIFIED graph files
     - If queue initialized: Return cached queue
   
4. Result: Only edges for accessed nodes are loaded from disk, with correct reduced costs
```

## Priority 1: Testing (CRITICAL)

### Task 1.1: Create Basic Lazy Loading Test
**Objective**: Verify that lazy loading works correctly and reduces memory usage.

**Steps**:
1. Create a test class: `src/test/java/optimalarborescence/inference/dynamic/SerializableDynamicTarjanArborescenceTest.java`
2. Create a small test graph (10-20 nodes, 30-50 edges)
3. Create test data:
   - Empty ATree roots list: `new ArrayList<>()`
   - Empty contracted edges list: `new ArrayList<>()`
   - Empty reduced costs map: `new HashMap<>()`
4. Create the modified graph:
   ```java
   // In real usage, this would be created by DynamicTarjanArborescence
   Graph modifiedGraph = /* create graph with reduced costs */;
   ```
5. **Save the MODIFIED graph** using `GraphMapper.saveGraph(modifiedGraph, mlstLength, "test_modified")`
6. Create `SerializableDynamicTarjanArborescence` with file mode:
   ```java
   SerializableDynamicTarjanArborescence algo = new SerializableDynamicTarjanArborescence(
       roots, contractedEdges, reducedCosts, graph, "test_modified"
   );
   ```
7. Run `inferPhylogeny(graph)` and verify:
   - Algorithm completes without errors
   - Result is a valid arborescence
   - Only accessed nodes have initialized queues (check `queueInitialized` map)

**Expected Output**: Test passes, confirming lazy loading works.

**Files to Create**:
- `src/test/java/optimalarborescence/inference/dynamic/SerializableDynamicTarjanArborescenceTest.java`

### Task 1.2: Add Logging for Queue Initialization
**Objective**: Track which queues are initialized and when.

**Steps**:
1. In `SerializableDynamicTarjanArborescence.initializeQueueForNode()` (line ~132):
   - Add logging: `System.out.println("Lazy loading edges for node " + v.getId() + ": " + incomingEdges.size() + " edges");`
2. Run test from Task 1.1 and verify logs show on-demand loading

**Files to Modify**:
- `src/main/java/optimalarborescence/inference/dynamic/SerializableDynamicTarjanArborescence.java` (line ~132)

## Priority 2: Fix Critical Issues

### Task 2.1: Implement Modified Graph Saving
**Critical Issue**: The modified graph needs to be saved to files before the algorithm can use lazy loading.

**Current Problem**: 
- Constructor expects modified graph files to already exist
- But there's no code to save the modified graph created by `createModifiedGraph()`

**Solution**: Add a method to save the modified graph during setup:

```java
// In SerializableDynamicTarjanArborescence constructor or a setup method
public void saveModifiedGraphToFiles(int mlstLength) throws IOException {
    Graph modifiedGraph = getModifiedGraph();
    GraphMapper.saveGraph(modifiedGraph, mlstLength, baseName);
}
```

**Where to call this**:
- After creating the `SerializableDynamicTarjanArborescence` instance
- Before calling `inferPhylogeny()`
- Or automatically in constructor if `mlstLength` is provided

**Files to Modify**:
- `src/main/java/optimalarborescence/inference/dynamic/SerializableDynamicTarjanArborescence.java`

## Priority 3: Persistence of Current Arborescence

### Task 3.1: Design Arborescence State File Format
**Objective**: Define how to save/load the current arborescence (solution) to files.

**File Format Proposal**: `{baseName}_arborescence.dat`
```
[num_edges: 4 bytes (int)]
For each edge:
  [source_id: 4 bytes (int)]
  [destination_id: 4 bytes (int)]
  [weight: 4 bytes (int)]
```

**Files to Create**:
- `src/main/java/optimalarborescence/memorymapper/ArborescenceMapper.java`

### Task 3.2: Implement Arborescence Saving
**Objective**: Save the current arborescence to a file.

**Method to Add** in `ArborescenceMapper`:
```java
public static void saveArborescence(List<Edge> arborescenceEdges, String baseName) throws IOException {
    // Write edges to {baseName}_arborescence.dat
}
```

**Where to Call**: In `FullyDynamicArborescence` after computing the arborescence.

**Files to Create/Modify**:
- `src/main/java/optimalarborescence/memorymapper/ArborescenceMapper.java`
- `src/main/java/optimalarborescence/inference/dynamic/FullyDynamicArborescence.java`

### Task 3.3: Implement Arborescence Loading
**Objective**: Load a previously saved arborescence from a file.

**Method to Add** in `ArborescenceMapper`:
```java
public static List<Edge> loadArborescence(String baseName, Map<Integer, Node> nodeMap) throws IOException {
    // Read edges from {baseName}_arborescence.dat
    // Reconstruct Edge objects using nodeMap
}
```

**Files to Create/Modify**:
- `src/main/java/optimalarborescence/memorymapper/ArborescenceMapper.java`

## Priority 4: Error Handling and Edge Cases

### Task 4.1: Improve Error Messages
**Objective**: Make debugging easier when lazy loading fails.

**Steps**:
1. In `getQueue(Node v)` catch block (line ~125):
   - Add more context: node ID, baseName, whether file exists
   ```java
   catch (IOException e) {
       throw new RuntimeException(
           "Failed to initialize queue for node " + nodeId + 
           " from file " + baseName + ": " + e.getMessage(), e);
   }
   ```

2. In `initializeQueueForNode()` (line ~140):
   - Check if `incomingEdges` is empty and log a warning
   ```java
   if (incomingEdges.isEmpty()) {
       System.out.println("Warning: Node " + v.getId() + " has no incoming edges");
   }
   ```

**Files to Modify**:
- `src/main/java/optimalarborescence/inference/dynamic/SerializableDynamicTarjanArborescence.java` (lines ~125, ~140)

### Task 4.2: Handle Corrupted Files
**Objective**: Gracefully handle file I/O errors.

**Steps**:
1. Add validation after loading nodeMap (line ~68):
   ```java
   if (nodeMap == null || nodeMap.isEmpty()) {
       throw new IOException("Failed to load node map from " + baseName);
   }
   ```

2. Add fallback to in-memory operation if files are corrupted

**Files to Modify**:
- `src/main/java/optimalarborescence/inference/dynamic/SerializableDynamicTarjanArborescence.java` (line ~68)

## Priority 5: Optimization and Convenience

### Task 5.1: Add Convenience Factory Methods
**Objective**: Simplify creation of serializable instances.

**Methods to Add**:
```java
/**
 * Create from an existing graph saved to memory-mapped files.
 * 
 * @param graphBaseName Base name of saved original graph files
 * @param roots ATree forest roots
 * @param contractedEdges Edges from decomposed contractions
 * @param reducedCosts Map of reduced costs
 * @return Configured SerializableDynamicTarjanArborescence instance
 */
public static SerializableDynamicTarjanArborescence fromGraphFile(
    String graphBaseName,
    List<ATreeNode> roots,
    List<Edge> contractedEdges,
    Map<Edge, Integer> reducedCosts) throws IOException {
    
    Graph graph = GraphMapper.loadGraph(graphBaseName);
    return new SerializableDynamicTarjanArborescence(
        roots, contractedEdges, reducedCosts, graph, graphBaseName);
}
```

**Files to Modify**:
- `src/main/java/optimalarborescence/inference/dynamic/SerializableDynamicTarjanArborescence.java`

### Task 5.2: Profile Memory Usage
**Objective**: Verify that lazy loading actually reduces memory footprint.

**Steps**:
1. Create a large test graph (1000+ nodes, 10000+ edges)
2. Measure memory usage with standard `DynamicTarjanArborescence` (all edges in memory)
3. Measure memory usage with `SerializableDynamicTarjanArborescence` (lazy loading)
4. Compare results and document memory savings

**Expected Result**: Significant memory reduction for large graphs.

## Priority 6: Documentation

### Task 6.1: Add JavaDoc Examples
**Objective**: Document how to use the serializable classes.

**Example to Add** to `SerializableDynamicTarjanArborescence` class JavaDoc:
```java
/**
 * Example usage:
 * <pre>
 * // Create and save a graph
 * Graph graph = new Graph(...);
 * GraphMapper.saveGraph(graph, mlstLength, "my_graph");
 * 
 * // Create serializable algorithm with lazy loading
 * SerializableDynamicTarjanArborescence algo = 
 *     new SerializableDynamicTarjanArborescence(
 *         roots, edges, costs, graph, "my_graph_modified", mlstLength);
 * 
 * // Run inference - edges loaded lazily from disk
 * Graph result = algo.inferPhylogeny(graph);
 * </pre>
 */
```

**Files to Modify**:
- `src/main/java/optimalarborescence/inference/dynamic/SerializableDynamicTarjanArborescence.java`
- `src/main/java/optimalarborescence/inference/dynamic/SerializableFullyDynamicArborescence.java`

### Task 6.2: Update README
**Objective**: Document the lazy-loading feature in the project README.

**Section to Add**:
- Overview of memory-mapped file support
- When to use serializable versions
- Performance characteristics
- Example usage

**Files to Modify**:
- `README.md`

## Summary of Files Requiring Attention

### High Priority
1. `src/main/java/optimalarborescence/inference/dynamic/SerializableDynamicTarjanArborescence.java`
   - Line ~68: Add validation for nodeMap loading
   - Line ~125: Improve error messages in getQueue()
   - Line ~140: Add logging and validation in initializeQueueForNode()
   - Verify: Edge loading from original graph works with modified graph

2. `src/main/java/optimalarborescence/inference/dynamic/DynamicTarjanArborescence.java`
   - Review `createModifiedGraph()` to understand node mapping

3. `src/main/java/optimalarborescence/memorymapper/GraphMapper.java`
   - Verify that saved graph files contain correct node map

### Medium Priority
4. Create: `src/test/java/optimalarborescence/inference/dynamic/SerializableDynamicTarjanArborescenceTest.java`
   - Basic lazy loading test

5. Create: `src/main/java/optimalarborescence/memorymapper/ArborescenceMapper.java`
   - Save/load current arborescence solution

6. `README.md`
   - Add documentation for lazy-loading feature

### Low Priority
7. `src/main/java/optimalarborescence/inference/dynamic/SerializableDynamicTarjanArborescence.java`
   - Add convenience factory methods
   
8. `src/main/java/optimalarborescence/inference/dynamic/FullyDynamicArborescence.java`
   - Integrate arborescence persistence after computing solution

## Testing Checklist

- [ ] Basic lazy loading test passes (Task 1.1)
- [ ] Queue initialization logging works (Task 1.2)
- [ ] Edge loading from original graph verified (Task 2.1)
- [ ] nodeMap loads correctly (Task 4.2)
- [ ] Error messages are informative (Task 4.1)
- [ ] Handles corrupted files gracefully (Task 4.2)
- [ ] Arborescence can be saved to files (Task 3.2)
- [ ] Arborescence can be loaded from files (Task 3.3)
- [ ] Memory usage is reduced for large graphs (Task 5.2)
- [ ] All existing tests still pass
- [ ] Documentation is updated

## Notes for LLM Agents

- **CRITICAL ARCHITECTURE FIX**: The MODIFIED graph (with reduced costs) MUST be persisted and loaded, NOT the original graph
  - This ensures we only load edges that the algorithm needs to consider
  - The modified graph has the same structure but with adjusted edge weights from ATree contractions
  - Loading from the original graph would defeat the purpose of the contraction optimization

- **What Gets Persisted**:
  1. Original graph (nodes + edges) via GraphMapper - for reference
  2. **Modified graph (nodes + edges with reduced costs)** via GraphMapper - for algorithm execution (THIS IS CRITICAL)
  3. ATree forest structure via ATreeMapper  
  4. Current arborescence (solution) - to be implemented via ArborescenceMapper

- **Key Insight**: `DynamicTarjanArborescence` already extends `CameriniForest`, so we can override queue methods directly

- **Modified Base Class**: `CameriniForest` fields `queues` and `maxDisjointCmp` are now `protected` (previously `private`)

- **Backward Compatibility**: All serializable classes support in-memory operation via constructors without baseName parameter

- **Critical Implementation Detail**: Need to add method to save modified graph to files before running algorithm with lazy loading
