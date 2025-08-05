package optimalarborescence.graph;

import java.io.Serializable;

public class Edge implements Serializable, Comparable<Edge> {
    
    Node source;
    Node destination;
    int weight;
    private static final long serialVersionUID = 1L;



    public Edge(Node source, Node destination, int weight) {
        this.source = source;
        this.destination = destination;
        this.weight = weight;
    }

    /* ******************************************
     *
     *            Getters and Setters
     * 
     * ******************************************/

    public Node getSource() {
        return source;
    }

    public Node getDestination() {
        return destination;
    }

    public int getWeight() {
        return weight;
    }

    public long getSerialVersionUID() {
        return serialVersionUID;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public void setSource(Node source) {
        this.source = source;
    }

    public void setDestination(Node destination) {
        this.destination = destination;
    }


    /* ******************************************
     *
     *            Overridden Methods
     * 
     * ******************************************/

    @Override
    public String toString() {
        return "Edge {" +
                "source=" + source.getMLSTdata() +
                ", destination=" + destination.getMLSTdata() +
                ", weight=" + weight +
                " }";
    }

    @Override
    public int compareTo(Edge other) {
        return Integer.compare(this.weight, other.weight);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Edge)) return false;
        Edge other = (Edge) obj;
        return this.source.equals(other.source) &&
               this.destination.equals(other.destination) &&
               this.weight == other.weight;
    
            }
}
