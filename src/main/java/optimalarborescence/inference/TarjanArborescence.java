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

public class TarjanArborescence extends StaticAlgorithm {

    /** A list of vertices to be processed. Initialized with all the vertices in 𝑉 */
    private List<Node> roots;

    /** A set of the root components of the subgraph 𝐺′ = (𝑉, 𝐻) with no incident edges of positive value */
    private Set<Node> rset;

    /** A list that for each vertex 𝑣 stores the minimum weight edge incident in 𝑣 */
    private List<Edge> inEdgeNode;

    /** A list of F’s leaves */
    private TarjanForestNode[] leaves;

    /** A list of the maximum weight edge for each SCC */
    private List<Edge> max; // Q_max in Joaquim's thesis

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

    private Comparator<Edge> cmp;
    private Comparator<HeapNode> maxDisjointCmp;

    public TarjanArborescence() {
        this.roots = new ArrayList<>();
        this.rset = new TreeSet<>();
        this.inEdgeNode = new ArrayList<>();
        this.leaves = null;
        this.max = new ArrayList<>();
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
    public TarjanArborescence(Graph graph, Comparator<Edge> comparator) {
        super(graph);
        this.roots = graph.cloneNodeList();
        this.rset = new TreeSet<>();
        this.inEdgeNode = new ArrayList<>(graph.getNumNodes());
        this.leaves = new TarjanForestNode[graph.getNumNodes()];
        this.max = new ArrayList<>();
        this.ufSCC = new UnionFindStronglyConnected(graph.getNumNodes());
        this.ufWCC = new UnionFind(graph.getNumNodes());
        this.queues = new ArrayList<>();
        this.sccStacks = new ArrayList<>();
        this.b = new ArrayList<>();

        this.cmp = comparator;
        this.maxDisjointCmp = (o1, o2) -> {
            Edge e1 = o1.getEdge();
            Edge e2 = o2.getEdge();
            return cmp.compare(e1, e2);
        };

        for (int i = 0; i < graph.getNumNodes(); i++) { // TODO - passar para initializeDataStructures()
            inEdgeNode.add(null);
            queues.add(new PairingHeap(maxDisjointCmp));
        }
        initializeDataStructures();
    }

    private void initializeDataStructures() {
        branchings = new ArrayList<>();
        levels = 0;
        cycles = new ArrayList<>();
        contractedNodes = new ArrayList<>();
        for (Edge e : graph.getEdges()) {
            Node v = e.getDestination();
            getQueue(v).insert(new HeapNode(e, null, null));;
        }
    }

    public TarjanForestNode[] getLeaves() {
        return leaves;
    }

    public List<Node> getNodes() {
        return this.graph.getNodes();
    }

    protected MergeableHeapInterface<HeapNode> getQueue(Node v) {
        return queues.get(v.getId());
    }

    private Node wccFind(Node v) {
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

    private void updateReducedCosts(List<Edge> cycle, int sigma, Map<Node, Edge> map) {

        for (Edge e : cycle) {
            Node v = e.getDestination();
            Node vRep = sccFind(v);
            int reduction = map.get(vRep).getWeight() - sigma;
            ((PairingHeap) getQueue(vRep)).decreaseAllKeys(reduction);
        }        
    }

    private Edge getMaxWeightEdgeInCycle(List<Edge> cycle) {
        return cycle.stream().max(Comparator.comparing(Edge::getWeight)).orElseThrow();
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

    private Edge extractMinIncidentEdge(Node v) {
        // After cycle contractions, v might be in an SCC. We should extract from the representative's queue.
        Node rep = sccFind(v);
        MergeableHeapInterface<HeapNode> q = getQueue(rep);
        if (q.isEmpty()) {
            return null;
        }
        Edge e = q.extractMin().getEdge();
        while (!q.isEmpty() && sccFind(e.getSource()) == sccFind(e.getDestination())) {
            e = q.extractMin().getEdge();
        }

        if (sccFind(e.getSource()) == sccFind(e.getDestination())) {
            return null;
        }

        return e;
    }

    private void mergeCycleQueues(Node rep, List<Edge> cycle) {
        // Merge queues of destination nodes in the cycle
        for (int i = 0; i < cycle.size(); i++) {
            Edge n = cycle.get(i);
            Node dest = n.getDestination();
            Node destRep = sccFind(dest);
            if (destRep != rep && !getQueue(rep).isEmpty() && !getQueue(destRep).isEmpty()) {
                getQueue(rep).merge(getQueue(destRep));
            }
        }
        
        // Also merge queues of source nodes in the cycle
        for (int i = 0; i < cycle.size(); i++) {
            Edge n = cycle.get(i);
            Node source = n.getSource();
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
                continue;
            }

            Node u = e.getSource();
            Node v = e.getDestination();
            if (wccFind(u) != wccFind(v)) { // No cycle formed, safe to add edge
                inEdgeNode.set(sccFind(v).getId(), e);
                wccUnion(u, r);
                b.add(e);
            }
            else { // Contract cycle
                inEdgeNode.set(sccFind(r).getId(), null);
                List<Edge> cycle = new ArrayList<>();
                cycle.add(e);
                Map<Node, Edge> map = new HashMap<>();
                map.put(sccFind(v), e); // Map the first destination to the edge that closes the cycle
                Node i = sccFind(u);
                while (inEdgeNode.get(i.getId()) != null) {
                    Edge inEdge = inEdgeNode.get(i.getId());
                    cycle.add(inEdge);
                    map.put(i, inEdge); // Map each node to its incoming edge
                    i = sccFind(inEdge.getSource());
                }

                Edge maxEdge = getMaxWeightEdgeInCycle(cycle);
                int sigma = maxEdge.getWeight();
                updateReducedCosts(cycle, sigma, map);

                for (Edge n: cycle) { // Perform union of the nodes in the cycle
                    sccUnion(n.getSource(), n.getDestination());
                }

                Node rep = sccFind(maxEdge.getDestination());
                roots.add(rep);
                mergeCycleQueues(rep, cycle);

                /** Prepare structs for the next level */
                levels++;
                max.add(maxEdge);
                cycles.add(new ArrayList<>(cycle));
                contractedNodes.add(rep);
                branchings.add(new ArrayList<>(b));
                sccStacks.add(ufSCC.clone());
                for (Edge n : cycle) {
                    b.remove(n);
                }
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
            if (isRoots(edges, n, levels)) {
                // Add all edges in the cycle except the maximum weight edge
                List<Edge> toAdd = cycles.get(levels).stream()
                    .filter(e -> e != max.get(levels) && !edges.contains(e))
                    .collect(Collectors.toList());
                edges.addAll(toAdd);
            }
            else {
                HashSet<Integer> endpoints = new HashSet<>();
                for (Edge e : edges) {
                    int destId = e.getDestination().getId();
                    endpoints.add(destId);
                }
                for (Edge e : cycles.get(levels)) {
                    int destId = e.getDestination().getId();
                    if (!endpoints.contains(destId)) {
                        edges.add(e);
                        endpoints.add(destId);
                    }
                }
            }
        }
        return edges;
    }

    @Override
    public Graph inferPhylogeny(Graph graph) {
        contractionPhase();
        List<Edge> forest = expand();
        return new Graph(forest);
    }
}
