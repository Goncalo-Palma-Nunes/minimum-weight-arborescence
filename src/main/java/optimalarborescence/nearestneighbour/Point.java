package optimalarborescence.nearestneighbour;

public class Point {
    private int id;
    private String sequence; // TODO - retirar sequence na versão final, para poupar espaço
    private byte[] bitArray; // 2 bits per base, packed

    public Point(int id, String sequence) {
        this.id = id;
        this.sequence = sequence;

        if (sequence == null || sequence.isEmpty()) {
            throw new IllegalArgumentException("Sequence cannot be null or empty");
        }
        if (!sequence.matches("[ACGT]*")) {
            throw new IllegalArgumentException("Sequence must contain only A, C, G, T characters");
        }
        if (id < 0) {
            throw new IllegalArgumentException("ID must be a non-negative integer");
        }

        // Convert sequence to array of bits (2 bits per base)
        int len = sequence.length();
        int numBytes = (len * 2 + 7) / 8; // 2 bits per base, 8 bits per byte
        bitArray = new byte[numBytes];
        for (int i = 0; i < len; i++) {
            int bits = encodeBase(sequence.charAt(i));
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

    public int getId() {
        return id;
    }

    public String getSequence() {
        return sequence;
    }

    public byte[] getBitArray() {
        return bitArray;
    }

    public String getBinaryRepresentation() {
        StringBuilder binary = new StringBuilder();
        for (byte b : bitArray) {
            binary.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
        }
        return binary.toString().substring(0, sequence.length() * 2); // Only the relevant bits
    }
}
