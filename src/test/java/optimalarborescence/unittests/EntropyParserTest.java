package optimalarborescence.unittests;

import optimalarborescence.EntropyParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EntropyParser.
 */
public class EntropyParserTest {
    
    private static final String ENTROPY_JSON_PATH = System.getProperty("user.home") + "/tese_data/entropy_sorted.json";
    
    @Test
    public void testParseTopEntropiesReturnsCorrectSize() throws IOException {
        int numIndices = 5;
        ArrayList<Integer> result = EntropyParser.parseTopEntropies(ENTROPY_JSON_PATH, numIndices);
        
        assertEquals(numIndices, result.size(), "Should return exactly 5 indices");
    }
    
    @Test
    public void testParseTopEntropiesReturnsIntegers() throws IOException {
        int numIndices = 10;
        ArrayList<Integer> result = EntropyParser.parseTopEntropies(ENTROPY_JSON_PATH, numIndices);
        
        for (Integer index : result) {
            assertNotNull(index, "Indices should not be null");
            assertTrue(index >= 0, "Indices should be non-negative");
        }
    }
    
    @Test
    public void testParseTopEntropiesHighestValue() throws IOException {
        ArrayList<Integer> result = EntropyParser.parseTopEntropies(ENTROPY_JSON_PATH, 1);
        
        assertEquals(1, result.size(), "Should return 1 index");
        assertEquals(1922, result.get(0), "First index should be 1922");
    }
    
    @Test
    public void testParseTopEntropiesRequestMoreThanAvailable() throws IOException {
        int numIndices = 10000; // More than available
        ArrayList<Integer> result = EntropyParser.parseTopEntropies(ENTROPY_JSON_PATH, numIndices);
        
        assertTrue(result.size() <= numIndices, "Should not exceed requested size");
        assertTrue(result.size() > 0, "Should return at least some entries");
    }
    
    @Test
    public void testParseTopEntropiesFirstTenIndices() throws IOException {
        ArrayList<Integer> result = EntropyParser.parseTopEntropies(ENTROPY_JSON_PATH, 10);
        
        assertEquals(10, result.size(), "Should return 10 indices");
        int[] expectedIndices = {1922, 284, 2663, 260, 2329, 1806, 2925, 1057, 507, 446};
        for (int i = 0; i < expectedIndices.length; i++) {
            assertEquals(expectedIndices[i], (int) result.get(i),
                    "Index at position " + i + " should match expected value");
        }
    }
}
