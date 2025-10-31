package optimalarborescence.nearestneighbour;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Random;
import java.util.stream.Collectors;

import java.util.Set;
import java.util.TreeSet;


import org.apache.commons.math3.util.CombinatoricsUtils;

import optimalarborescence.distance.DistanceFunction;
import optimalarborescence.exception.NotImplementedException;

/* TODO (importante) - Acho que há um bug com o parâmetero widthConcatenatedHashes
   Com base no LSHTest.java, parece que se widthConcatenatedHashes for > que a hamming distance
   entre as sequências, ele nunca os encontra como vizinhos, mesmo que estejam dentro de um
   radius correto.

   Pode ser só uma questão de probabilidades devido ao número de tabelas/hashes, mas não me parece
*/


// TODO - acho que isto também tem de ser serializável

// TODO - as tabelas estão a usar array lists para os baldes. Talvez se poupe memória com linkedlists. Presumo que o ArrayList duplique a memória para amortizar as bounds

// TODO - rever como parameterizar a 'capacity' e 'load factor' da HashTable da biblioteca java.util
//        Se não definir isso, as tabelas podem ter de fazer resize + rehash muitas vezes

/* TODO - perceber se a Hashtable com a List<Hash> como key funciona bem / em O(1) ou se convém ver
 * - randomized-algorithms-motwani-and-raghavan
 * - Michael L. Fredman, János Komlós, and Endre Szemerédi. 1984. Storing a Sparse Table with 0(1) Worst Case Access Time. J. ACM 31, 3 (July 1984), 538–544. https://doi.org/10.1145/828.1884
 * - CLRS intro to algorithms
 */

public class LSH extends NearestNeighbourSearchAlgorithm {

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

    public class Hash implements Comparable<Hash> {

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
            if (point.getBitArray() == null) {
                throw new NotImplementedException("Hash function not implemented for this point.");
            }
            // Extract the bit at the specified index
            return (point.getBitArray()[index / 8] >> (index % 8)) & 1;

            // TODO - basta devolver 1 bit? As bases são representadas por 2 bits
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
            if (!(obj instanceof Hash)) return false;
            Hash other = (Hash) obj;
            return index == other.index;
        }

        @Override
        public int compareTo(Hash other) {
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
    public List<Set<Hash>> concatenatedHashes = new ArrayList<>();
    private List<Hashtable<List<Integer>, List<Point>>> tables = new ArrayList<>(); // TODO - rename to tables
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
            Set<Hash> hashes = new TreeSet<>(); // Used to prevent repeated indices within a concatenated hash
            Set<Integer> indices = new TreeSet<>();

            /* Each concatenated hash is obtained by concatenating widthConcatenatedHashes
             * hamming hashes uniformly sampled */
            while (hashes.size() < this.widthConcatenatedHashes) {
                int index = r.nextInt(maxHashIndex - minHashIndex + 1) + minHashIndex;
                boolean added = hashes.add(new Hash(index)); // TreeSet so it won't add if it is already there
                if (added) { indices.add(index); }
            }

            if (!uniqueHashes.contains(indices)) {
                // System.out.println("Created concatenated hash " + (tablesCreated + 1) + ": " + hashes);
                // System.out.println("Indices used: " + indices);
                // System.out.println("");
                uniqueHashes.add(indices);
                concatenatedHashes.add(hashes);
                tables.add(new Hashtable<>());
                tablesCreated++;
            }
        }
    }

    private List<Integer> computeHash(Set<Hash> concatenatedHash, Point p) {
        return concatenatedHash.stream()
                .map(h -> h.hash(p))
                .collect(Collectors.toList());
    }

    @Override
    public void storePoint(Point p) { // Guarda-se o ponto em todas as tabelas?
        if (p.getBitArray() == null) {
            throw new IllegalArgumentException("Point does not have a bit array.");
        }

        for (int i = 0; i < this.numTables; i++) {
            Set<Hash> concatenatedHash = concatenatedHashes.get(i);
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
            Set<Hash> concatenatedHash = concatenatedHashes.get(i);
            List<Integer> bucketIndices = computeHash(concatenatedHash, point);

            if (tables.get(i).containsKey(bucketIndices)) {
                List<Point> pointsInBucket = tables.get(i).get(bucketIndices);
                
                if (pointsInBucket != null && !pointsInBucket.isEmpty()) {
                    result.addAll(pointsInBucket.stream()
                            .filter(p -> p != point && getDistanceFunction().calculate(point.getBitArray(), p.getBitArray()) <= radius)
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
        for (Hashtable<List<Integer>, List<Point>> table : tables) {
            tableString.append("Table ").append(tableIndex++).append(" = ").append(table.toString()).append("\n\n");
            tableIndex = tableIndex + 1;
        }

        String TablesInfo = "TablesInfo {\n" + tableString.toString() + "\n}";

        return LSHMetaData + "\n" + TablesInfo;
    }
}