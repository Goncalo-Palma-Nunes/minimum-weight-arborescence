package optimalarborescence.inference;

import optimalarborescence.graph.Node;

public class Action {
    public enum ActionType {
        ADD,
        REMOVE,
        UPDATE
    }

    private final ActionType type;
    private final int weight;
    private final Node u; // entry point
    private final Node v; // end point

    public Action(ActionType type, int weight, Node u, Node v) {
        this.type = type;
        this.weight = weight;
        this.u = u;
        this.v = v;
    }

    public ActionType getType() {
        return type;
    }

    public int getWeight() {
        return weight;
    }

    public Node getU() {
        return u;
    }

    public Node getV() {
        return v;
    }

    @Override
    public String toString() {
        return "Action{" +
                "type=" + type +
                ", weight=" + weight +
                ", u=" + u +
                ", v=" + v +
                '}';
    }
}
