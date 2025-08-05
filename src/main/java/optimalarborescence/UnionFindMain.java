package optimalarborescence;

import java.io.*;
import optimalarborescence.datastructure.UnionFind;

public class UnionFindMain {
 
    public static void main(String[] args) {
        String inputFileName = "UnionFindInput.txt";

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFileName))) {
            // Read the number of lines
            int numLines = Integer.parseInt(reader.readLine().trim());

            // Create an instance of UnionFind
            UnionFind unionFind = new UnionFind(numLines);

            // Process each line of the input file
            String line;
            int iteration = 1;
            while ((line = reader.readLine()) != null) {
                System.out.println("###### Iteration nº" + 1 + " #######");
                String[] parts = line.trim().split(" ");
                int a = Integer.parseInt(parts[0]);
                int b = Integer.parseInt(parts[1]);
                System.out.println("left = "+ a + "; right = " + b);

                // Perform union if a and b are not in the same set
                if (unionFind.find(a) != unionFind.find(b)) {
                    unionFind.union(a, b);
                    System.out.println("Union performed: " + a + " " + b);
                }
                else {
                    System.out.println("They were already connected");
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading the input file: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("Invalid number format in the input file: " + e.getMessage());
        }
    }
}
