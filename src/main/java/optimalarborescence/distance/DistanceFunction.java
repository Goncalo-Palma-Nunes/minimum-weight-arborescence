package optimalarborescence.distance;

public interface DistanceFunction {
    /**
     * Calculates the distance between two strings.
     *
     * @param s1 the first string
     * @param s2 the second string
     * @return the distance between the two
     */
    double calculate(String s1, String s2);

    /**
     * Returns a description of the distance function.
     *
     * @return a string description of the distance function
     */
    String getDescription();
} 