package optimalarborescence.distance;

import optimalarborescence.sequences.Sequence;

public interface DistanceFunction {
    /**
     * Calculates the distance between two sequences.
     *
     * @param seq1 the first sequence
     * @param seq2 the second sequence
     * @return the distance between the two sequences
     */
    double calculate(Sequence<?> seq1, Sequence<?> seq2);

    /**
     * Returns a description of the distance function.
     *
     * @return a string description of the distance function
     */
    String getDescription();
} 