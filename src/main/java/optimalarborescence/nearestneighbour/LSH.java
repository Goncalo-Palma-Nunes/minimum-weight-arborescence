package optimalarborescence.nearestneighbour;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Random;
import java.util.stream.Collectors;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.Set;
import java.util.TreeSet;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.JavaSerializer;

import org.apache.commons.math3.util.CombinatoricsUtils;

import optimalarborescence.distance.DistanceFunction;
import optimalarborescence.distance.HammingDistance;
import optimalarborescence.sequences.Sequence;
import optimalarborescence.sequences.SequenceTypingData;
import optimalarborescence.sequences.AllelicProfile;
import optimalarborescence.graph.Node;
import optimalarborescence.exception.NotImplementedException;

public class LSH<T> extends NearestNeighbourSearchAlgorithm<T> {

    /*
     * References:
     * Jafari, O., Maurya, P., Nagarkar, P., Islam, K.M., & Crushev, C. (2021).
     * A Survey on Locality Sensitive Hashing Algorithms and their Applications. ArXiv, abs/2102.08942.
     * 
     * Piotr Indyk and Rajeev Motwani. 1998. Approximate nearest neighbors: towards removing the curse of dimensionality. 
     * In Proceedings of the thirtieth annual ACM symposium on Theory of computing
     * 
     */

    public static class Hash<T> implements Comparable<Hash<T>> {

        private int index;

        /**
         * Constructs a hash function that returns a element from the point's sequence.
         * The index specifies which element to return.
         * 
         * @param index The index of the element to return.
         */
        public Hash(int index) {
            this.index = index;
        }

        /**
         * Hashes a point by returning the element at the specified index.
         * 
         * @param point The point to hash.
         * @return The element at the specified index.
         */
        public T hash(Point<T> point) {
            if (point.getSequence() == null) {
                throw new NotImplementedException("Hash function not implemented for this point.");
            }


            return point.getSequence().getElementAt(index);
        }

        @Override
        public String toString() {
            return "Hash{" +
                    "index=" + index +
                    '}';
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Hash<?> other = (Hash<?>) obj;
            return index == other.index;
        }

        @Override
        public int compareTo(Hash<T> other) {
            return Integer.compare(this.index, other.index);
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(index);
        }
    }

    /****************************************
     *              Attributes              *
     ****************************************/


    private int widthConcatenatedHashes;
    private int numTables;
    private int minHashIndex = 0;
    private int maxHashIndex = 0;
    public List<Set<Hash<T>>> concatenatedHashes = new ArrayList<>();
    private List<Hashtable<List<T>, List<Point<T>>>> tables = new ArrayList<>();
    // private DistanceFunction distanceFunction;
    private float radius;

    private final static int SEED = 42;

    /****************************************
     *              Constructors            *
     ****************************************/

    /**
     * Constructs an LSH instance with the specified number of hash functions and buckets.
     * 
     * @param numHashFunctions The number of hash functions to use.
     * @param numTables The number of buckets to use for storing points.
     */
    public LSH(int widthConcatenatedHashes, int numTables, int minHashIndex,
                int maxHashIndex, DistanceFunction distanceFunction,
                float radius) {
        super(distanceFunction);
        this.widthConcatenatedHashes = widthConcatenatedHashes;
        this.numTables = numTables;
        this.minHashIndex = minHashIndex;
        this.maxHashIndex = maxHashIndex;
        this.radius = radius;

        validLSH();
        generateHashes();
    }

    /**
     * Constructs an LSH instance with the specified number of hash functions and buckets, using the provided highest entropy indices for hash generation.
     * @param widthConcatenatedHashes 
     * @param numTables 
     * @param minHashIndex 
     * @param maxHashIndex 
     * @param distanceFunction
     * @param radius
     * @param highestEntropyIndices
     */
    public LSH(int widthConcatenatedHashes, int numTables, int minHashIndex,
                int maxHashIndex, DistanceFunction distanceFunction,
                float radius, List<Integer> highestEntropyIndices) {
        super(distanceFunction);
        this.widthConcatenatedHashes = widthConcatenatedHashes;
        this.numTables = numTables;
        this.minHashIndex = minHashIndex;
        this.maxHashIndex = maxHashIndex;
        this.radius = radius;

        validLSH();
        generateHashes(highestEntropyIndices);
    }

    private void validLSH() {
        if (widthConcatenatedHashes <= 0 || numTables <= 0 || radius <= 0) {
            throw new IllegalArgumentException("Number of hash functions, buckets, and radius must be greater than zero.");
        }

        int sequenceSize = maxHashIndex - minHashIndex + 1;
        if (sequenceSize <= 0 || minHashIndex >= maxHashIndex) {
            throw new IllegalArgumentException("Invalid hash index range.");
        }

        int maxNumTables = (int) (CombinatoricsUtils.factorial(sequenceSize) / 
                            (CombinatoricsUtils.factorial(widthConcatenatedHashes) * CombinatoricsUtils.factorial(sequenceSize - widthConcatenatedHashes))
                            );
        if (numTables > Integer.MAX_VALUE || numTables > maxNumTables) {
            throw new IllegalArgumentException("Too many hash tables.");
        }
    }

    /****************************************
     *              Methods                 *
     ****************************************/

    private void generateHashes() {

        Random r = new Random();
        r.setSeed(SEED);

        /* Generate numTables concatenated hashes and the respective buckets */
        Set<Set<Integer>> uniqueHashes = new HashSet<>(); // Used to prevent repeated concatenated hashes
        int tablesCreated = 0;
        while (tablesCreated < this.numTables) {
            Set<Hash<T>> hashes = new TreeSet<>(); // Used to prevent repeated indices within a concatenated hash
            Set<Integer> indices = new TreeSet<>();

            /* Each concatenated hash is obtained by concatenating widthConcatenatedHashes
             * hamming hashes uniformly sampled */
            while (hashes.size() < this.widthConcatenatedHashes) {
                int index = r.nextInt(maxHashIndex - minHashIndex + 1) + minHashIndex;
                boolean added = hashes.add(new Hash<T>(index)); // TreeSet so it won't add if it is already there
                if (added) { indices.add(index); }
            }

            if (!uniqueHashes.contains(indices)) {
                uniqueHashes.add(indices);
                concatenatedHashes.add(hashes);
                tables.add(new Hashtable<>());
                tablesCreated++;
            }
        }
    }

    private void generateHashes(List<Integer> highestEntropyIndices) {
        Random r = new Random();
        r.setSeed(SEED);

        /* Generate numTables concatenated hashes and the respective buckets */
        Set<Set<Integer>> uniqueHashes = new HashSet<>(); // Used to prevent repeated concatenated hashes
        int tablesCreated = 0;
        while (tablesCreated < this.numTables) {
            Set<Hash<T>> hashes = new TreeSet<>(); // Used to prevent repeated indices within a concatenated hash
            Set<Integer> indices = new TreeSet<>();

            /* Each concatenated hash is obtained by concatenating widthConcatenatedHashes
             * hamming hashes uniformly sampled */
            while (hashes.size() < this.widthConcatenatedHashes) {
                // sample a random index from the highest entropy indices
                int index = highestEntropyIndices.get(r.nextInt(highestEntropyIndices.size()));
                boolean added = hashes.add(new Hash<T>(index)); // TreeSet so it won't add if it is already there
                if (added) { indices.add(index); }
            }

            if (!uniqueHashes.contains(indices)) {
                uniqueHashes.add(indices);
                concatenatedHashes.add(hashes);
                tables.add(new Hashtable<>());
                tablesCreated++;
            }
        }

    }

    private List<T> computeHash(Set<Hash<T>> concatenatedHash, Point<T> p) {
        return concatenatedHash.stream()
                .map(h -> h.hash(p))
                .collect(Collectors.toList());
    }

    @Override
    public void storePoint(Point<T> p) {
        if (p.getSequence() == null) {
            throw new IllegalArgumentException("Point does not have a sequence.");
        }

        for (int i = 0; i < this.numTables; i++) {
            Set<Hash<T>> concatenatedHash = concatenatedHashes.get(i);
            List<T> bucketIndices = computeHash(concatenatedHash, p);

            tables.get(i).putIfAbsent(bucketIndices, new ArrayList<>());
            tables.get(i).get(bucketIndices).add(p);
        }
    }

    @Override
    public List<Point<T>> neighbourSearch(Point<T> point, int numNeighbours) {
        if (point.getSequence() == null) {
            throw new IllegalArgumentException("Point does not have a sequence.");
        }
        if (numNeighbours <= 0) {
            throw new IllegalArgumentException("Number of neighbours must be greater than zero.");
        }

        List<Point<T>> result = new ArrayList<>();
        int i = 0;
        while ((i < numTables) && (result.size() < numNeighbours)) {
            Set<Hash<T>> concatenatedHash = concatenatedHashes.get(i);
            List<T> bucketIndices = computeHash(concatenatedHash, point);

            if (tables.get(i).containsKey(bucketIndices)) {
                List<Point<T>> pointsInBucket = tables.get(i).get(bucketIndices);
                
                if (pointsInBucket != null && !pointsInBucket.isEmpty()) {
                    result.addAll(pointsInBucket.stream()
                            .filter(p -> p != point && getDistanceFunction().calculate(point.getSequence(), p.getSequence()) <= radius)
                            .filter(p -> !result.contains(p))
                            .limit(numNeighbours - result.size())
                            .collect(Collectors.toList()));
                }
            }

            i = i + 1;
        }

        return result;
    }


    /****************************************
     *              Serialization           *
     ****************************************/

    /**
     * Configures and returns a Kryo instance with all necessary classes registered.
     * This ensures consistent serialization/deserialization.
     * 
     * @return Configured Kryo instance
     */
    private static Kryo createKryoInstance() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false); // Allow unregistered classes for flexibility
        kryo.setReferences(true); // Enable circular reference handling
        
        // Register LSH-specific classes
        kryo.register(LSH.class);
        kryo.register(Hash.class);
        
        // Register collection types
        kryo.register(ArrayList.class);
        kryo.register(HashSet.class);
        kryo.register(TreeSet.class);
        kryo.register(Hashtable.class);
        
        // Register Point and related classes
        kryo.register(Point.class);
        kryo.register(Node.class);
        
        // Register Sequence types
        kryo.register(Sequence.class);
        kryo.register(SequenceTypingData.class);
        kryo.register(AllelicProfile.class);
        kryo.register(Integer[].class);
        kryo.register(Character[].class);
        kryo.register(byte[].class);
        
        // Register DistanceFunction implementations
        kryo.register(HammingDistance.class);
        
        return kryo;
    }

    /**
     * Saves an LSH instance to a file using Kryo serialization.
     * 
     * @param lsh The LSH instance to save
     * @param filePath The path where the LSH should be saved
     * @throws IOException If an I/O error occurs during saving
     */
    public static <T> void saveLSH(LSH<T> lsh, String filePath) throws IOException {
        Kryo kryo = createKryoInstance();
        
        try (Output output = new Output(new FileOutputStream(filePath))) {
            kryo.writeObject(output, lsh);
        }
    }

    /**
     * Loads an LSH instance from a file using Kryo deserialization.
     * 
     * @param filePath The path to the saved LSH file
     * @return The deserialized LSH instance
     * @throws IOException If an I/O error occurs during loading
     */
    @SuppressWarnings("unchecked")
    public static <T> LSH<T> loadLSH(String filePath) throws IOException {
        Kryo kryo = createKryoInstance();
        
        try (Input input = new Input(new FileInputStream(filePath))) {
            return (LSH<T>) kryo.readObject(input, LSH.class);
        }
    }

    /****************************************
     *              Debug                   *
     ****************************************/


    @Override
    public String toString() {

        String LSHMetaData = "LSH{" +
                "numTables=" + numTables +
                ", widthConcatenatedHashes=" + widthConcatenatedHashes +
                ", minHashIndex=" + minHashIndex +
                ", maxHashIndex=" + maxHashIndex +
                ", radius=" + radius +
                '}';
        

        StringBuilder tableString = new StringBuilder();
        int tableIndex = 1;
        for (Hashtable<List<T>, List<Point<T>>> table : tables) {
            tableString.append("Table ").append(tableIndex++).append(" = ").append(table.toString()).append("\n\n");
            tableIndex = tableIndex + 1;
        }

        String TablesInfo = "TablesInfo {\n" + tableString.toString() + "\n}";

        return LSHMetaData + "\n" + TablesInfo;
    }
}