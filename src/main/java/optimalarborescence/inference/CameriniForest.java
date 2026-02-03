package optimalarborescence.inference;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

import optimalarborescence.datastructure.UnionFind;
import optimalarborescence.datastructure.UnionFindStronglyConnected;
import optimalarborescence.datastructure.heap.HeapNode;
import optimalarborescence.datastructure.heap.MergeableHeapInterface;
import optimalarborescence.datastructure.heap.PairingHeap;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Node;
import optimalarborescence.graph.Edge;

public class CameriniForest extends StaticAlgorithm {

    /**  Array that for each i stores a node from the forest which is associated with the minimum weight edge incident in node i */
    protected List<TarjanForestNode> inEdgeNode;

    /** array stores the leaf nodes of the forest */
    protected TarjanForestNode[] leaves;

    /** A list of vertices to be processed. Initialized with all the vertices in 𝑉 */
    protected List<Node> roots;

    /** A set of the root components of the subgraph 𝐺′ = (𝑉, 𝐻) with no incident edges of positive value */
    protected Set<Node> rset;

    /** A list of the maximum weight edge for each SCC */
    private List<Node> max;

    /** A list that stores for each representative cycle vertex 𝑣 the list of cycle edge nodes in F */
    protected List<List<TarjanForestNode>> cycleEdgeNodes;

    /** A union-find data structure to maintain the strongly connected components of 𝐻 */
    private UnionFindStronglyConnected ufSCC;

    private UnionFind ufWCC;

    protected List<MergeableHeapInterface<HeapNode>> queues;

    protected Comparator<HeapNode> maxDisjointCmp;

    private Comparator<Edge> cmp;

    /** Constructor for CameriniForest. This class is an implementation of
     * Tarjan's optimum branching algorithm as corrected by Camerini et al.
     */
    public CameriniForest(Graph graph, Comparator<Edge> comparator) {
        super(graph);
        
        // Find the maximum node ID to determine array size
        int maxNodeId = graph.getNodes().stream()
            .mapToInt(Node::getId)
            .max()
            .orElse(0);
        
        this.roots = graph.cloneNodeList();
        this.rset = new TreeSet<>();
        this.inEdgeNode = new ArrayList<>();
        this.leaves = new TarjanForestNode[maxNodeId + 1];
        this.max = new ArrayList<>();
        this.cycleEdgeNodes = new ArrayList<>();
        this.ufSCC = new UnionFindStronglyConnected(maxNodeId + 1);
        this.ufWCC = new UnionFind(maxNodeId + 1);
        this.queues = new ArrayList<>();

        this.cmp = comparator;

        this.maxDisjointCmp = new Comparator<HeapNode>() {
            @Override
            public int compare(HeapNode o1, HeapNode o2) {
                Edge e1 = getAdjustedEdge(o1.getEdge());
                Edge e2 = getAdjustedEdge(o2.getEdge());
                return cmp.compare(e1, e2);
            }
        };

        // Find the maximum node ID to determine array size
         maxNodeId = graph.getNodes().stream()
            .mapToInt(Node::getId)
            .max()
            .orElse(0);
        
        // Initialize arrays/lists to accommodate the maximum node ID
        for (int i = 0; i <= maxNodeId; i++) {
            inEdgeNode.add(null);
            max.add(null);
            cycleEdgeNodes.add(new ArrayList<>());
            queues.add(new PairingHeap(maxDisjointCmp));
        }
        
        // Populate entries only for nodes that exist
        for (Node node : graph.getNodes()) {
            max.set(node.getId(), node);
        }
        
        initializeDataStructures();
    }

    protected void initializeDataStructures() {
        for (Edge e : graph.getEdges()) {
            Node v = e.getDestination();
            getQueue(v).insert(new HeapNode(e, null, null));;
        }
    }

    public List<Node> getNodes() {
        return this.graph.getNodes();
    }

    public TarjanForestNode[] getLeaves() {
        return this.leaves;
    }

    protected MergeableHeapInterface<HeapNode> getQueue(Node v) {
        return queues.get(v.getId());
    }

    protected boolean emptyQueue(MergeableHeapInterface<HeapNode> q) {
        return q.isEmpty();
    }

    private void addLeave(TarjanForestNode node, int index) {
        // Only set the leaf if it hasn't been set yet
        if (leaves[index] == null) {
            leaves[index] = node;
        }
    }

    protected TarjanForestNode createMinNode(Edge e) {  // Rever esta parte no paper
        Node r = e.getDestination();
        List<TarjanForestNode> cycleEdges = getCycleEdges(r);

        TarjanForestNode minNode = new TarjanForestNode(e);
        if (cycleEdges.isEmpty()) {
            addLeave(minNode, r.getId());
        }
        else {
            for (TarjanForestNode ce : cycleEdges) {
                ce.setParent(minNode);
            }
        }
        return minNode;
    }

    private List<TarjanForestNode> getCycleEdges(Node v) {
        return cycleEdgeNodes.get(sccFind(v).getId());
    }

    protected Node wccFind(Node v) {
        int repId = ufWCC.find(v.getId());
        // Find the node with the representative ID
        for (Node node : getNodes()) {
            if (node.getId() == repId) {
                return node;
            }
        }
        throw new RuntimeException("Node with ID " + repId + " not found in wccFind");
    }

    protected void wccUnion(Node u, Node v) {
        ufWCC.union(u.getId(), v.getId());
    }

    protected Node sccFind(Node v) {
        int repId = ufSCC.find(v.getId());
        // Find the node with the representative ID
        for (Node node : getNodes()) {
            if (node.getId() == repId) {
                return node;
            }
        }
        throw new RuntimeException("Node with ID " + repId + " not found in sccFind");
    }

    protected void sccUnion(Node u, Node v) {
        ufSCC.union(u.getId(), v.getId());
    }

    protected Edge getAdjustedEdge(Edge edge) {
        Number w = this.getAdjustedWeight(edge);
        return new Edge(edge.getSource(), edge.getDestination(), w.intValue());
    }

    protected Number getAdjustedWeight(Edge e) {
        return e.getWeight() + ufSCC.findWeight(e.getDestination().getId());
    }

    protected void updateReducedCosts(List<Integer> contractionSet, int sigma, Map<Integer, TarjanForestNode> map) {
        for (Integer node : contractionSet) { // Update reduced costs
            Number incidentW = getAdjustedWeight(map.get(node).getEdge());
            Number reducedCost = sigma - incidentW.floatValue();
            ufSCC.addWeight(node, reducedCost.intValue());
        }
    }

    protected TarjanForestNode getMaxWeightEdgeInCycle(List<TarjanForestNode> cycle) {
        Comparator <TarjanForestNode> cmp = new Comparator<TarjanForestNode>() {
            @Override
            public int compare(TarjanForestNode n1, TarjanForestNode n2) {
                // adjusted weight
                Edge e1 = getAdjustedEdge(n1.getEdge());
                Edge e2 = getAdjustedEdge(n2.getEdge());
                return Integer.compare(e1.getWeight(), e2.getWeight());
            }
        };
        return cycle.stream().max(cmp).orElseThrow();
    }

    protected void updateMax(Node index, Node newVal) {
        if (getCycleEdges(index) != null && !getCycleEdges(index).isEmpty()) {
            Comparator<TarjanForestNode> cmp = new Comparator<TarjanForestNode>() {
                @Override
                public int compare(TarjanForestNode n1, TarjanForestNode n2) {
                    // adjusted weight
                    Edge e1 = getAdjustedEdge(n1.getEdge());
                    Edge e2 = getAdjustedEdge(n2.getEdge());
                    return Integer.compare(e1.getWeight(), e2.getWeight());
                }
            };
            List<TarjanForestNode> cycleEdges = getCycleEdges(index);
            TarjanForestNode maxNode = cycleEdges.stream().max(cmp).orElseThrow();
            Edge maxEdge = maxNode.getEdge();
            newVal = maxEdge.getDestination();
        }
        max.set(index.getId(), newVal);
    }

    public List<TarjanForestNode> getRoots() {
        List<TarjanForestNode> rootsList = new ArrayList<>();

        for (TarjanForestNode leaf : leaves) {
        
            while (leaf != null && leaf.getParent() != null) {
                leaf = leaf.getParent();
            }
            if (leaf != null && !rootsList.contains(leaf)) {
                rootsList.add(leaf);
            }
        }
        return rootsList;
    }

    private void contractionPhase() {
        while (!roots.isEmpty()) {
            Node root = roots.remove(0);
            MergeableHeapInterface<HeapNode> q = getQueue(sccFind(root)); // priority queue of edges entering r

            if (emptyQueue(q)) {
                // Only add to rset if this node has no incoming edge (no leaf set)
                rset.add(root);
                continue;
            }
            Edge e = q.extractMin().getEdge();
            while (!emptyQueue(q) && sccFind(e.getSource()) == sccFind(e.getDestination())) {
                e = q.extractMin().getEdge();
            }

            if (sccFind(e.getSource()) == sccFind(e.getDestination())) {
                // Both ends of the edge are in the same SCC, skip this edge
                rset.add(root);
                continue;
            }

            Node u = e.getSource();
            Node v = e.getDestination();

            TarjanForestNode minNode = createMinNode(e);
            if (wccFind(u) != wccFind(v)) {
                // no cycle formed
                inEdgeNode.set(root.getId(), minNode);
                wccUnion(u, v);
            }
            else {

                // store nodes in cycle
                List<Integer> contractionSet = new ArrayList<>();
                contractionSet.add(sccFind(v).getId());

                // keep track of the edges in the cycle
                List<TarjanForestNode> edgeNodesInCycle = new ArrayList<>();
                edgeNodesInCycle.add(minNode);

                // map the edge incident in a node
                Map<Integer, TarjanForestNode> map = new HashMap<>();
                map.put(sccFind(v).getId(), minNode);

                // since a cycle as arisen we need to choose a new minimum weight edge incident in node root
                inEdgeNode.set(root.getId(), null);

                Set<Integer> visited = new HashSet<>();
                for (int i = sccFind(u).getId(); inEdgeNode.get(i) != null; i = sccFind(inEdgeNode.get(i).edge.getSource()).getId()) {
                    if (visited.contains(i)) {
                        break;  // Cycle detected - prevent infinite loop
                    }
                    visited.add(i);
                    map.put(i, inEdgeNode.get(i));
                    edgeNodesInCycle.add(inEdgeNode.get(i));
                    contractionSet.add(i);
                }

                TarjanForestNode maxWeightTarjanNode = getMaxWeightEdgeInCycle(edgeNodesInCycle);
                Edge maxWeightEdge = maxWeightTarjanNode.edge;
                // Node dst = max.get(sccFind(maxWeightEdge.getDestination()).getId());
                Node dst = sccFind(maxWeightEdge.getDestination());

                // int sigma = maxWeightEdge.getWeight();
                int sigma = getAdjustedWeight(maxWeightEdge).intValue();
                updateReducedCosts(contractionSet, sigma, map);

                for (TarjanForestNode n: edgeNodesInCycle) { // Perform union of the nodes in the cycle
                    sccUnion(n.edge.getSource(), n.edge.getDestination());
                }

                Node rep = sccFind(maxWeightEdge.getDestination());
                roots.add(0, rep); // Add representative to roots to be processed again
                for (Integer node : contractionSet) { // Merge queues involved in the cycle
                    if (rep.getId() != node) {
                        getQueue(rep).merge(getQueue(getNodes().get(node)));
                    }
                }
                updateMax(rep, dst);
                // updateMax(dst, rep);
                cycleEdgeNodes.set(rep.getId(), edgeNodesInCycle);
            }
        }
    }

    /**
     * Trace-Path function: traces a path from edgeNode to root, marking all nodes
     * for removal and making their children new roots in N.
     */
    private void tracePath(TarjanForestNode edgeNode, List<TarjanForestNode> N) {
        TarjanForestNode current = edgeNode;
        while (current != null) {
            current.setRemove(true);
            // Make all children roots by setting parent to null
            List<TarjanForestNode> children = new ArrayList<>(current.getChildren());
            for (TarjanForestNode child : children) {
                child.setParent(null);
                N.add(child);
                // if (!N.contains(child)) {
                //     N.add(child);
                // }
            }
            current = current.getParent();
        }
    }

    protected List<Edge> expansionPhase() {
        Set<Integer> R = rset.stream()
            .map(v -> max.get(v.getId()).getId())
            .collect(Collectors.toSet());

        List<Edge> B = new ArrayList<>(this.graph.getNumNodes() - 1);
        List<TarjanForestNode> N = getRoots();

        // Process set R first - trace paths from leaves of R nodes
        for (Integer node : R) {
            if (leaves[node] != null) {
                tracePath(leaves[node], N);
            }
        }

        // Process set N
        while (!N.isEmpty()) {
            TarjanForestNode edgeNode = N.remove(0);
            
            // Skip nodes marked for removal
            if (edgeNode.isRemove()) {
                continue;
            }
            
            // Add edge to result
            B.add(edgeNode.getEdge());
            
            // Trace path from destination's leaf
            Node v = edgeNode.getEdge().getDestination();
            tracePath(leaves[v.getId()], N);
            // if (leaves[v.getId()] != null) {
            //     tracePath(leaves[v.getId()], N);
            // }
        }

        return B;
    }

    @Override
    public Graph inferPhylogeny(Graph graph) {

        contractionPhase();
        List<Edge> forest = expansionPhase();
        
        return new Graph(forest);
    }
}
