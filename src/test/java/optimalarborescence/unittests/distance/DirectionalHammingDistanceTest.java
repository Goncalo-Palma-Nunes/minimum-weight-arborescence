package optimalarborescence.unittests.distance;

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

import optimalarborescence.distance.DirectionalHammingDistance;
import optimalarborescence.sequences.AllelicProfile;
import optimalarborescence.sequences.SequenceTypingData;

/**
 * Unit tests for DirectionalHammingDistance class.
 * 
 * This distance function is asymmetric and calculates distance from seq1 to seq2 where:
 * 1. If seq2 has missing data and seq1 doesn't: distance++
 * 2. ELSE IF seq1 has missing data and seq2 doesn't: do nothing
 * 3. ELSE IF seq1.compareAt(i, seq2) > 0 (seq1 value > seq2 value): distance++
 * 
 * Only one condition can be true per position, so each position contributes 0 or 1 to distance.
 */
public class DirectionalHammingDistanceTest {

    private DirectionalHammingDistance directionalDistance;

    @Before
    public void setUp() {
        directionalDistance = new DirectionalHammingDistance();
    }

    private AllelicProfile createAllelicProfile(String sequence) {
        Character[] data = new Character[sequence.length()];
        for (int i = 0; i < sequence.length(); i++) {
            data[i] = sequence.charAt(i);
        }
        return new AllelicProfile(data, sequence.length());
    }

    private SequenceTypingData createSequenceTypingData(Long... values) {
        return new SequenceTypingData(values, values.length);
    }

    // Basic functionality tests

    @Test
    public void testCalculateIdenticalSequences() {
        AllelicProfile seq1 = createAllelicProfile("ACGT");
        AllelicProfile seq2 = createAllelicProfile("ACGT");
        
        assertEquals("Distance between identical sequences should be 0", 
                     0.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testCalculateSeq1LessThanSeq2() {
        // When seq1 < seq2, compareAt returns < 0, so no distance added
        AllelicProfile seq1 = createAllelicProfile("AAAA");
        AllelicProfile seq2 = createAllelicProfile("TTTT");
        
        assertEquals("Distance should be 0 when seq1 < seq2 at all positions", 
                     0.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testCalculateSeq1GreaterThanSeq2() {
        // When seq1 > seq2, compareAt returns > 0, so distance++ for each position
        AllelicProfile seq1 = createAllelicProfile("TTTT");
        AllelicProfile seq2 = createAllelicProfile("AAAA");
        
        assertEquals("Distance should be 4 when seq1 > seq2 at all positions", 
                     4.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testCalculateMixedComparisons() {
        // T > A (pos 0): +1, A < C (pos 1): +0, G > D (pos 2): +1, A < X (pos 3): +0
        AllelicProfile seq1 = createAllelicProfile("TAGA");
        AllelicProfile seq2 = createAllelicProfile("ACDX");
        
        assertEquals("Distance should count only positions where seq1 > seq2", 
                     2.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    // Missing data tests - Asymmetric behavior

    @Test
    public void testAsymmetricMissingDataSecondHasMissing() {
        // seq1 has existing allele, seq2 has missing data -> distance++
        // Position 1: C vs ? -> seq2 missing (+1), else if doesn't execute
        AllelicProfile seq1 = createAllelicProfile("ACGT");
        AllelicProfile seq2 = createAllelicProfile("A?GT");
        
        // Position 1: seq2 missing (+1) only, comparison check skipped due to else if
        // Total: 1
        assertEquals("Distance counts missing data penalty only", 
                     1.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testBothHaveMissingDataAtSamePosition() {
        AllelicProfile seq1 = createAllelicProfile("A?GT");
        AllelicProfile seq2 = createAllelicProfile("A?GT");
        
        assertEquals("Distance should be 0 when both have missing data at same position", 
                     0.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testMultipleMissingDataPositions() {
        // Positions: A vs ? (+1: missing only), C vs ? (+1), G vs G (0), T vs ? (+1)
        AllelicProfile seq1 = createAllelicProfile("ACGT");
        AllelicProfile seq2 = createAllelicProfile("??G?");
        
        assertEquals("Distance should count all positions where seq2 has missing data", 
                     3.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testMixedMissingDataAndDifferences() {
        // Position 0: A vs A (same) -> 0
        // Position 1: C vs ? (seq2 missing) -> +1, else if skipped
        // Position 2: G vs T (G < T) -> 0
        // Position 3: T vs T (same) -> 0
        AllelicProfile seq1 = createAllelicProfile("ACGT");
        AllelicProfile seq2 = createAllelicProfile("A?TT");
        
        assertEquals("Distance should count missing data only", 
                     1.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testFirstHasSameMissingCountAsSecond() {
        // Both have 2 missing positions
        AllelicProfile seq1 = createAllelicProfile("A??T");
        AllelicProfile seq2 = createAllelicProfile("AC?-");
        
        // Position 0: A vs A -> 0
        // Position 1: ? vs C -> seq1 missing, else if branches don't execute -> 0
        // Position 2: ? vs ? -> both missing -> 0
        // Position 3: T vs - -> seq2 missing (+1), else if skipped
        assertEquals("Should work when seq1 has same number of missing as seq2", 
                     1.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    // Tests with SequenceTypingData

    @Test
    public void testSequenceTypingDataIdentical() {
        SequenceTypingData seq1 = createSequenceTypingData(1L, 2L, 3L, 4L, 5L);
        SequenceTypingData seq2 = createSequenceTypingData(1L, 2L, 3L, 4L, 5L);
        
        assertEquals("Distance between identical typing data should be 0", 
                     0.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testSequenceTypingDataWithDifferencesSeq1Greater() {
        // Testing positions where seq1 > seq2
        SequenceTypingData seq1 = createSequenceTypingData(10L, 20L, 30L, 40L, 50L);
        SequenceTypingData seq2 = createSequenceTypingData(5L, 10L, 15L, 20L, 25L);
        
        // All positions have seq1 > seq2
        assertEquals("Distance should count all positions where seq1 > seq2", 
                     5.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testSequenceTypingDataWithDifferencesSeq1Less() {
        // Testing positions where seq1 < seq2
        SequenceTypingData seq1 = createSequenceTypingData(1L, 2L, 3L, 4L, 5L);
        SequenceTypingData seq2 = createSequenceTypingData(10L, 20L, 30L, 40L, 50L);
        
        // All positions have seq1 < seq2, so distance = 0
        assertEquals("Distance should be 0 when seq1 < seq2 at all positions", 
                     0.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testSequenceTypingDataWithMissingAsMinusOne() {
        // Positions where seq2 has -1 (missing)
        SequenceTypingData seq1 = createSequenceTypingData(1L, 2L, 3L, 4L, 5L);
        SequenceTypingData seq2 = createSequenceTypingData(1L, -1L, 3L, -1L, 5L);
        
        // Position 1: seq2 missing (+1), else if skipped
        // Position 3: seq2 missing (+1), else if skipped
        // Total: 2
        assertEquals("Distance should count missing data penalties only", 
                     2.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testSequenceTypingDataWithMissingAsZero() {
        SequenceTypingData seq1 = createSequenceTypingData(1L, 2L, 3L, 4L, 5L);
        SequenceTypingData seq2 = createSequenceTypingData(1L, 0L, 3L, 0L, 5L);
        
        // Position 1: seq2 missing (+1), else if skipped
        // Position 3: seq2 missing (+1), else if skipped
        // Total: 2
        assertEquals("Distance should count missing data penalties only", 
                     2.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testSequenceTypingDataBothHaveSameMissing() {
        SequenceTypingData seq1 = createSequenceTypingData(1L, -1L, 3L, 0L, 5L);
        SequenceTypingData seq2 = createSequenceTypingData(1L, -1L, 3L, 0L, 5L);
        
        assertEquals("Distance should be 0 when both have same values and missing positions", 
                     0.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    // Edge cases

    @Test
    public void testEmptySequences() {
        AllelicProfile seq1 = createAllelicProfile("");
        AllelicProfile seq2 = createAllelicProfile("");
        
        assertEquals("Distance between empty sequences should be 0", 
                     0.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testSingleElementSequencesIdentical() {
        AllelicProfile seq1 = createAllelicProfile("A");
        AllelicProfile seq2 = createAllelicProfile("A");
        
        assertEquals("Distance between identical single elements should be 0", 
                     0.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testSingleElementSeq1Greater() {
        AllelicProfile seq1 = createAllelicProfile("T");
        AllelicProfile seq2 = createAllelicProfile("A");
        
        // T > A
        assertEquals("Distance should be 1 when single element seq1 > seq2", 
                     1.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testSingleElementSeq1Less() {
        AllelicProfile seq1 = createAllelicProfile("A");
        AllelicProfile seq2 = createAllelicProfile("T");
        
        // A < T
        assertEquals("Distance should be 0 when single element seq1 < seq2", 
                     0.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testSingleElementWithMissing() {
        AllelicProfile seq1 = createAllelicProfile("A");
        AllelicProfile seq2 = createAllelicProfile("?");
        
        // seq2 missing (+1), else if skipped
        assertEquals("Distance should count missing penalty only", 
                     1.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testAllPositionsMissingInSeq2() {
        AllelicProfile seq1 = createAllelicProfile("ACGT");
        AllelicProfile seq2 = createAllelicProfile("????");
        
        // Each position: seq2 missing (+1), else if skipped
        // Total: 4
        assertEquals("Distance should count all missing penalties only", 
                     4.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    // Exception tests

    @Test(expected = IllegalArgumentException.class)
    public void testDifferentLengthSequences() {
        AllelicProfile seq1 = createAllelicProfile("ACG");
        AllelicProfile seq2 = createAllelicProfile("ACGT");
        
        directionalDistance.calculate(seq1, seq2);
    }

    @Test
    public void testDifferentLengthSequencesMessage() {
        AllelicProfile seq1 = createAllelicProfile("ACG");
        AllelicProfile seq2 = createAllelicProfile("ACGT");
        
        try {
            directionalDistance.calculate(seq1, seq2);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("Sequences must be of equal length", e.getMessage());
        }
    }

    // Complex scenarios

    @Test
    public void testComplexScenarioMixedPositions() {
        // Position analysis:
        // 0: A vs A -> 0
        // 1: C vs ? -> seq2 missing (+1), else if skipped
        // 2: G vs G -> 0
        // 3: T vs X -> neither missing, T < X, else if = 0
        // 4: Z vs A -> neither missing, Z > A, else if (+1)
        // 5: C vs - -> seq2 missing (+1), else if skipped
        // 6: G vs Y -> neither missing, G < Y, else if = 0
        // 7: T vs T -> 0
        // Total: 3
        AllelicProfile seq1 = createAllelicProfile("ACGTZCGT");
        AllelicProfile seq2 = createAllelicProfile("A?GXA-YT");
        
        assertEquals("Distance should correctly handle complex mixed scenario", 
                     3.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testAllelicProfileWithAllMissingSymbols() {
        // Test all three missing symbols: ?, -, space
        AllelicProfile seq1 = createAllelicProfile("ABC");
        AllelicProfile seq2 = createAllelicProfile("?- ");
        
        // Position 0: A vs ? -> missing(+1), else if skipped
        // Position 1: B vs - -> missing(+1), else if skipped
        // Position 2: C vs space -> missing(+1), else if skipped
        // Total: 3
        assertEquals("All missing symbols should be treated correctly", 
                     3.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testLongSequenceAllGreater() {
        SequenceTypingData seq1 = createSequenceTypingData(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);
        SequenceTypingData seq2 = createSequenceTypingData(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        
        // All positions have seq1 > seq2
        assertEquals("Distance should count all positions where seq1 > seq2", 
                     10.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }

    @Test
    public void testIntegerComparisonBoundary() {
        // Test comparison with 0 and negative values
        SequenceTypingData seq1 = createSequenceTypingData(1L, 1L, 1L, 1L);
        SequenceTypingData seq2 = createSequenceTypingData(0L, -1L, 1L, 2L);
        
        // Position 0: 1 vs 0 (missing) -> missing(+1), else if skipped
        // Position 1: 1 vs -1 (missing) -> missing(+1), else if skipped
        // Position 2: 1 vs 1 -> 0
        // Position 3: 1 vs 2 -> neither missing, 1 < 2, else if = 0
        // Total: 2
        assertEquals("Should correctly handle missing data markers as integers", 
                     2.0, directionalDistance.calculate(seq1, seq2), 0.0);
    }
}
