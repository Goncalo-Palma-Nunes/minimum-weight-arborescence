package optimalarborescence.nearestneighbour;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Hashtable;
import java.util.Random;
import java.util.stream.Collectors;

import javax.management.RuntimeErrorException;

import optimalarborescence.distance.DistanceFunction;
import optimalarborescence.distance.HammingDistance;
import optimalarborescence.exception.NotImplementedException;

// TODO - acho que isto também tem de ser serializável

// TODO - rever como parameterizar a 'capacity' e 'load factor' da HashTable da biblioteca java.util
//        Se não definir isso, as tabelas podem ter de fazer resize + rehash muitas vezes

/* TODO - perceber se a Hashtable com a List<Hash> como key funciona bem / em O(1) ou se convém ver
 * - randomized-algorithms-motwani-and-raghavan
 * - Michael L. Fredman, János Komlós, and Endre Szemerédi. 1984. Storing a Sparse Table with 0(1) Worst Case Access Time. J. ACM 31, 3 (July 1984), 538–544. https://doi.org/10.1145/828.1884
 * - CLRS intro to algorithms
 */

public class LSH implements NearestNeighbourSearchAlgorithm {

    /*
     * References:
     * Jafari, O., Maurya, P., Nagarkar, P., Islam, K.M., & Crushev, C. (2021).
     * A Survey on Locality Sensitive Hashing Algorithms and their Applications. ArXiv, abs/2102.08942.
     * 
     * Piotr Indyk and Rajeev Motwani. 1998. Approximate nearest neighbors: towards removing the curse of dimensionality. 
     * In Proceedings of the thirtieth annual ACM symposium on Theory of computing
     * 
     * Wikipedia contributors. "Locality-sensitive hashing." Wikipedia, The Free Encyclopedia. Wikipedia, The Free Encyclopedia, 9 Aug. 2025. Web. 11 Aug. 2025. 
     */

    private class Hash {

        private int index;

        /**
         * Constructs a hash function that returns a single bit from the point's bit array.
         * The index specifies which bit to return.
         * 
         * @param index The index of the bit to return.
         */
        public Hash(int index) {
            this.index = index;
        }

        /**
         * Hashes a point by returning the bit at the specified index.
         * 
         * @param point The point to hash.
         * @return The bit at the specified index.
         */
        public int hash(Point point) {
            if (point.getBitArray() == null || point.getBitArray().length * 8 <= index) {
                throw new NotImplementedException("Hash function not implemented for this point.");
            }
            // Extract the bit at the specified index
            return (point.getBitArray()[index / 8] >> (index % 8)) & 1;

            // TODO - basta devolver 1 bit? As bases são representadas por 2 bits
        }
    }

    /****************************************
     *              Attributes              *
     ****************************************/


    private int widthConcatenatedHashes;
    private int numTables;
    private long bucketSize;
    private int minHashIndex = 0;
    private int maxHashIndex = 0;
    private List<List<Hash>> concatenatedHashes = new ArrayList<>();
    private List<Hashtable<List<Integer>, List<Point>>> tables = new ArrayList<>(); // TODO - rename to tables
    private DistanceFunction distanceFunction;
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
                int maxHashIndex, int bucketSize, DistanceFunction distanceFunction,
                float radius) {
        this.widthConcatenatedHashes = widthConcatenatedHashes;
        this.numTables = numTables;
        this.minHashIndex = minHashIndex;
        this.maxHashIndex = maxHashIndex;
        this.bucketSize = bucketSize;
        this.radius = radius;
        this.distanceFunction = distanceFunction;

        if (widthConcatenatedHashes <= 0 || numTables <= 0 || bucketSize <= 0 
            || radius <= 0) {
            throw new IllegalArgumentException("Number of hash functions, buckets, and bucket size must be greater than zero.");
        }

        generateHashes();
    }

    /****************************************
     *              Methods                 *
     ****************************************/

    private void generateHashes() {

        Random r = new Random();
        r.setSeed(SEED);

        /* Generate numTables concatenated hashes and the respective buckets */
        for (int i = 0; i < this.numTables; i++) {
            List<Hash> hashes = new ArrayList<>();
            Hashtable<Integer, Boolean> usedIndices = new Hashtable<>();

            /* Each concatenated hash is obtained by concatenating widthConcatenatedHashes
             * hamming hashes uniformly sampled */
            for (int j = 0; j < this.widthConcatenatedHashes; j++) {
                int index = r.nextInt(maxHashIndex - minHashIndex + 1) + minHashIndex;
                while (usedIndices.containsKey(index)) {
                    /* Prevent repeated hashes per concatenation */
                    index = r.nextInt(maxHashIndex - minHashIndex + 1) + minHashIndex;
                }
                usedIndices.put(index, true);
                hashes.add(new Hash(index));
            }
            concatenatedHashes.add(hashes);

            /* Initialize the Hash table for this concatenated hash */
            Hashtable<List<Integer>, List<Point>> table = new Hashtable<>();
            tables.add(table);
        }
    }

    private List<Integer> computeHash(List<Hash> concatenatedHash, Point p) {
        return concatenatedHash.stream()
                .map(h -> h.hash(p))
                .collect(Collectors.toList());
    }

    private void storePoint(Point p) { // Guarda-se o ponto em todas as tabelas?
        if (p.getBitArray() == null) {
            throw new IllegalArgumentException("Point does not have a bit array.");
        }

        for (int i = 0; i < this.numTables; i++) {
            List<Hash> concatenatedHash = concatenatedHashes.get(i);
            List<Integer> bucketIndices = computeHash(concatenatedHash, p);

            tables.get(i).putIfAbsent(bucketIndices, new ArrayList<>());
            tables.get(i).get(bucketIndices).add(p);
        }
    }

    @Override
    public List<Point> neighbourSearch(Point point, int numNeighbours) {
        if (point.getBitArray() == null) {
            throw new IllegalArgumentException("Point does not have a bit array.");
        }
        if (numNeighbours <= 0) {
            throw new IllegalArgumentException("Number of neighbours must be greater than zero.");
        }

        List<Point> result = new ArrayList<>();
        int i = 0;
        while ((i < numTables) && (result.size() < numNeighbours)) {
            List<Hash> concatenatedHash = concatenatedHashes.get(i);
            List<Integer> bucketIndices = computeHash(concatenatedHash, point);

            if (tables.get(i).containsKey(bucketIndices)) {
                List<Point> pointsInBucket = tables.get(i).get(bucketIndices);
                if (pointsInBucket != null && !pointsInBucket.isEmpty()) {
                    result.addAll(pointsInBucket.stream()
                            .filter(p -> distanceFunction.calculate(point.getBitArray(), p.getBitArray()) <= radius)
                            .limit(numNeighbours)
                            .collect(Collectors.toList()));
                }
            }
        }
        return result;
    }
}