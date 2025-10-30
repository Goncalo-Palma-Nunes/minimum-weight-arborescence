package optimalarborescence.unittests.nearestneighbour;

import optimalarborescence.nearestneighbour.Point;
import optimalarborescence.distance.DistanceFunction;
import optimalarborescence.distance.HammingDistance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class PointTest {

    private Point point1;
    private Point point2;
    private Point point3;
    private Point point4;
    private List<Point> points;

    @BeforeEach
    public void setUp() {
        point1 = new Point(1, "AACGTA");
        point2 = new Point(4, "TTGCA");
        point3 = new Point(2, "AACGTA");
        point4 = new Point(3, "AAGCCT");
        points = new ArrayList<>();
        points.add(point1);
        points.add(point2);
        points.add(point3);
        points.add(point4);
    }

    @AfterEach
    public void tearDown() {
        point1 = null;
        point2 = null;
        point3 = null;
        point4 = null;
        points = null;
    }

    @Test
    public void testNullSequence() {
        try {
            new Point(5, null);
            fail("Expected IllegalArgumentException for null sequence");
        } catch (IllegalArgumentException e) {
            // Expected exception
            assertEquals("Sequence cannot be null or empty", e.getMessage());
        }
    }

    @Test
    public void testEmptySequence() {
        try {
            new Point(5, "");
            fail("Expected IllegalArgumentException for empty sequence");
        } catch (IllegalArgumentException e) {
            // Expected exception
            assertEquals("Sequence cannot be null or empty", e.getMessage());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", " ", "\n", "ACGTX", "1234", "AC GT", "acgt", "ACGT!", "TN"})
    public void testInvalidCharactersInSequence(String invalidSequence) {
        try {
            new Point(5, invalidSequence);
            fail("Expected IllegalArgumentException for invalid characters in sequence");
        } catch (IllegalArgumentException e) {
            // Expected exception
            assertEquals("Sequence must contain only A, C, G, T characters", e.getMessage());
        }
    }

    @Test
    public void testNegativeId() {
        try {
            new Point(-1, "ACGT");
            fail("Expected IllegalArgumentException for negative ID");
        } catch (IllegalArgumentException e) {
            // Expected exception
            assertEquals("ID must be a non-negative integer", e.getMessage());
        }
    }

    @Test
    public void testGetters() {
        assertEquals(1, point1.getId());
        assertEquals("AACGTA", point1.getSequence());
        assertNotNull(point1.getBitArray());
        assertEquals(12, point1.getBinaryRepresentation().length()); // 6 bases * 2 bits each
    }

    @Test
    public void testBinaryRepresentation() {
        Point p = new Point(0, "ACGT");
        String expectedBinary = "00011011"; // A=00, C=01, G=10, T=11
        assertEquals(expectedBinary, p.getBinaryRepresentation());
    }

    @Test
    public void testEqualPoints() {
        Point p1 = new Point(point1.getId(), point1.getSequence());
        assertEquals(p1, point1);
    }

    @Test
    public void testUnequalPoints() {
        assertNotEquals(point1, point2);

        Point p2 = new Point(point1.getId() + 1, point1.getSequence());
        assertNotEquals(p2, point1);

        p2 = new Point(point1.getId(), "TTTTTT");
        assertNotEquals(p2, point1);
    }
}
