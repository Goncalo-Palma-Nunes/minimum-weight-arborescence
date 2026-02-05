package optimalarborescence.sequences;

import java.util.List;


public class AllelicProfile extends Sequence<Character> {

    private static final List<Character> MISSING_DATA_SYMBOLS = List.of('?', '-', ' ');

    public AllelicProfile(Character[] data, int length) {
        super(data, length);
    }

    /**
     * Gets the element at the specified index in the sequence.
     */
    @Override
    public Character getElementAt(int index) {
        if (index < 0 || index >= getLength()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + getLength());
        }
        
        return getData()[index];
    }

    @Override
    public int compareAt(int index, Sequence<?> other) {
        if (!(other instanceof AllelicProfile)) {
            throw new IllegalArgumentException("Cannot compare AllelicProfile with different Sequence type");
        }

        Character thisElement = getElementAt(index);
        Character otherElement = (Character) other.getElementAt(index);
        return thisElement.compareTo(otherElement);
    }

    @Override
    public boolean isMissingDataAt(int index) {
        return MISSING_DATA_SYMBOLS.contains(getElementAt(index));
    }

    @Override
    public String toString() {
        return "AllelicProfile{data=" + getData() + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        AllelicProfile other = (AllelicProfile) obj;
        if (getLength() != other.getLength()) return false;

        for (int i = 0; i < getLength(); i++) {
            if (!getData()[i].equals(other.getData()[i])) {
                return false;
            }
        }
        return true;
    }
}