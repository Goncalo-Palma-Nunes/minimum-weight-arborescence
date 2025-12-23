package optimalarborescence.unittests.graph;

import optimalarborescence.graph.Node;
import optimalarborescence.sequences.AllelicProfile;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

public class NodeTest {

    // Helper method to create AllelicProfile from string
    private AllelicProfile createProfile(String data) {
        Character[] chars = new Character[data.length()];
        for (int i = 0; i < data.length(); i++) {
            chars[i] = data.charAt(i);
        }
        return new AllelicProfile(chars, data.length());
    }

    List<AllelicProfile> mlstDataList = new ArrayList<AllelicProfile>() {{
        add(createProfile("AACGT"));
        add(createProfile("AAGCT"));
        add(createProfile("TTGCA"));
    }};
    List<Node> nodeList = new ArrayList<Node>() {{
        add(new Node(mlstDataList.get(0), 1));
        add(new Node(mlstDataList.get(1), 2));
        add(new Node(mlstDataList.get(2), 3));
    }};

    @Test
    public void testAddNeighbor() {
        nodeList.get(0).addNeighbor(nodeList.get(1), 5);
        nodeList.get(0).addNeighbor(nodeList.get(2));

        assertEquals(2, nodeList.get(0).getNeighbors().size());
        assertTrue(nodeList.get(0).getNeighbors().containsKey(nodeList.get(1)));
        assertTrue(nodeList.get(0).getNeighbors().containsKey(nodeList.get(2)));
        assertEquals(Integer.valueOf(5), nodeList.get(0).getNeighbors().get(nodeList.get(1)));
        assertEquals(Integer.valueOf(0), nodeList.get(0).getNeighbors().get(nodeList.get(2)));
    }

    @Test
    public void testCompareTo() {
        assertTrue(nodeList.get(0).compareTo(nodeList.get(1)) < 0);
        assertTrue(nodeList.get(1).compareTo(nodeList.get(0)) > 0);
        assertEquals(0, nodeList.get(0).compareTo(nodeList.get(0)));
    }
}
