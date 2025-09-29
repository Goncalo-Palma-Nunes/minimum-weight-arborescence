package optimalarborescence.inference;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;
import java.util.stream.Collectors;

import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.datastructure.UnionFind;
import optimalarborescence.datastructure.UnionFindStronglyConnected;
import optimalarborescence.datastructure.heap.*;

public class TarjanArborescence extends StaticAlgorithm {


    /***********************************************************************************
     *               Auxiliary Data Structures for Tarjan's Algorithm                  *
     ***********************************************************************************/
 
    public class TarjanForestNode {
        Edge edge;
        TarjanForestNode parent;
        List<TarjanForestNode> children; // TODO - passar a uma left child right sibling representation

        /** Auxiliary data structure for Tarjan's algorithm. TarjanForestNode is used to build the
         * forest F described in Camerini's correction of Tarjan's optimum branching algorithm.
         */
        protected TarjanForestNode(Edge edge) {
            this.edge = edge;
            this.children = new ArrayList<>();
            this.parent = null;
        }

        /* Checks if the node is a leaf node. */
        protected boolean isLeaf() {
            return children == null || children.isEmpty();
        }

        protected List<TarjanForestNode> getChildren() {
            return children;
        }

        protected TarjanForestNode getParent() {
            return parent;
        }

        protected TarjanForestNode setParent(TarjanForestNode parent) {
            if (this.parent != null) {
                this.parent.children.remove(this);
            }
            this.parent = parent;
            if (this.parent != null) {
                this.parent.addChild(this);
            }
            return this.parent;
        }

        protected void addChild(TarjanForestNode child) {
            // if (children == null) {
            //     children = new ArrayList<>();
            // }
            children.add(child);
        }

        @Override
        public String toString() {
            // return "TarjanForestNode(\nedge=" + edge + 
            // "\nparent=" + (parent != null ? parent.edge : null) + 
            // "\nchildren=" + children + "\n)";
            return "(" + edge.getSource().getId() + ", " + edge.getDestination().getId() + ")";
        }
    }

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
    private List<Node> max;

    /** A list that stores for each representative cycle vertex 𝑣 the list of cycle edge nodes in F */
    //private List<List<Edge>> cycleEdgeNodes;
    private List<List<TarjanForestNode>> cycleEdgeNodes;

    /** A union-find data structure to maintain the strongly connected components of 𝐻 */
    private UnionFindStronglyConnected ufSCC;

    private UnionFind ufWCC;

    private List<MergeableHeapInterface<HeapNode>> queues;

    // private List<Node> nodes;

    /** Constructor for TarjanArborescence. This class is an implementation of
     * Tarjan's optimum branching algorithm as corrected by Camerini et al.
     */
    public TarjanArborescence(Graph graph) {
        super(graph);
        // this.nodes = graph.getNodes();
        // this.roots = graph.getNodes();
        this.roots = graph.cloneNodeList();
        this.rset = new TreeSet<>();
        this.inEdgeNode = new ArrayList<>(graph.getNumNodes());
        // this.inEdgeNode = new ArrayList<>();
        // this.leaves = new ArrayList<>();
        // this.leaves = new TarjanForestNode[graph.getNumNodes()];
        this.leaves = new TarjanForestNode[graph.getNumNodes()]; // +1 to avoid issues with getID() calls
        this.max = new ArrayList<>();
        this.cycleEdgeNodes = new ArrayList<>();
        this.ufSCC = new UnionFindStronglyConnected(graph.getNumNodes());
        this.ufWCC = new UnionFind(graph.getNumNodes());
        this.queues = new ArrayList<>(); // TODO - inicializar as queues

        for (int i = 0; i < graph.getNumNodes(); i++) { // +1 to avoid issues with getID() calls
            inEdgeNode.add(null);
            max.add(graph.getNodes().get(i));
            cycleEdgeNodes.add(new ArrayList<>());
            queues.add(new PairingHeap());
        }
        initializeDataStructures();

        // System.out.println("\n\n\nnum nodes = " + graph.getNumNodes() + "\n\n\n");
        // System.out.println("Length of inEdgeNode = " + inEdgeNode.size());
        // System.out.println("Length of leaves = " + leaves.length);
        // System.out.println("Length of max = " + max.size());
        // System.out.println("Length of cycleEdgeNodes = " + cycleEdgeNodes.size());
        // System.out.println("Length of queues = " + queues.size());
    }

    private void initializeDataStructures() {
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

    private Node sccFind(Node v) {
        return getNodes().get(ufSCC.find(v.getId()));
    }

    private void sccUnion(Node u, Node v) {
        ufSCC.union(u.getId(), v.getId());
    }

    private void updateReducedCosts(List<TarjanForestNode> cycle, int sigma, Map<TarjanForestNode, Edge> map) {
        System.out.println("\tUpdating cycle's reduced costs with sigma = " + sigma);
        System.out.println("\tCycle: " + cycle);
        System.out.println("\tCycle edges before update: " + getCycleEdges(sccFind(cycle.get(0).edge.getDestination())));
        for (TarjanForestNode node : cycle) { // Update reduced costs
            Edge edge = map.get(node);
            int cost = sigma - edge.getWeight(); // Compute cost reduction
            Node v = edge.getDestination();
            ufSCC.addWeight(v.getId(), cost);
            getCycleEdges(sccFind(v)).addAll(cycle); // TODO - meti addAll porque antes só estava a adicionar a si próprio. Deve haver uma maneira mais eficiente (tipo meter todos a apontar para o mesmo sítio)
        }
        System.out.println("\tCycle edges after update: " + getCycleEdges(sccFind(cycle.get(1).edge.getDestination())));
    }

    private TarjanForestNode getMaxWeightEdgeInCycle(List<TarjanForestNode> cycle) {
        return cycle.stream().max(Comparator.comparing(n -> n.edge.getWeight())).orElseThrow();
    }

    private void updateSCCMaxWeightEdge(Node rep) {
        List<TarjanForestNode> cycleEdges = getCycleEdges(rep);
        Edge maxEdge = cycleEdges.stream().max(Comparator.comparing(n -> n.edge.getWeight())).orElseThrow().edge;
        max.set(sccFind(rep).getId(), maxEdge.getDestination());
    }

    private Node getSCCMaxTarget(Node v) {
        return max.get(sccFind(v).getId());
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
            Node maxNode = max.get(i);
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

    private void contractionPhase() {
        System.out.println("<--------------------------------------------------->");
        // System.out.println("TarjanArborescence (Contraction): starting contraction phase...");
        // System.out.println("TarjanArborescence (Contraction): roots = " + roots);
        while (!roots.isEmpty()) {
            printRoots();
            printQueues();

            Node r = roots.remove(0);
            Edge e = null;
            System.out.println("Popped root r = " + r);

            MergeableHeapInterface<HeapNode> q = getQueue(r); // priority queue of edges entering r
            System.out.println("Queue of edges entering r: " + q);
            if (!emptyQueue(q)) {
                e = q.extractMin().getEdge();
                System.out.print("Queue not empty");
                while (!emptyQueue(q) && ufSCC.find(e.getSource().getId()) == ufSCC.find(r.getId())) {
                    e = q.extractMin().getEdge();
                }
                System.out.println(", extracted edge e = " + e);
                if (ufSCC.find(e.getSource().getId()) == ufSCC.find(r.getId())) {
                    System.out.println("All edges in the queue form a loop. Add r to rset and process next node");
                    rset.add(r);
                    continue;
                }
            }
            else { System.out.println("Queue is empty. Nothing to process. Add r to rset and process next node"); rset.add(r); continue; } // Passar este 1º if/else para uma função?

            TarjanForestNode minNode = createMinNode(e);

            Node u = e.getSource();
            if (wccFind(u) != wccFind(r)) {
                System.out.println("No cycle formed. Adding edge e to the arborescence.");
                inEdgeNode.set(r.getId(), minNode);
                wccUnion(u, r);
            }
            else {
                System.out.println("Cycle formed. Contracting the cycle.");
                inEdgeNode.set(r.getId(), null);
                List<TarjanForestNode> cycle = new ArrayList<>(); cycle.add(minNode);
                Map<TarjanForestNode, Edge> map = new HashMap<>();
                map.put(minNode, e);

                u = sccFind(u);
                while (inEdgeNode.get(u.getId()) != null) {
                    TarjanForestNode node = inEdgeNode.get(u.getId());
                    cycle.add(node);
                    map.put(node, node.edge);
                    u = sccFind(node.edge.getSource());
                }
                System.out.println("Cycle nodes: " + cycle);

                TarjanForestNode maxWeightEdge = getMaxWeightEdgeInCycle(cycle);
                int sigma = maxWeightEdge.edge.getWeight();
                updateReducedCosts(cycle, sigma, map);

                for (TarjanForestNode n: cycle) { // Perform union of the nodes in the cycle
                    sccUnion(n.edge.getSource(), n.edge.getDestination());
                }

                Node rep = sccFind(maxWeightEdge.edge.getDestination());
                // inEdgeNode.set(rep.getId(), null); -----> ??????? delírios do copilot ?????
                
                roots.add(sccFind(rep));
                updateSCCMaxWeightEdge(rep);

                for (TarjanForestNode n : cycle) { // Merge queues involved in the cycle
                    if (sccFind(n.edge.getDestination()) != rep) {
                        getQueue(rep).merge(getQueue(sccFind(n.edge.getDestination())));
                    }
                }
            }
        }

        // System.out.println("TarjanArborescence (Contraction): rset = " + rset);
        // for (int i = 0; i < max.size(); i++) {
        //     System.out.println("TarjanArborescence (Contraction): max[" + i + "] = " + max.get(i));
        // }
        // for (int i = 0; i < inEdgeNode.size(); i++) {
        //     System.out.println("TarjanArborescence (Contraction): inEdgeNode[" + i + "] = " + inEdgeNode.get(i));
        // }
        // for (int i = 0; i < leaves.length; i++) {
        //     System.out.println("TarjanArborescence (Contraction): leaves[" + i + "] = " + leaves[i]);
        // }
        // System.out.println("TarjanArborescence: leaves = " + List.of(leaves));
        // System.out.println("TarjanArborescence (Contraction): contraction phase completed.");
        System.out.println("<--------------------------------------------------->");
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
        List<Edge> forest = expansionPhase();
        // System.out.println("TarjanArborescence (inferPhylogeny): optimal forest H = " + forest);
        
        return new Graph(forest);
    }
}
