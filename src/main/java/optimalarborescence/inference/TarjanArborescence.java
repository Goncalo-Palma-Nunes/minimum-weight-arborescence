package optimalarborescence.inference;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

import optimalarborescence.exception.NotImplementedException;
import optimalarborescence.graph.Graph;
import optimalarborescence.graph.Edge;
import optimalarborescence.graph.Node;
import optimalarborescence.datastructure.UnionFind;
import optimalarborescence.datastructure.heap.*;

public class TarjanArborescence extends StaticAlgorithm {


    /***********************************************************************************
     *               Auxiliary Data Structures for Tarjan's Algorithm                  *
     ***********************************************************************************/
 
    public class TarjanForestNode {
        Edge edge;
        List<TarjanForestNode> children; // TODO - passar a uma left child right sibling representation

        /* Auxiliary data structure for Tarjan's algorithm. TarjanForestNode is used to build the 
         * forest F described in Camerini's correction of Tarjan's optimum branching algorithm.
         */
        public TarjanForestNode(Edge edge) {
            this.edge = edge;
            this.children = null;
        }

        /* Checks if the node is a leaf node. */
        public boolean isLeaf() {
            return children == null || children.isEmpty();
        }

        public List<TarjanForestNode> getChildren() {
            return children;
        }

        public void addChild(TarjanForestNode child) {
            if (children == null) {
                children = new ArrayList<>();
            }
            children.add(child);
        }
    }


    private List<TarjanForestNode> forest;

    /* A list of vertices to be processed. Initialized with all the vertices in 𝑉 */
    private List<Node> roots;

    /* A set of the root components of the subgraph 𝐺′ = (𝑉, 𝐻) with no incident edges of positive value */
    private Set<Node> rset;

    /* A list that for each vertex 𝑣 stores a vertex of 𝐹 associated with the minimum weight
    edge incident in 𝑣 */
    private List<TarjanForestNode> inEdgeNode;

    /* A list of 𝐻’s leaves */
    private List<Node> leaves;

    /* A list of the maximum weight edge for each SCC */
    private List<Edge> max;

    /* A list that stores for each representative cycle vertex 𝑣 the list of cycle edge nodes in 𝐻 */
    private List<List<Edge>> cycleEdgeNodes;

    /* A union-find data structure to maintain the strongly connected components of 𝐻 */
    private UnionFind uf;

    private List<MergeableHeapInterface<HeapNode>> queues;

    /* Constructor for TarjanArborescence. This class is an implementation of
     * Tarjan's optimum branching algorithm as corrected by Camerini et al.
     */
    public TarjanArborescence(Graph graph) {
        super(graph);
        this.forest = new ArrayList<>();
        this.roots = new ArrayList<>();
        this.rset = new TreeSet<>();
        //this.inEdgeNode = new ArrayList<>(graph.getNumNodes());
        this.inEdgeNode = new ArrayList<>();
        this.leaves = new ArrayList<>();
        this.max = new ArrayList<>();
        this.cycleEdgeNodes = new ArrayList<>();
        //this.uf = new UnionFind(graph.getNumNodes());
        this.uf = new UnionFind(-1);
        this.queues = new ArrayList<>();
    }

    private MergeableHeapInterface<HeapNode> getQueue(Node v) {
        return queues.get(v.getId());
    }

    private boolean emptyQueue(MergeableHeapInterface<HeapNode> q) {
        return q.isEmpty();
    }



    @Override
    public Graph inferPhylogeny(Graph graph) {

        while (!roots.isEmpty()) {
            Node r = roots.remove(0);

            MergeableHeapInterface<HeapNode> q = getQueue(r); // priority queue of edges entering r
            if (!emptyQueue(q)) {
                Edge e = q.extractMin();

                while (!emptyQueue(q) && uf.find(e.getSource().getId()) == uf.find(r.getId())) {
                    e = q.extractMin();
                }
                if (uf.find(e.getSource().getId()) == uf.find(r.getId())) {
                    rset.add(r);
                    continue;
                }
            }
            else { rset.add(r); continue; }
        }

        throw new NotImplementedException("Tarjan's optimum branching algorithm is not yet implemented.");
    }
}
