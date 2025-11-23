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
    // List<TarjanForestNode> cycleEdgeNode;

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
    private List<List<TarjanForestNode>> cycleEdgeNodes;

    /** A union-find data structure to maintain the strongly connected components of 𝐻 */
    private UnionFindStronglyConnected ufSCC;

    private UnionFind ufWCC;

    private List<MergeableHeapInterface<HeapNode>> queues;

    private Comparator<HeapNode> maxDisjointCmp;

    private Comparator<Edge> cmp;

    // private List<Node> nodes;

    /** Constructor for CameriniForest. This class is an implementation of
     * Tarjan's optimum branching algorithm as corrected by Camerini et al.
     */
    public CameriniForest(Graph graph, Comparator<Edge> comparator) {
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

        this.cmp = comparator;

        // this.maxDisjointCmp = (o1, o2) -> {
        //     Edge e1 = getAdjustedEdge(o1.getEdge());
        //     Edge e2 = getAdjustedEdge(o2.getEdge());
        //     return cmp.compare(e1, e2);
        // };
        this.maxDisjointCmp = new Comparator<HeapNode>() {
            @Override
            public int compare(HeapNode o1, HeapNode o2) {
                Edge e1 = getAdjustedEdge(o1.getEdge());
                Edge e2 = getAdjustedEdge(o2.getEdge());
                return cmp.compare(e1, e2);
            }
        };


        for (int i = 0; i < graph.getNumNodes(); i++) {
            inEdgeNode.add(null);
            max.add(graph.getNodes().get(i));
            cycleEdgeNodes.add(new ArrayList<>());
            queues.add(new PairingHeap(maxDisjointCmp));
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

    protected Edge getAdjustedEdge(Edge edge) {
        Number w = this.getAdjustedWeight(edge);
        return new Edge(edge.getSource(), edge.getDestination(), w.intValue());
    }

    protected Number getAdjustedWeight(Edge e) {
        return e.getWeight() + ufSCC.findWeight(e.getDestination().getId());
    }

    private void updateReducedCosts(List<Integer> contractionSet, int sigma, Map<Integer, TarjanForestNode> map) {
        for (Integer node : contractionSet) { // Update reduced costs
            Number incidentW = getAdjustedWeight(map.get(node).getEdge());
            Number reducedCost = sigma - incidentW.floatValue();
            ufSCC.addWeight(node, reducedCost.intValue());
            // Edge e = map.get(sccFind(node.edge.getDestination()));
            // int updatedWeight = e.getWeight() - sigma;
            // ufSCC.addWeight(node.getEdge().getDestination().getId(), updatedWeight);
        }
    }

    private TarjanForestNode getMaxWeightEdgeInCycle(List<TarjanForestNode> cycle) {
        System.out.println("\t\u001B[32mcycle edges: \u001B[0m");
        for (TarjanForestNode n : cycle) {
            System.out.println("\t\t(" + n.getEdge().getSource().getId() + "," + n.getEdge().getDestination().getId() + ", adjusted weight=" + getAdjustedWeight(n.getEdge()) + ")");
        }
        Comparator <TarjanForestNode> cmp = new Comparator<TarjanForestNode>() {
            @Override
            public int compare(TarjanForestNode n1, TarjanForestNode n2) {
                // adjusted weight
                Edge e1 = getAdjustedEdge(n1.getEdge());
                Edge e2 = getAdjustedEdge(n2.getEdge());
                return Integer.compare(e1.getWeight(), e2.getWeight());
            }
        };
        System.out.println("\t\u001B[32mMax edge in cycle: " + cycle.stream().max(cmp).orElseThrow() + "\u001B[0m");

        return cycle.stream().max(cmp).orElseThrow();
    }

    private void updateSCCMaxWeightEdge(Node rep) {
        List<TarjanForestNode> cycleEdges = cycleEdgeNodes.get(rep.getId());
        TarjanForestNode maxNode = getMaxWeightEdgeInCycle(cycleEdges);

        Edge maxEdge = maxNode.getEdge();
        max.set(rep.getId(), maxEdge.getDestination());
    }

    private void updateMax(Node index, Node newVal) {

        System.out.println("\u001B[31mUpdating max for SCC represented by node " + index.getId() + "\u001B[0m");
        System.out.println("\tNew candidate max: " + (newVal != null ? newVal.getId() : "null"));
        System.out.println("\tCurrent max target: " + (max.get(index.getId()) != null ? max.get(index.getId()).getId() : "null"));
        if (getCycleEdges(index) != null && !getCycleEdges(index).isEmpty()) {
            System.out.println("\tSCC has a cycle, finding max edge in cycle.");
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
                    // .map(hn -> hn.getEdge().toString())
                    .map(hn -> "(" + hn.getEdge().getSource().getId() + "," + hn.getEdge().getDestination().getId() + "," + hn.getEdge().getWeight() + ")")
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
            // System.out.println("SCC represented by node " + v.getId() + ": max edge target = " + (maxNode != null ? maxNode.getId() : "null"));
            System.out.println("\tmax[" + i + "]: " + (maxNode != null ? maxNode.getId() : "null"));
        }
    }

    private void printLeaves() {
        System.out.println("Printing leaves:");
        for (int i = 0; i < leaves.length; i++) {
            TarjanForestNode leaf = leaves[i];
            System.out.println("\tpi[" + i + "]: " + (leaf != null ? "(" + leaf.edge.getSource().getId() + "," + leaf.edge.getDestination().getId()  + ")": "null"));
        }
    }

    private void printRset() {
        System.out.print("\nrset = {");
        for (Node r : rset) {
            System.out.print(" " + r.getID() + ", ");
        }
        System.out.println("}");
    }

    private void printRoots() {
        System.out.print("\nroots = {");
        for (Node r : roots) {
            System.out.print(" " + r.getID() + ", ");
        }
        System.out.println("}");
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
            printRoots();
            // printQueues();
            printLeaves();
            // printRset();
            printMaxEdges();
            Node root = roots.remove(0);
            MergeableHeapInterface<HeapNode> q = getQueue(sccFind(root)); // priority queue of edges entering r
            System.out.println("<----- contractionPhase: processing root " + root.getId() + " ----->");

            if (emptyQueue(q)) {
                // Only add to rset if this node has no incoming edge (no leaf set)
                rset.add(root);
                System.out.println("\tRoot " + root.getId() + " has no incoming edges (emptyQueue), added to rset.");
                continue;
            }
            Edge e = q.extractMin().getEdge();
            while (!emptyQueue(q) && sccFind(e.getSource()) == sccFind(e.getDestination())) {
                e = q.extractMin().getEdge();
            }

            System.out.println("\tSelected edge (" + e.getSource().getId() + "," + e.getDestination().getId() + ") with adjusted weight " + getAdjustedWeight(e));
            if (sccFind(e.getSource()) == sccFind(e.getDestination())) {
                // Both ends of the edge are in the same SCC, skip this edge
                rset.add(root);
                System.out.println("\t\tBoth ends of the edge are in the same SCC, skipping edge and adding root " + root.getId() + " to rset.");
                continue;
            }

            Node u = e.getSource();
            Node v = e.getDestination();

            TarjanForestNode minNode = createMinNode(e);
            if (wccFind(u) != wccFind(v)) {
                // no cycle formed
                System.out.println("\t\tNo cycle formed by adding edge (" + e.getSource().getId() + "," + e.getDestination().getId() + ")");
                inEdgeNode.set(root.getId(), minNode);
                wccUnion(u, v);
            }
            else {
                System.out.println("\t\tCycle formed by adding edge (" + e.getSource().getId() + "," + e.getDestination().getId() + ")");

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

                for (int i = sccFind(u).getId(); inEdgeNode.get(i) != null; i = sccFind(inEdgeNode.get(i).edge.getSource()).getId()) {
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
            System.out.println("<----- End of processing for root " + root.getId() + " ----->\n");
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

    private List<Edge> expansionPhase() {
        // List<Edge> B = new ArrayList<>(); // optimal arborescence (called H in other places)
        // List<Node> R = rset.stream().map(v -> getSCCMaxTarget(v)).collect(Collectors.toCollection(ArrayList::new));
        // Set<Node> R = rset.stream()
        //     .map(v -> getSCCMaxTarget(v)) // ir buscar o max sem SCC?
        //     .collect(Collectors.toSet());
        System.out.println("####### Expansion Phase #######");
        printMaxEdges();
        printLeaves();
        printRset();

        Set<Integer> R = rset.stream()
            .map(v -> max.get(v.getId()).getId())
            // .map(v -> getSCCMaxTarget(v))
            // .map(n -> n.getId())
            .collect(Collectors.toSet());

        // Comparator<TarjanForestNode> tarjanComp = new Comparator<TarjanForestNode>() {
        //     @Override
        //     public int compare(TarjanForestNode n1, TarjanForestNode n2) {
        //         Edge e1 = getAdjustedEdge(n1.getEdge());
        //         Edge e2 = getAdjustedEdge(n2.getEdge());
        //         return Integer.compare(e1.getWeight(), e2.getWeight());
        //     }
        // };
        // Set<Integer> R = rset.stream()
        //     .map(v -> sccFind(v)) // get the representative of the SCC
        //     .map(v -> getCycleEdges(v)) // get the cycle edges associated with the SCC
        //     .map(cycleEdges -> cycleEdges.stream().max(tarjanComp).orElseThrow()) // get the max edge in the cycle
        //     .map(maxNode -> maxNode.getEdge().getDestination().getId()) // get the target node ID of the max edge
        //     .collect(Collectors.toSet());

        List<Edge> B = new ArrayList<>(this.graph.getNumNodes() - 1);
        List<TarjanForestNode> N = getRoots();

        System.out.println("Set R (targets of max edges for rset): " + R);
        System.out.println("Initial roots in N: ");
        for (TarjanForestNode n : N) {
            System.out.println("\tNode with edge (" + n.edge.getSource().getId() + "," + n.edge.getDestination().getId() + ", remove=" + n.isRemove() + ")");
        }

        // Process set R first - trace paths from leaves of R nodes
        for (Integer node : R) {
            System.out.println("\tProcessing R node with ID " + node);
            System.out.println("\t\tLeaf node for R node: " + (leaves[node] != null ? "(" + leaves[node].edge.getSource().getId() + "," + leaves[node].edge.getDestination().getId() + ")" : "null"));
            System.out.println("\t\tTracing path from leaf to root");
            if (leaves[node] != null) {
                tracePath(leaves[node], N);
            }
            System.out.println("\t\tNew set N (roots): ");
            for (TarjanForestNode n : N) {
                System.out.println("\t\t\tNode with edge (" + n.edge.getSource().getId() + "," + n.edge.getDestination().getId() + ", remove=" + n.isRemove() + ")");
            }
        }
        // for (Node u : R) {
        //     if (leaves[u.getId()] != null) {
        //         tracePath(leaves[u.getId()], N);
        //     }
        // }

        // Process set N
        while (!N.isEmpty()) {
            TarjanForestNode edgeNode = N.remove(0);
            System.out.println("Processing node with edge (" + edgeNode.getEdge().getSource().getId() + "," + edgeNode.getEdge().getDestination().getId() + "), remove=" + edgeNode.isRemove());
            
            // Skip nodes marked for removal
            if (edgeNode.isRemove()) {
                System.out.println("\tSkipping node marked for removal during tracePath operation.");
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

        System.out.println("Final set of edges (B): ");
        for (Edge edge : B) {
            System.out.println("\tEdge (" + edge.getSource().getId() + "," + edge.getDestination().getId() + "," + edge.getWeight() + ")");
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
