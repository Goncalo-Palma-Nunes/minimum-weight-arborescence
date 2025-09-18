package optimalarborescence.datastructure;

import optimalarborescence.inference.Action;
import optimalarborescence.graph.Node;

import java.util.List;

public interface OnlineStruct {

    List<Action> add(Node node);

    List<Action> remove(Node node);

    List<Action> update(Node node, int newWeight);

}