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
            this.children = null;
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
            this.parent = parent;
            // Adicionar como child ao parent aqui?
            return this.parent;
        }

        protected void addChild(TarjanForestNode child) {
            if (children == null) {
                children = new ArrayList<>();
            }
            children.add(child);
        }
    }


    // private List<TarjanForestNode> forest; // Guardando as leafs e um parent pointer, não precisamos disto diria eu

    /** A list of vertices to be processed. Initialized with all the vertices in 𝑉 */
    private List<Node> roots;

    /** A set of the root components of the subgraph 𝐺′ = (𝑉, 𝐻) with no incident edges of positive value */
    private Set<Node> rset;

    /** A list that for each vertex 𝑣 stores a vertex of 𝐹 associated with the minimum weight
    edge incident in 𝑣 */
    private List<TarjanForestNode> inEdgeNode;

    /** A list of F’s leaves */
    private List<TarjanForestNode> leaves;

    /** A list of the maximum weight edge for each SCC */
    private List<Edge> max;

    /** A list that stores for each representative cycle vertex 𝑣 the list of cycle edge nodes in F */
    //private List<List<Edge>> cycleEdgeNodes;
    private List<List<TarjanForestNode>> cycleEdgeNodes;

    /** A union-find data structure to maintain the strongly connected components of 𝐻 */
    private UnionFindStronglyConnected ufSCC;

    private UnionFind ufWCC;

    private List<MergeableHeapInterface<HeapNode>> queues;

    /** Constructor for TarjanArborescence. This class is an implementation of
     * Tarjan's optimum branching algorithm as corrected by Camerini et al.
     */
    public TarjanArborescence(Graph graph) {
        super(graph);
        // this.forest = new ArrayList<>();
        this.roots = new ArrayList<>();
        this.rset = new TreeSet<>();
        //this.inEdgeNode = new ArrayList<>(graph.getNumNodes());
        this.inEdgeNode = new ArrayList<>();
        this.leaves = new ArrayList<>();
        this.max = new ArrayList<>();
        this.cycleEdgeNodes = new ArrayList<>();
        //this.ufSCC = new UnionFind(graph.getNumNodes());
        this.ufSCC = new UnionFindStronglyConnected(-1);
        this.ufWCC = new UnionFind(-1);
        this.queues = new ArrayList<>();
    }

    private MergeableHeapInterface<HeapNode> getQueue(Node v) {
        return queues.get(v.getId());
    }

    private boolean emptyQueue(MergeableHeapInterface<HeapNode> q) {
        return q.isEmpty();
    }

    private TarjanForestNode createMinNode(Edge e) {  // Rever esta parte no paper
        Node r = e.getDestination();
        List<TarjanForestNode> cycleEdges = getCycleEdges(r);

        TarjanForestNode minNode = new TarjanForestNode(e);
        if (cycleEdges.isEmpty()) {
            //leaves.set(r.getId(), minNode);
            leaves.add(minNode);
        }
        else {
            for (TarjanForestNode ce : cycleEdges) {
                ce.setParent(minNode);
                minNode.addChild(ce);
            }
        }

        return minNode;
    }

    private List<TarjanForestNode> getCycleEdges(Node v) {
        return cycleEdgeNodes.get(ufSCC.find(v.getId()));
    }

    @Override
    public Graph inferPhylogeny(Graph graph) {

        while (!roots.isEmpty()) {
            Node r = roots.remove(0);
            Edge e = null;

            MergeableHeapInterface<HeapNode> q = getQueue(r); // priority queue of edges entering r
            if (!emptyQueue(q)) {
                e = q.extractMin();

                while (!emptyQueue(q) && ufSCC.find(e.getSource().getId()) == ufSCC.find(r.getId())) {
                    e = q.extractMin();
                }
                if (ufSCC.find(e.getSource().getId()) == ufSCC.find(r.getId())) {
                    rset.add(r);
                    continue;
                }
            }
            else { rset.add(r); continue; } // Passar este 1º if/else para uma função?

            TarjanForestNode minNode = createMinNode(e);

            Node u = e.getSource();
            if (ufWCC.find(u.getId()) != ufWCC.find(r.getId())) {
                inEdgeNode.set(r.getId(), minNode);
                ufWCC.union(u.getId(), r.getId());
            }
            else {
                inEdgeNode.set(r.getId(), null);
                List<TarjanForestNode> cycle = new ArrayList<>(); cycle.add(minNode);
                Map<TarjanForestNode, Edge> map = new HashMap<>();
                map.put(minNode, e);

                u = ufSCC.find(u.getId());
                while (inEdgeNode.get(u.getId()) != null) {
                    TarjanForestNode node = inEdgeNode.get(u.getId());
                    cycle.add(node);
                    map.put(node, node.edge);
                    u = ufSCC.find(node.edge.getSource().getId());
                }

                TarjanForestNode maxWeightEdge = cycle.stream().max(Comparator.comparing(n -> n.edge.getWeight())).orElseThrow();
                /** Value of the maximum weight edge in cycle */
                int sigma = maxWeightEdge.edge.getWeight();

                for (TarjanForestNode node : cycle) {
                    Edge edge = map.get(node);
                    int cost = sigma - edge.getWeight();
                    Node v = edge.getDestination();
                }

            }
        }
        throw new NotImplementedException("Tarjan's optimum branching algorithm is not yet implemented.");
    }
}
