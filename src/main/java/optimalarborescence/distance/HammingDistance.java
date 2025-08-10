package optimalarborescence.distance;

public class HammingDistance implements DistanceFunction {

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
    public String getDescription() {
        return "Hamming Distance: measures the number of positions at which the corresponding sequences are different.";
    }
    
}