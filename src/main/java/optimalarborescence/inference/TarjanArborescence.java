package optimalarborescence.inference;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.stream.Collectors;

import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.datastructure.UnionFind;
import optimalarborescence.datastructure.UnionFindStronglyConnected;
import optimalarborescence.datastructure.heap.*;
import optimalarborescence.inference.TarjanForestNode;

public class TarjanArborescence extends StaticAlgorithm {

    /** A list of vertices to be processed. Initialized with all the vertices in 𝑉 */
    private List<Node> roots;

    /** A set of the root components of the subgraph 𝐺′ = (𝑉, 𝐻) with no incident edges of positive value */
    private Set<Node> rset;

    /** A list that for each vertex 𝑣 stores a vertex of 𝐹 associated with the minimum weight
    edge incident in 𝑣 */
    private List<TarjanForestNode> inEdgeNode;

    /** A list of F’s leaves */
    // private List<TarjanForestNode> leaves;
    private TarjanForestNode[] leaves;

    /** A list of the maximum weight edge for each SCC */
    // private List<Node> max; // Q_max in Joaquim's thesis
    private List<Edge> max;

    /** A list that stores for each representative cycle vertex 𝑣 the list of cycle edge nodes in F */
    //private List<List<Edge>> cycleEdgeNodes;
    private List<List<TarjanForestNode>> cycleEdgeNodes;

    /** A union-find data structure to maintain the strongly connected components of 𝐻 */
    private UnionFindStronglyConnected ufSCC;

    private UnionFind ufWCC;

    private List<MergeableHeapInterface<HeapNode>> queues;

    /** A stack of partial solutions/branchings */
    private List<List<Edge>> branchings;

    /** The current branching */
    private List<Edge> b;

    /** Stores the number of formed cycles */
    private int levels;

    /** Stack of cycles */
    private List<List<Edge>> cycles;

    /** Stack of contracted nodes */
    List<Node> contractedNodes;

    /** A stack of strongly connected disjoint-sets S, representing the contractions */
    private List<UnionFindStronglyConnected> sccStacks;

    // private List<Node> nodes;

    public TarjanArborescence() {
        this.roots = new ArrayList<>();
        this.rset = new TreeSet<>();
        this.inEdgeNode = new ArrayList<>();
        this.leaves = null;
        this.max = new ArrayList<>();
        this.cycleEdgeNodes = new ArrayList<>();
        this.ufSCC = null;
        this.ufWCC = null;
        this.queues = new ArrayList<>();
        this.sccStacks = new ArrayList<>();
        this.b = new ArrayList<>();
        this.branchings = new ArrayList<>();
        this.levels = 0;
        this.cycles = new ArrayList<>();
        this.contractedNodes = new ArrayList<>();
    }


    /** Constructor for TarjanArborescence. This class is an implementation of
     * Tarjan's optimum branching algorithm as corrected by Camerini et al.
     */
    public TarjanArborescence(Graph graph) {
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
        this.sccStacks = new ArrayList<>();
        this.b = new ArrayList<>();

        for (int i = 0; i < graph.getNumNodes(); i++) { // TODO - passar para initializeDataStructures()
            inEdgeNode.add(null);
            // max.add(graph.getNodes().get(i));
            cycleEdgeNodes.add(new ArrayList<>());
            queues.add(new PairingHeap());
        }
        initializeDataStructures();
    }

    private void initializeDataStructures() {
        branchings = new ArrayList<>();
        levels = 0;
        cycles = new ArrayList<>();
        contractedNodes = new ArrayList<>();

        // Check for duplicate edges
        // Map<String, List<Edge>> edgesByKey = new HashMap<>();
        // for (Edge e : graph.getEdges()) {
            // String key = e.getSource().getId() + "->" + e.getDestination().getId();
            // edgesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        // }
        // for (Map.Entry<String, List<Edge>> entry : edgesByKey.entrySet()) {
        //     if (entry.getValue().size() > 1) {
        //         System.out.println("[Tarjan.init] WARNING: Duplicate edges for " + entry.getKey() + ":");
        //         for (Edge e : entry.getValue()) {
        //             System.out.println("  " + e);
        //         }
        //     }
        // }
        
        for (Edge e : graph.getEdges()) {
            Node v = e.getDestination();
            getQueue(v).insert(new HeapNode(e, null, null));;
        }


        // for (int i = 0; i < graph.getNumNodes(); i++) {
        //     // inEdgeNode.add(null);
        //     // max.add(null);
        //     // cycleEdgeNodes.add(new ArrayList<>());

        //     // Node v = nodes.get(i);
        //     // queues.add(new PairingHeap<>());
        // }
    }

    public List<Node> getNodes() {
        return this.graph.getNodes();
    }

    protected MergeableHeapInterface<HeapNode> getQueue(Node v) {
        return queues.get(v.getId());
    }

    private boolean emptyQueue(MergeableHeapInterface<HeapNode> q) {
        return q.isEmpty();
    }

    private void addLeave(TarjanForestNode node, int index) {
        leaves[index] = node;
    }

    public TarjanForestNode[] getLeaves() {
        return leaves;
    }

    private TarjanForestNode createMinNode(Edge e) {  // Rever esta parte no paper
        Node r = e.getDestination();
        List<TarjanForestNode> cycleEdges = getCycleEdges(r);

        TarjanForestNode minNode = new TarjanForestNode(e);
        if (cycleEdges.isEmpty()) {
            //leaves.set(r.getId(), minNode);
            // leaves.add(minNode);
            addLeave(minNode, r.getId());
        }
        else {
            for (TarjanForestNode ce : cycleEdges) {
                System.out.println("\tSetting parent of " + ce + " to " + minNode);
                ce.setParent(minNode);
                // minNode.addChild(ce);
            }
        }
        return minNode;
    }

    private List<TarjanForestNode> getCycleEdges(Node v) {
        return cycleEdgeNodes.get(ufSCC.find(v.getId()));
    }

    private Node wccFind(Node v) {
        // System.out.println("wccFind on node with id " + v.getId());
        // System.out.println("nodes size = " + nodes.size());
        // System.out.println("ufWCC size = " + ufWCC.getSize());
        return getNodes().get(ufWCC.find(v.getId()));
    }

    private void wccUnion(Node u, Node v) {
        ufWCC.union(u.getId(), v.getId());
    }

    private Node getNodeById(int id) {
        for (Node node : getNodes()) {
            if (node.getId() == id) {
                return node;
            }
        }
        throw new IllegalStateException("Node with ID " + id + " not found");
    }

    private Node sccFind(Node v) {
        int repId = ufSCC.find(v.getId());
        return getNodeById(repId);
    }

    private Node sccFind(Node v, int level) {
        int repId = sccStacks.get(level).find(v.getId());
        return getNodeById(repId);
    }

    private void sccUnion(Node u, Node v) {
        ufSCC.union(u.getId(), v.getId());
    }

    private void updateReducedCosts(List<TarjanForestNode> cycle, int sigma, Map<TarjanForestNode, Edge> map) {
        for (TarjanForestNode node : cycle) { // Update reduced costs
            Edge edge = map.get(node);
            Node v = edge.getDestination();
            // The cost reduction for the destination node is the difference between
            // the max edge weight (sigma) and this edge's weight
            int cost = sigma - edge.getWeight();
            ufSCC.addWeight(v.getId(), cost);
        }
        // Add the cycle edges to the SCC representative (do this only once after all nodes are merged)
        Node cycleRep = sccFind(cycle.get(0).edge.getDestination());
        getCycleEdges(cycleRep).addAll(cycle);
        
        // After setting the cost differences, decrease all edge weights entering the cycle by sigma
        for (TarjanForestNode node : cycle) {
            Node v = node.edge.getDestination();
            ((PairingHeap) getQueue(v)).decreaseAllKeys(sigma);
        }
    }

    private TarjanForestNode getMaxWeightEdgeInCycle(List<TarjanForestNode> cycle) {
        return cycle.stream().max(Comparator.comparing(n -> n.edge.getWeight())).orElseThrow();
    }

    private void updateSCCMaxWeightEdge(Node rep) { // TODO - isto faz alguma coisa?????
        List<TarjanForestNode> cycleEdges = getCycleEdges(rep);
        if (!cycleEdges.isEmpty()) {
            Edge maxEdge = cycleEdges.stream().max(Comparator.comparing(n -> n.edge.getWeight())).orElseThrow().edge;
                                                                                                                                                                                                                                                                                                                                                                                                        // max.set(sccFind(rep).getId(), maxEdge.getDestination());
        }
    }

    private Node getSCCMaxTarget(Node v) {
        // return max.get(sccFind(v).getId());
        Edge e = max.get(ufSCC.find(v.getId())); // TODO - sccFind
        return e != null ? e.getDestination() : null;
    }

    private List<TarjanForestNode> deleteAncestors(TarjanForestNode nodeF, List<TarjanForestNode> N) {
        while (nodeF != null) {
            List<TarjanForestNode> copy_arr = new ArrayList<>(nodeF.getChildren());
            for (TarjanForestNode child : copy_arr) {
                Edge e = child.edge;
                child.setParent(null);
                N.add(child);
            }
            N.remove(nodeF);
            TarjanForestNode parent = nodeF.getParent();
            // nodeF = nodeF.getParent();
            nodeF.setParent(null);
            nodeF = parent;
        }
        return N;
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
            // Node maxNode = max.get(i);
            Edge e = max.get(ufSCC.find(v.getId()));
            Node maxNode = e != null ? e.getDestination() : null;
            System.out.println("SCC represented by node " + v.getId() + ": max edge target = " + (maxNode != null ? maxNode.getId() : "null"));
        }
    }

    private void printLeaves() {
        System.out.println("Printing leaves:");
        for (int i = 0; i < leaves.length; i++) {
            TarjanForestNode leaf = leaves[i];
            // System.out.println("pi[" + i + "]: " + (leaf != null ? leaf.edge.toString() : "null"));
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
            // System.out.println("\tIterating over leaf " + leaf);
        
            while (leaf != null && leaf.getParent() != null) {
                // System.out.println("\t\tVisiting parent " + leaf.getParent());
                leaf = leaf.getParent();
            }
            // System.out.println("\t\tAdding root " + leaf + " to rootsList");
            if (leaf != null && !rootsList.contains(leaf)) {
                // System.out.println("\t\tAdded root");
                rootsList.add(leaf);
            }
        }
        return rootsList;
    }

    private Edge extractMinIncidentEdge(Node v) {
        // After cycle contractions, v might be in an SCC. We should extract from the representative's queue.
        Node rep = sccFind(v);
        MergeableHeapInterface<HeapNode> q = getQueue(rep);
        if (q.isEmpty()) {
            return null;
        }
        Edge e = q.extractMin().getEdge();
        // Skip edges that are internal to the SCC represented by v
        // An edge (u → w) entering rep's queue has destination w in rep's SCC
        // The edge is internal if the source u is also in rep's SCC
        while (!q.isEmpty() && ufSCC.find(e.getSource().getId()) == ufSCC.find(v.getId())) {
            e = q.extractMin().getEdge();
        }
        if (ufSCC.find(e.getSource().getId()) == ufSCC.find(v.getId())) {
            return null;
        }
        return e;
    }

    private void mergeCycleQueues(Node rep, List<TarjanForestNode> cycle) {
        // Original logic: merge queues of destination nodes in the cycle
        for (TarjanForestNode n : cycle) {
            Node dest = n.edge.getDestination();
            Node destRep = sccFind(dest);
            if (destRep != rep && !getQueue(rep).isEmpty() && !getQueue(destRep).isEmpty()) {
                getQueue(rep).merge(getQueue(destRep));
            }
        }
        
        // Additional fix: also merge queues of source nodes in the cycle
        // For example, in cycle {(3→2)} with rep=2, we need to merge queue[3]
        for (TarjanForestNode n : cycle) {
            Node source = n.edge.getSource();
            Node sourceRep = sccFind(source);
            // If the source is in the same SCC (after union) but isn't the rep itself, merge its queue
            if (sourceRep == rep && source != rep && !getQueue(source).isEmpty() && !getQueue(rep).isEmpty()) {
                getQueue(rep).merge(getQueue(source));
            }
        }
    }

    private void contractionPhase() {
        while (!roots.isEmpty()) {
            Node r = roots.remove(0);
            Edge e = extractMinIncidentEdge(r);
            if (e == null) {
                rset.add(r);
                continue;
            }

            TarjanForestNode minNode = createMinNode(e);

            Node u = e.getSource();
            if (wccFind(u) != wccFind(r)) { // No cycle formed, safe to add edge
                inEdgeNode.set(sccFind(r).getId(), minNode);
                wccUnion(u, r);
                b.add(e);
            }
            else { // Contract cycle
                inEdgeNode.set(sccFind(r).getId(), null);
                List<TarjanForestNode> cycle = new ArrayList<>(); cycle.add(minNode);
                Map<TarjanForestNode, Edge> map = new HashMap<>();
                map.put(minNode, e);

                u = sccFind(u);
                while (inEdgeNode.get(u.getId()) != null) { // Build cycle
                    TarjanForestNode node = inEdgeNode.get(u.getId());
                    cycle.add(node);
                    map.put(node, node.edge);
                    u = sccFind(node.edge.getSource());
                }

                TarjanForestNode maxWeightEdge = getMaxWeightEdgeInCycle(cycle);
                int sigma = maxWeightEdge.edge.getWeight();
                updateReducedCosts(cycle, sigma, map);

                for (TarjanForestNode n: cycle) { // Perform union of the nodes in the cycle
                    sccUnion(n.edge.getSource(), n.edge.getDestination());
                }

                Node rep = sccFind(maxWeightEdge.edge.getDestination());
                
                roots.add(sccFind(rep));
                updateSCCMaxWeightEdge(rep);
                mergeCycleQueues(rep, cycle);

                /** Prepare structs for the next level */
                levels++;
                branchings.add(new ArrayList<>(b));
                contractedNodes.add(rep);
                sccStacks.add(ufSCC.clone());
                cycles.add(cycle.stream().map(n -> n.edge).collect(Collectors.toList()));
                for (TarjanForestNode n : cycle) {
                    b.remove(n.edge);
                }
                max.add(maxWeightEdge.edge);
            }
        }
    }

    private boolean isRoots(List<Edge> edges, Node n, int level) {
        for (Edge e : edges) {
            if (sccFind(e.getDestination(), level) == n) {
                return false;
            }
        }
        return true;
    }

    private List<Edge> expand() {
        if (levels == 0) { // No cycles were contracted
            return b;
        }

        List<Edge> edges = new ArrayList<>(b);
        while (levels > 0) {
            levels--;
            Node n = contractedNodes.remove(levels);
            boolean isRoot = isRoots(edges, n, levels);
            if (isRoot) {
                // Add all edges in the cycle except the maximum weight edge
                List<Edge> toAdd = cycles.get(levels).stream()
                    .filter(e -> e != max.get(levels) && !edges.contains(e))
                    .collect(Collectors.toList());
                edges.addAll(toAdd);
            }
            else {
                // Add cycle edges except the max and except edges already in the branching
                List<Edge> toAdd = cycles.get(levels).stream()
                    .filter(e -> e != max.get(levels) && !edges.contains(e))
                    .collect(Collectors.toList());
                edges.addAll(toAdd);
            }
        }
        return edges;
    }

    private List<Edge> expansionPhase() {
        // System.out.println("TarjanArborescence (Expansion): starting expansion phase...");
        List<Edge> H = new ArrayList<>(); // optimal arborescence
        List<Node> R = rset.stream().map(v -> getSCCMaxTarget(v)).collect(Collectors.toCollection(ArrayList::new)); // roots of H
        // List<TarjanForestNode> leavesList = Stream.of(leaves).filter(n -> n != null).collect(Collectors.toCollection(ArrayList::new));
        // System.out.println("TarjanArborescence (Expansion): R = " + R);
        System.out.println("<--------------------------------------------------->");
        System.out.println("SCC uf = " + ufSCC);
        printMaxEdges();
        System.out.println("R = " + R.stream().map(n -> n.getID()).collect(Collectors.toList()));
        List<TarjanForestNode> N = getRoots();
        System.out.println("N = " + N);
        printLeaves();

        List<Edge> edges = branchings.get(levels);
        while (!R.isEmpty()) {
            Node u = R.remove(0);
            System.out.println("Processing node u = " + u.getId());
            N = deleteAncestors(leaves[u.getID()], N);
            System.out.println("N after deleting ancestors of pi[" + u.getID() + "] = " + N);
        }
        // for (Node u : R) {
        //     N = deleteAncestors(leaves[u.getId()], N);
        // }
        // for (TarjanForestNode root : N) {
        //     Edge e = root.edge;
        //     H.add(e);
        //     Node v = e.getDestination();
        //     N = deleteAncestors(leaves[v.getId()], N);
        // }
        System.out.println("\n");
        while (!N.isEmpty()) {
            Edge e = (N.remove(0)).edge;
            System.out.println("Adding edge (" + e.getSource().getId() + "," + e.getDestination().getId() + ") to the optimal arborescence H");
            H.add(e);
            Node v = e.getDestination();
            System.out.println("Processing (destination) node u = " + v.getId());
            N = deleteAncestors(leaves[v.getId()], N);
            System.out.println("N after deleting ancestors of pi[" + v.getId() + "] = " + N);
        }

        // System.out.println("TarjanArborescence (Expansion): expansion phase completed.");
        // System.out.println("TarjanArborescence (Expansion): optimal forest H = " + H);
        System.out.println("<--------------------------------------------------->");
        return H;
    }

    @Override
    public Graph inferPhylogeny(Graph graph) {
        contractionPhase();
        List<Edge> forest = expand();
        return new Graph(forest);
    }
}
