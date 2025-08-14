package optimalarborescence.nearestneighbour;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Hashtable;
import java.util.Random;

import javax.management.RuntimeErrorException;

import optimalarborescence.exception.NotImplementedException;

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
    private int numBuckets;
    private long bucketSize;
    private List<Hash> hashFunctions = new ArrayList<>();
    private int minHashIndex = 0;
    private int maxHashIndex = 0;
    private List<List<Hash>> concatenatedHashes = new ArrayList<>();
    private List<Hashtable<Integer, List<Point>>> buckets = new ArrayList<>();
    private final static int SEED = 42;

    /****************************************
     *              Constructors            *
     ****************************************/

    /**
     * Constructs an LSH instance with the specified number of hash functions and buckets.
     * 
     * @param numHashFunctions The number of hash functions to use.
     * @param numBuckets The number of buckets to use for storing points.
     */
    public LSH(int widthConcatenatedHashes, int numBuckets, int minHashIndex,
                int maxHashIndex, int bucketSize) {
        this.widthConcatenatedHashes = widthConcatenatedHashes;
        this.numBuckets = numBuckets;
        this.minHashIndex = minHashIndex;
        this.maxHashIndex = maxHashIndex;
        this.bucketSize = bucketSize;

        if (widthConcatenatedHashes <= 0 || numBuckets <= 0 || bucketSize <= 0) {
            throw new IllegalArgumentException("Number of hash functions, buckets, and bucket size must be greater than zero.");
        }

    }

    /****************************************
     *              Methods                 *
     ****************************************/

    private void generateHashes() {

        Random r = new Random();
        r.setSeed(SEED);

        /* Generate numBuckets concatenated hashes and the respective buckets */
        for (int i = 0; i < this.numBuckets; i++) {
            List<Hash> hashes = new ArrayList<>();
            Hashtable<Integer, Boolean> usedIndices = new Hashtable<>();

            /* Each concatenated hash is obtained by concatenating widthConcatenatedHashes
             * hamming hashes uniformly sampled
             */
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

            /* Initialize the bucket for this concatenated hash */
            buildHashTable();
        }
    }

    private void buildHashTable() {
        Hashtable<Integer, List<Point>> bucket = new Hashtable<>();
        for (int j = 0; j < this.bucketSize; j++) {
            bucket.put(j, new LinkedList<>());
        }
        buckets.add(bucket);
    }
    

    @Override
    public List<Point> neighbourSearch(Point point, int numNeighbours) {
        if (point.getBitArray() == null) {
            throw new NotImplementedException("Point does not have a bit array.");
        }

        // Calculate the bucket index for the point
        int bucketIndex = 0;
        for (Hash hashFunction : hashFunctions) {
            bucketIndex = (bucketIndex * 2 + hashFunction.hash(point)) % numBuckets;
        }

        // Retrieve points from the corresponding bucket
        List<Point> candidates = buckets.get(bucketIndex);
        if (candidates == null) {
            return new ArrayList<>(); // No candidates found
        }

        // Return the candidates as the nearest neighbours
        return candidates.subList(0, Math.min(numNeighbours, candidates.size()));
    }   
}