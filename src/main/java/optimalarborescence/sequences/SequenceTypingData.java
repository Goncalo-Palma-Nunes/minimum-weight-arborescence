package optimalarborescence.sequences;

import java.util.List;
import java.util.Locale;

/**
 * SequenceTypingData represents a sequence of typing data, typically used in bioinformatics.
 * It extends the generic Sequence class with a long array as its data type.
 */
public class SequenceTypingData extends Sequence<Long> {

    private static final List<Long> MISSING_DATA_VALUES = List.of(-1L, 0L);
    private static final List<String> MISSING_DATA_STRING_SYMBOLS = List.of("?", "N");

    public SequenceTypingData(Long[] data, int length) {
        super(data, length);
    }

    public SequenceTypingData(Object[] data, int length) {
        super(normalizeTypingData(data, length), length);
    }

    public static Long normalizeAlleleValue(Object rawValue) {
        if (rawValue == null) {
            return -1L;
        }

        if (rawValue instanceof Number) {
            return ((Number) rawValue).longValue();
        }

        String token = rawValue.toString().trim();
        if (token.isEmpty()) {
            return -1L;
        }

        if (MISSING_DATA_STRING_SYMBOLS.contains(token.toUpperCase(Locale.ROOT))) {
            return -1L;
        }

        try {
            return Long.parseLong(token);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    public static boolean isMissingDataSymbol(Object rawValue) {
        if (rawValue == null) {
            return true;
        }

        if (rawValue instanceof Number) {
            return MISSING_DATA_VALUES.contains(((Number) rawValue).longValue());
        }

        String token = rawValue.toString().trim();
        if (token.isEmpty()) {
            return true;
        }

        if (MISSING_DATA_STRING_SYMBOLS.contains(token.toUpperCase(Locale.ROOT))) {
            return true;
        }

        try {
            return MISSING_DATA_VALUES.contains(Long.parseLong(token));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Long[] normalizeTypingData(Object[] rawData, int length) {
        Long[] normalizedData = new Long[length];
        for (int i = 0; i < length; i++) {
            normalizedData[i] = normalizeAlleleValue(rawData[i]);
        }
        return normalizedData;
    }

    /**
     * Gets the element at the specified index in the sequence.
     */
    @Override
    public Long getElementAt(int index) {
        if (index < 0 || index >= getLength()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + getLength());
        }
        
        return getData()[index];
    }

    @Override
    public int compareAt(int index, Sequence<?> other) {
        if (!(other instanceof SequenceTypingData)) {
            throw new IllegalArgumentException("Cannot compare SequenceTypingData with different Sequence type");
        }

        Long thisElement = getElementAt(index);
        Long otherElement = (Long) other.getElementAt(index);
        return thisElement.compareTo(otherElement);
    }

    @Override
    public boolean isMissingDataAt(int index) {
        return isMissingDataSymbol(getElementAt(index));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SequenceTypingData{data=[");;
        Long[] data = getData();
        for (int i = 0; i < data.length; i++) {
            sb.append(data[i]);
            if (i < data.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        SequenceTypingData other = (SequenceTypingData) obj;
        if (getLength() != other.getLength()) return false;

        Long[] data1 = getData();
        Long[] data2 = other.getData();
        for (int i = 0; i < getLength(); i++) {
            if (!data1[i].equals(data2[i])) {
                return false;
            }
        }
        return true;
    }
}