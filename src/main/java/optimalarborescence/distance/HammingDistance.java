package optimalarborescence.distance;

import java.io.Serializable;

public class HammingDistance implements DistanceFunction, Serializable {

    private static final long serialVersionUID = 1L; // TODO - UID para serialização. Pesquisar mais sobre isto

    @Override
    public double calculate(String s1, String s2) {
        if (s1.length() != s2.length()) {
            throw new IllegalArgumentException("Strings must be of equal length");
        }
        
        int distance = 0;
        for (int i = 0; i < s1.length(); i++) {
            if (s1.charAt(i) != s2.charAt(i)) {
                distance++;
            }
        }
        return distance;
    }
        
    @Override
    public double calculate(byte[] s1, byte[] s2) {
        if (s1.length != s2.length) {
            throw new IllegalArgumentException("Byte arrays must be of equal length");
        }
        int numBases = (s1.length * 8) / 2; // 2 bits per base
        int distance = 0;
        for (int i = 0; i < numBases; i++) {
            int byteIndex = (i * 2) / 8;
            int bitOffset = (i * 2) % 8;
            int base1 = (s1[byteIndex] >> (6 - bitOffset)) & 0b11;
            int base2 = (s2[byteIndex] >> (6 - bitOffset)) & 0b11;
            if (base1 != base2) {
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