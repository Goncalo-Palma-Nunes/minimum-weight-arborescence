package optimalarborescence.inference;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

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
    List<TarjanForestNode> inEdgeNode;
    
    /** Array that for each i, where i is a cycle representative, stores a cycle of nodes of the forest */
    List<TarjanForestNode> cycleEdgeNode;

    /** array stores the leaf nodes of the forest */
    // List<TarjanForestNode> leaves;
    private TarjanForestNode[] leaves;

    /** A list of vertices to be processed. Initialized with all the vertices in 𝑉 */
    private List<Node> roots;

    /** A set of the root components of the subgraph 𝐺′ = (𝑉, 𝐻) with no incident edges of positive value */
    private Set<Node> rset;

    /** A list of the maximum weight edge for each SCC */
    private List<Node> max;

    /** A list that stores for each representative cycle vertex 𝑣 the list of cycle edge nodes in F */
    //private List<List<Edge>> cycleEdgeNodes;
    private List<List<TarjanForestNode>> cycleEdgeNodes;

    /** A union-find data structure to maintain the strongly connected components of 𝐻 */
    private UnionFindStronglyConnected ufSCC;

    private UnionFind ufWCC;

    private List<MergeableHeapInterface<HeapNode>> queues;

    // private List<Node> nodes;

    /** Constructor for CameriniForest. This class is an implementation of
     * Tarjan's optimum branching algorithm as corrected by Camerini et al.
     */
    public CameriniForest(Graph graph) {
        super(graph);
        this.roots = graph.cloneNodeList();
        this.rset = new TreeSet<>();
        this.inEdgeNode = new ArrayList<>(graph.getNumNodes());
        this.leaves = new TarjanForestNode[graph.getNumNodes()];
        this.max = new ArrayList<>();
        this.cycleEdgeNodes = new ArrayList<>();
        this.ufSCC = new UnionFindStronglyConnected(graph.getNumNodes());
        this.ufWCC = new UnionFind(graph.getNumNodes());
        this.queues = new ArrayList<>();

        for (int i = 0; i < graph.getNumNodes(); i++) {
            inEdgeNode.add(null);
            max.add(graph.getNodes().get(i));
            cycleEdgeNodes.add(new ArrayList<>());
            queues.add(new PairingHeap());
        }
        initializeDataStructures();
    }

    private void initializeDataStructures() {
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

    private MergeableHeapInterface<HeapNode> getQueue(Node v) {
        return queues.get(v.getId());
    }

    private boolean emptyQueue(MergeableHeapInterface<HeapNode> q) {
        return q.isEmpty();
    }

    private void addLeave(TarjanForestNode node, int index) {
        leaves[index] = node;
    }

    private TarjanForestNode createMinNode(Edge e) {  // Rever esta parte no paper
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

    private Node wccFind(Node v) {
        int repId = ufWCC.find(v.getId());
        // Find the node with the representative ID
        for (Node node : getNodes()) {
            if (node.getId() == repId) {
                return node;
            }
        }
        throw new RuntimeException("Node with ID " + repId + " not found in wccFind");
    }

    private void wccUnion(Node u, Node v) {
        ufWCC.union(u.getId(), v.getId());
    }

    private Node sccFind(Node v) {
        int repId = ufSCC.find(v.getId());
        // Find the node with the representative ID
        for (Node node : getNodes()) {
            if (node.getId() == repId) {
                return node;
            }
        }
        throw new RuntimeException("Node with ID " + repId + " not found in sccFind");
    }

    private void sccUnion(Node u, Node v) {
        ufSCC.union(u.getId(), v.getId());
    }

    private void updateReducedCosts(List<TarjanForestNode> cycle, int sigma, Map<Node, Edge> map) {
        for (TarjanForestNode node : cycle) { // Update reduced costs
            Edge e = map.get(sccFind(node.edge.getDestination()));
            int updatedWeight = e.getWeight() - sigma;
            ufSCC.addWeight(node.getEdge().getDestination().getId(), updatedWeight);
        }
    }

    private TarjanForestNode getMaxWeightEdgeInCycle(List<TarjanForestNode> cycle) {
        return cycle.stream().max(Comparator.comparing(n -> n.edge.getWeight())).orElseThrow();
    }

    private void updateSCCMaxWeightEdge(Node rep) {
        List<TarjanForestNode> cycleEdges = cycleEdgeNodes.get(rep.getId());
        Edge maxEdge = cycleEdges.stream().max(Comparator.comparing(n -> n.edge.getWeight())).orElseThrow().edge;
        max.set(rep.getId(), maxEdge.getDestination());
    }

    private Node getSCCMaxTarget(Node v) {
        return max.get(sccFind(v).getId());
    }

    private void printQueues() {
        System.out.println("Printing queues:");
        for (int i = 0; i < queues.size(); i++) {
            MergeableHeapInterface<HeapNode> q = queues.get(i);
            System.out.print("Queue entering node " + i + ": ");
            if (q.isEmpty()) {
                System.out.println("Empty");
            } else {
                List<HeapNode> elements = new ArrayList<>();
                while (!q.isEmpty()) {
                    // System.out.println("Extracting min...");
                    elements.add(q.extractMin());
                }
                System.out.println(elements.stream()
                    .map(hn -> hn.getEdge().toString())
                    .collect(Collectors.joining(", ")));
                // Reinsert elements back into the queue
                for (HeapNode hn : elements) {
                    q.insert(hn);
                }
            }
        }
    }

    private void printMaxEdges() {
        System.out.println("Printing max edges for each SCC:");
        for (int i = 0; i < max.size(); i++) {
            Node v = getNodes().get(i);
            Node maxNode = max.get(i);
            System.out.println("SCC represented by node " + v.getId() + ": max edge target = " + (maxNode != null ? maxNode.getId() : "null"));
        }
    }

    private void printLeaves() {
        System.out.println("Printing leaves:");
        for (int i = 0; i < leaves.length; i++) {
            TarjanForestNode leaf = leaves[i];
            System.out.println("\tpi[" + i + "]: " + (leaf != null ? "(" + leaf.edge.getSource().getId() + "," + leaf.edge.getDestination().getId()  + ")": "null"));
        }
    }

    private void printRoots() {
        System.out.print("\nroots = {");
        for (Node r : roots) {
            System.out.print(" " + r.getID() + ", ");
        }
        System.out.println("}");
    }

    private List<TarjanForestNode> getRoots() {
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
                if (leaves[root.getId()] == null) {
                    rset.add(root);
                }
                continue;
            }
            Edge e = q.extractMin().getEdge();
            while (!emptyQueue(q) && sccFind(e.getSource()) == sccFind(e.getDestination())) {
                e = q.extractMin().getEdge();
            }
            if (sccFind(e.getSource()) == sccFind(e.getDestination())) {
                continue;
            }

            Node u = e.getSource();
            Node v = e.getDestination();
            
            TarjanForestNode minNode;
            if (wccFind(u) != wccFind(v)) {
                minNode = createMinNode(e);
                inEdgeNode.set(v.getId(), minNode);
                wccUnion(u, root);
            }
            else {
                // Create minNode without setting leaf (it's part of a cycle)
                minNode = new TarjanForestNode(e);
                // inEdgeNode.set(root.getId(), null);
                inEdgeNode.set(sccFind(root).getId(), null);
                List<TarjanForestNode> cycle = new ArrayList<>(); cycle.add(minNode);
                Map<Node, Edge> map = new HashMap<>();
                map.put(sccFind(minNode.edge.getDestination()), minNode.edge);

                Node i = sccFind(u);
                while (inEdgeNode.get(i.getId()) != null) {
                    TarjanForestNode node = inEdgeNode.get(i.getId());
                    cycle.add(node);
                    map.put(i, node.edge);
                    
                    i = sccFind(node.edge.getSource());
                }

                TarjanForestNode maxWeightEdge = getMaxWeightEdgeInCycle(cycle);
                int sigma = maxWeightEdge.edge.getWeight();
                updateReducedCosts(cycle, sigma, map);

                for (TarjanForestNode n: cycle) { // Perform union of the nodes in the cycle
                    sccUnion(n.edge.getSource(), n.edge.getDestination());
                }

                Node rep = sccFind(maxWeightEdge.edge.getDestination());
                
                // Store cycle edges for the representative
                cycleEdgeNodes.set(rep.getId(), new ArrayList<>(cycle));
                
                roots.add(sccFind(rep));
                updateSCCMaxWeightEdge(rep);

                for (TarjanForestNode n : cycle) { // Merge queues involved in the cycle
                    if (sccFind(n.edge.getDestination()) != rep) {
                        getQueue(rep).merge(getQueue(sccFind(n.edge.getDestination())));
                    }
                }
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
            for (TarjanForestNode child : current.getChildren()) {
                child.setParent(null);
                if (!N.contains(child)) {
                    N.add(child);
                }
            }
            current = current.getParent();
        }
    }

    private List<Edge> expansionPhase() {
        List<Edge> B = new ArrayList<>(); // optimal arborescence (called H in other places)
        List<Node> R = rset.stream().map(v -> getSCCMaxTarget(v)).collect(Collectors.toCollection(ArrayList::new));
        List<TarjanForestNode> N = getRoots();

        // Process set R first - trace paths from leaves of R nodes
        for (Node u : R) {
            if (leaves[u.getId()] != null) {
                tracePath(leaves[u.getId()], N);
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
            if (leaves[v.getId()] != null) {
                tracePath(leaves[v.getId()], N);
            }
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
