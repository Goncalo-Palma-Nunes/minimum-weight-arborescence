package optimalarborescence;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * Parser for entropy metrics in JSON format.
 * Reads entropy_sorted.json and extracts the highest entropy positions.
 */
public class EntropyParser {
    
    /**
     * Represents an entropy entry with position and entropy value.
     */
    public static class EntropyEntry {
        public int index;
        public double entropy;
        
        public EntropyEntry(int index, double entropy) {
            this.index = index;
            this.entropy = entropy;
        }
        
        @Override
        public String toString() {
            return String.format("Index: %d, Entropy: %.4f", index, entropy);
        }
    }
    
    /**
     * Parses the entropy_sorted.json file and returns the top numIndices entries' indices.
     * 
     * @param jsonFilePath Path to the entropy_sorted.json file
     * @param numIndices Number of top entropy indices to return
     * @return ArrayList of indices sorted by descending entropy
     * @throws IOException If the file cannot be read
     */
    public static ArrayList<Integer> parseTopEntropies(String jsonFilePath, int numIndices) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(jsonFilePath)));
        JSONObject json = new JSONObject(content);
        JSONArray entries = json.getJSONArray("entropy_data");
        
        ArrayList<Integer> result = new ArrayList<>();
        int count = Math.min(numIndices, entries.length());
        
        for (int i = 0; i < count; i++) {
            JSONObject entry = entries.getJSONObject(i);
            int index = entry.getInt("index");
            result.add(index);
        }
        
        return result;
    }
    
    /**
     * Main method for testing the parser.
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java EntropyParser <json_file_path> <num_indices>");
            System.exit(1);
        }
        
        String jsonFilePath = args[0];
        int numIndices = Integer.parseInt(args[1]);
        
        try {
            ArrayList<Integer> topIndices = parseTopEntropies(jsonFilePath, numIndices);
            System.out.println("Top " + numIndices + " highest entropy positions:");
            for (Integer index : topIndices) {
                System.out.println("  " + index);
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
