package optimalarborescence.unittests.distancefunction;

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;

import optimalarborescence.distance.HammingDistance;
import optimalarborescence.sequences.AllelicProfile;
import optimalarborescence.sequences.Sequence;
import optimalarborescence.sequences.SequenceTypingData;

public class HammingDistanceTest {

    private HammingDistance hammingDistance = new HammingDistance();
    private List<String> testStrings = new ArrayList<>();

    @Before
    public void setUp() {
        testStrings.add("AACGT"); // index 0
        testStrings.add("AAGCT"); // index 1
        testStrings.add("TTGCA"); // index 2
        testStrings.add("EEEEE"); // index 3
        testStrings.add("");      // index 4
        testStrings.add("A");     // index 5
    }

    private AllelicProfile createAllelicProfile(String sequence) {
        Character[] data = new Character[sequence.length()];
        for (int i = 0; i < sequence.length(); i++) {
            data[i] = sequence.charAt(i);
        }
        return new AllelicProfile(data, sequence.length());
    }

    private SequenceTypingData createSequenceTypingData(Integer... values) {
        return new SequenceTypingData(values, values.length);
    }

    @Test
    public void testCalculateSequenceWithAllelicProfile() {
        AllelicProfile seq1 = createAllelicProfile("AACGT");
        AllelicProfile seq2 = createAllelicProfile("AAGCT");
        AllelicProfile seq3 = createAllelicProfile("TTGCA");
        AllelicProfile seq4 = createAllelicProfile("AACGT");

        assertEquals("Hamming distance between identical sequences", 0.0, hammingDistance.calculate(seq1, seq4), 0.0);
        assertEquals("Hamming distance between AACGT and AAGCT", 2.0, hammingDistance.calculate(seq1, seq2), 0.0);
        assertEquals("Hamming distance between AACGT and TTGCA", 5.0, hammingDistance.calculate(seq1, seq3), 0.0);
    }

    @Test
    public void testCalculateSequenceWithSequenceTypingData() {
        SequenceTypingData seq1 = createSequenceTypingData(1, 2, 3, 4, 5);
        SequenceTypingData seq2 = createSequenceTypingData(1, 2, 3, 4, 5);
        SequenceTypingData seq3 = createSequenceTypingData(1, 3, 3, 5, 5);
        SequenceTypingData seq4 = createSequenceTypingData(10, 20, 30, 40, 50);

        assertEquals("Hamming distance between identical typing data", 0.0, hammingDistance.calculate(seq1, seq2), 0.0);
        assertEquals("Hamming distance with 2 differences", 2.0, hammingDistance.calculate(seq1, seq3), 0.0);
        assertEquals("Hamming distance with all differences", 5.0, hammingDistance.calculate(seq1, seq4), 0.0);
    }

    @Test
    public void testCalculateSequenceWithGenericSequence() {
        // Test with a custom generic Sequence implementation
        Sequence<String> seq1 = new Sequence<String>(new String[]{"A", "B", "C"}, 3) {
            @Override
            public String getElementAt(int index) {
                return getData()[index];
            }

            @Override
            public int compareAt(int index, Sequence<?> other) {
                String thisElement = getElementAt(index);
                String otherElement = (String) other.getElementAt(index);
                return thisElement.compareTo(otherElement);
            }

            @Override
            public List<Integer> getPositionsWithMissingData() { return null; } // Not used in this test

            @Override
            public boolean isMissingDataAt(int index) { return false; } // Not used in this test
        };

        Sequence<String> seq2 = new Sequence<String>(new String[]{"A", "B", "C"}, 3) {
            @Override
            public String getElementAt(int index) {
                return getData()[index];
            }

            @Override
            public int compareAt(int index, Sequence<?> other) {
                String thisElement = getElementAt(index);
                String otherElement = (String) other.getElementAt(index);
                return thisElement.compareTo(otherElement);
            }

            @Override
            public List<Integer> getPositionsWithMissingData() { return null; } // Not used in this test

            @Override
            public boolean isMissingDataAt(int index) { return false; } // Not used in this test
        };

        Sequence<String> seq3 = new Sequence<String>(new String[]{"A", "X", "C"}, 3) {
            @Override
            public String getElementAt(int index) {
                return getData()[index];
            }

            @Override
            public int compareAt(int index, Sequence<?> other) {
                String thisElement = getElementAt(index);
                String otherElement = (String) other.getElementAt(index);
                return thisElement.compareTo(otherElement);
            }

            @Override
            public List<Integer> getPositionsWithMissingData() { return null; } // Not used in this test

            @Override
            public boolean isMissingDataAt(int index) { return false; } // Not used in this test
        };

        assertEquals("Hamming distance between identical generic sequences", 0.0, hammingDistance.calculate(seq1, seq2), 0.0);
        assertEquals("Hamming distance with 1 difference in generic sequences", 1.0, hammingDistance.calculate(seq1, seq3), 0.0);
    }

    @Test
    public void testCalculateSequenceUnequalLengths() {
        AllelicProfile seq1 = createAllelicProfile("AACGT");
        AllelicProfile seq2 = createAllelicProfile("AAC");

        try {
            hammingDistance.calculate(seq1, seq2);
            fail("Expected IllegalArgumentException for sequences of different lengths");
        } catch (IllegalArgumentException e) {
            assertEquals("Sequences must be of equal length", e.getMessage());
        }
    }

    @Test
    public void testCalculateSequenceEmptySequences() {
        AllelicProfile seq1 = createAllelicProfile("");
        AllelicProfile seq2 = createAllelicProfile("");

        assertEquals("Hamming distance between empty sequences", 0.0, hammingDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testCalculateSequenceMixedTypes() {
        AllelicProfile allelicProfile = createAllelicProfile("ACGT");
        SequenceTypingData typingData = createSequenceTypingData(1, 2, 3, 4);

        try {
            hammingDistance.calculate(allelicProfile, typingData);
            fail("Expected IllegalArgumentException when comparing different sequence types");
        } catch (IllegalArgumentException e) {
            // Expected exception - either from AllelicProfile or SequenceTypingData compareAt methods
            assertTrue("Exception message should indicate type mismatch", 
                e.getMessage().contains("Cannot compare"));
        }
    }
}
