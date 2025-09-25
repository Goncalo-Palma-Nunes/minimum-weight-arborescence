package optimalarborescence.inference;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;

import optimalarborescence.exception.NotImplementedException;
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

        System.out.println("\n\n\nnum nodes = " + graph.getNumNodes() + "\n\n\n");
        System.out.println("Length of inEdgeNode = " + inEdgeNode.size());
        System.out.println("Length of leaves = " + leaves.length);
        System.out.println("Length of max = " + max.size());
        System.out.println("Length of cycleEdgeNodes = " + cycleEdgeNodes.size());
        System.out.println("Length of queues = " + queues.size());
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
        System.out.println("wccFind on node with id " + v.getId());
        // System.out.println("nodes size = " + nodes.size());
        System.out.println("ufWCC size = " + ufWCC.getSize());
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
        for (TarjanForestNode node : cycle) { // Update reduced costs
            Edge edge = map.get(node);
            int cost = sigma - edge.getWeight(); // Compute cost reduction
            Node v = edge.getDestination();
            ufSCC.addWeight(v.getId(), cost);
            getCycleEdges(sccFind(v)).add(node);
        }
    }

    private TarjanForestNode getMaxWeightEdgeInCycle(List<TarjanForestNode> cycle) {
        return cycle.stream().max(Comparator.comparing(n -> n.edge.getWeight())).orElseThrow();
    }

    private void updateSCCMaxWeightEdge(Node rep) {
        max.set(sccFind(rep).getId(), max.get(rep.getId())); // Update maximum weight edge for the new SCC
    }

    private Node getSCCMaxTarget(Node v) {
        return max.get(sccFind(v).getId());
    }

    private List<TarjanForestNode> deleteAncestors(TarjanForestNode nodeF, List<TarjanForestNode> leavesList) {
        while (nodeF != null) {
            for (TarjanForestNode child : nodeF.getChildren()) {
                Edge e = child.edge;
                child.setParent(null);
                leavesList.add(child);
            }
            nodeF = nodeF.getParent();
        }
        return leavesList;
    }

    private void contractionPhase() {
        System.out.println("<--------------------------------------------------->");
        System.out.println("TarjanArborescence (Contraction): starting contraction phase...");
        // System.out.println("TarjanArborescence (Contraction): roots = " + roots);
        while (!roots.isEmpty()) {
            Node r = roots.remove(0);
            Edge e = null;

            MergeableHeapInterface<HeapNode> q = getQueue(r); // priority queue of edges entering r
            if (!emptyQueue(q)) {
                e = q.extractMin().getEdge();

                while (!emptyQueue(q) && ufSCC.find(e.getSource().getId()) == ufSCC.find(r.getId())) {
                    e = q.extractMin().getEdge();
                }
                if (ufSCC.find(e.getSource().getId()) == ufSCC.find(r.getId())) {
                    rset.add(r);
                    continue;
                }
            }
            else { rset.add(r); continue; } // Passar este 1º if/else para uma função?

            TarjanForestNode minNode = createMinNode(e);

            Node u = e.getSource();
            if (wccFind(u) != wccFind(r)) {
                inEdgeNode.set(r.getId(), minNode);
                wccUnion(u, r);
            }
            else {
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
        System.out.println("TarjanArborescence (Contraction): contraction phase completed.");
        System.out.println("<--------------------------------------------------->");
    }

    private List<Edge> expansionPhase() {
        System.out.println("TarjanArborescence (Expansion): starting expansion phase...");
        List<Edge> H = new ArrayList<>(); // optimal arborescence
        List<Node> R = rset.stream().map(v -> getSCCMaxTarget(v)).toList(); // roots of H
        List<TarjanForestNode> leavesList = Stream.of(leaves).filter(n -> n != null).toList();

        // while (!R.isEmpty()) {
        //     Node u = R.remove(0);
        //     leavesList = deleteAncestors(leaves[u.getID()], leavesList);
        // }
        for (Node u : R) {
            leavesList = deleteAncestors(leaves[u.getId()], leavesList);
        }
        for (TarjanForestNode leaf : leavesList) {
            Edge e = leaf.edge;
            H.add(e);
            Node v = e.getDestination();
            leavesList = deleteAncestors(leaves[v.getId()], leavesList);
        }
        // while (!leavesList.isEmpty()) {
        //     Edge e = leavesList.remove(0).edge;
        //     H.add(e);
        //     Node v = e.getDestination();
        //     leavesList = deleteAncestors(leaves[v.getID()], leavesList);
        // }

        System.out.println("TarjanArborescence (Expansion): expansion phase completed.");
        System.out.println("TarjanArborescence (Expansion): optimal forest H = " + H);
        System.out.println("<--------------------------------------------------->");
        return H;
    }

    @Override
    public Graph inferPhylogeny(Graph graph) {

        contractionPhase();
        List<Edge> forest = expansionPhase();
        System.out.println("TarjanArborescence (inferPhylogeny): optimal forest H = " + forest);
        
        return new Graph(forest);
    }
}
