package optimalarborescence.nearestneighbour;

import optimalarborescence.sequences.Sequence;
import optimalarborescence.graph.Node;

public class Point<T> {
    private int id;
    private Sequence<T> sequence;
    private Node node; // Associated node

    public Point(int id, Sequence<T> sequence) {
        this.id = id;
        this.sequence = sequence;
        this.node = null;

        if (sequence == null || sequence.getLength() == 0) {
            throw new IllegalArgumentException("Sequence cannot be null or empty");
        }
        if (id < 0) {
            throw new IllegalArgumentException("ID must be a non-negative integer");
        }
    }

    public int getId() {
        return id;
    }

    public Sequence<T> getSequence() {
        return sequence;
    }

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Point<?>)) return false;
        Point<?> other = (Point<?>) obj;
        System.out.println("Comparing Point " + id + " with Point " + other.id);
        System.out.println("This sequence: " + sequence);
        System.out.println("Other sequence: " + other.sequence);

        return id == other.id && sequence.equals(other.sequence);
    }

    @Override
    public String toString() {
        return "Point{" +
                "id=" + id +
                ", sequence='" + sequence + '\'' +
                // ", bitArray=" + (bitArray != null ? getBinaryRepresentation() : "null") +
                '}';
    }

    /*********************************************************************************
     * 
     * 
     *    TODO - Apagar tudo o que está abaixo e só usar a versão com Sequence<?>
     * 
     *********************************************************************************/

    private String sequenceString; // TODO - retirar sequenceString na versão final, para poupar espaço
    private byte[] bitArray; // 2 bits per base, packed

    public Point(int id, String sequenceString) {
        this.id = id;
        this.sequenceString = sequenceString;

        if (sequenceString == null || sequenceString.isEmpty()) {
            throw new IllegalArgumentException("sequenceString cannot be null or empty");
        }
        if (!sequenceString.matches("[ACGT]*")) {
            throw new IllegalArgumentException("sequenceString must contain only A, C, G, T characters");
        }
        if (id < 0) {
            throw new IllegalArgumentException("ID must be a non-negative integer");
        }

        // Convert sequenceString to array of bits (2 bits per base)
        int len = sequenceString.length();
        int numBytes = (len * 2 + 7) / 8; // 2 bits per base, 8 bits per byte
        bitArray = new byte[numBytes];
        for (int i = 0; i < len; i++) {
            int bits = encodeBase(sequenceString.charAt(i));
            int bitIndex = i * 2;
            int byteIndex = bitIndex / 8;
            int bitOffset = bitIndex % 8;
            bitArray[byteIndex] |= (bits << (6 - bitOffset));
        }
    }

    private int encodeBase(char c) {
        switch (c) {
            case 'A': return 0b00;
            case 'C': return 0b01;
            case 'G': return 0b10;
            case 'T': return 0b11;
            default: throw new IllegalArgumentException("Invalid base: " + c);
        }
    }

    public String getsequenceString() {
        return sequenceString;
    }

    public byte[] getBitArray() {
        return bitArray;
    }

    public String getBinaryRepresentation() {
        StringBuilder binary = new StringBuilder();
        for (byte b : bitArray) {
            binary.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
        }
        return binary.toString().substring(0, sequenceString.length() * 2); // Only the relevant bits
    }
}
