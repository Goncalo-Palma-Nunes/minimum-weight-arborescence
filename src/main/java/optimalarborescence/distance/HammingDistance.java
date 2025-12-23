package optimalarborescence.distance;

import optimalarborescence.sequences.Sequence;

import java.io.Serializable;

public class HammingDistance implements DistanceFunction, Serializable {

    private static final long serialVersionUID = 1L; // TODO - UID para serialização. Pesquisar mais sobre isto

    @Override
    public double calculate(Sequence<?> seq1, Sequence<?> seq2) {
        if (seq1.getLength() != seq2.getLength()) {
            throw new IllegalArgumentException("Sequences must be of equal length");
        }
        
        int distance = 0;
        for (int i = 0; i < seq1.getLength(); i++) {
            if (seq1.compareAt(i, seq2) != 0) {
                distance++;
            }
        }
        return distance;
    }

    @Override
    public String getDescription() {
        return "Hamming Distance: measures the number of positions at which the corresponding sequences are different.";
    }
    
}