package optimalarborescence.unittests.nearestneighbour;

import optimalarborescence.nearestneighbour.Point;
import optimalarborescence.sequences.AllelicProfile;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PointTest {

    private Point<Character> point1;
    private Point<Character> point2;
    private Point<Character> point3;
    private Point<Character> point4;
    private List<Point<Character>> points;

    @BeforeEach
    public void setUp() {
        point1 = new Point<>(1, createAllelicProfile("AACGTA"));
        point2 = new Point<>(4, createAllelicProfile("TTGCA"));
        point3 = new Point<>(2, createAllelicProfile("AACGTA"));
        point4 = new Point<>(3, createAllelicProfile("AAGCCT"));
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

    private AllelicProfile createAllelicProfile(String sequence) {
        Character[] data = new Character[sequence.length()];
        for (int i = 0; i < sequence.length(); i++) {
            data[i] = sequence.charAt(i);
        }
        return new AllelicProfile(data, sequence.length());
    }

    private AllelicProfile createEmptyAllelicProfile() {
        return new AllelicProfile(new Character[0], 0);
    }

    @Test
    public void testNullSequence() {
        try {
            AllelicProfile nullProfile = null;
            new Point<>(5, nullProfile);
            fail("Expected IllegalArgumentException for null sequence");
        } catch (IllegalArgumentException e) {
            // Expected exception
            assertEquals("Sequence cannot be null or empty", e.getMessage());
        }
    }

    @Test
    public void testEmptySequence() {
        try {
            new Point<>(5, createEmptyAllelicProfile());
            fail("Expected IllegalArgumentException for empty sequence");
        } catch (IllegalArgumentException e) {
            // Expected exception
            assertEquals("Sequence cannot be null or empty", e.getMessage());
        }
    }

    @Test
    public void testNegativeId() {
        try {
            new Point<>(-1, createAllelicProfile("ACGT"));
            fail("Expected IllegalArgumentException for negative ID");
        } catch (IllegalArgumentException e) {
            // Expected exception
            assertEquals("ID must be a non-negative integer", e.getMessage());
        }
    }

    @Test
    public void testGetters() {
        assertEquals(1, point1.getId());
        assertNotNull(point1.getSequence());
        assertEquals(6, point1.getSequence().getLength());
    }

    @Test
    public void testSequenceElementAccess() {
        Point<Character> p = new Point<>(0, createAllelicProfile("ACGT"));
        assertEquals('A', p.getSequence().getElementAt(0));
        assertEquals('C', p.getSequence().getElementAt(1));
        assertEquals('G', p.getSequence().getElementAt(2));
        assertEquals('T', p.getSequence().getElementAt(3));
    }

    @Test
    public void testEqualPointsAllelicProfiles() {
        Point<Character> p1 = new Point<>(point1.getId(), createAllelicProfile("AACGTA"));
        assertEquals(p1, point1);
    }

    @Test
    public void testEqualPointsSequenceTypingData() {
        Point<Long> p1 = new Point<>(1, new optimalarborescence.sequences.SequenceTypingData(new Long[]{1L, 2L, 3L, 4L}, 4));
        Point<Long> p2 = new Point<>(1, new optimalarborescence.sequences.SequenceTypingData(new Long[]{1L, 2L, 3L, 4L}, 4));
        assertEquals(p1, p2);
    }

    @Test
    public void testUnequalPoints() {
        assertNotEquals(point1, point2);

        Point<Character> p2 = new Point<>(point1.getId() + 1, createAllelicProfile("AACGTA"));
        assertNotEquals(p2, point1);

        p2 = new Point<>(point1.getId(), createAllelicProfile("TTTTTT"));
        assertNotEquals(p2, point1);
    }
}
