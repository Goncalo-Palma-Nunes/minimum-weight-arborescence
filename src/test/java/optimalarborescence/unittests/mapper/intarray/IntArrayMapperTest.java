package optimalarborescence.unittests.mapper.intarray;

import java.io.IOException;
import optimalarborescence.memorymapper.IntArrayMapper;

import org.junit.Assert;
import org.junit.Test;

public class IntArrayMapperTest {

    @Test
    public void testSaveAndLoadArray() throws IOException {
        int[] original = {1, 2, 3, 4, 5};
        String fileName = "test.dat";

        IntArrayMapper.saveArrayToMappedFile(original, fileName);
        int[] loaded = IntArrayMapper.loadArrayFromMappedFile(fileName);

        Assert.assertArrayEquals(original, loaded);
    }

    @Test
    public void testResizeArraySmall() {
        int[] original = {1, 2, 3, 4, 5};
        int[] resized = IntArrayMapper.resizeArray(original, 3);

        Assert.assertArrayEquals(new int[]{1, 2, 3}, resized);
    }

    @Test
    public void testResizeArrayLarge() {
        int[] original = {1, 2, 3};
        int[] resized = IntArrayMapper.resizeArray(original, 5);

        Assert.assertArrayEquals(new int[]{1, 2, 3, 0, 0}, resized);
    }

    @Test
    public void testSaveElementToFileAtPosition() throws IOException {
        String fileName = "test.dat";
        IntArrayMapper.saveElementToFileAtPosition(fileName, 42, 0);

        int[] loaded = IntArrayMapper.loadArrayFromMappedFile(fileName);
        Assert.assertEquals(42, loaded[0]);
    }

    @Test
    public void testAppendElementsToFile() throws IOException {
        String fileName = "test.dat";
        int[] original = IntArrayMapper.loadArrayFromMappedFile(fileName);
        int originalLength = original.length;

        int[] newElements = {6, 7, 8};
        int newLength = originalLength + newElements.length;
        IntArrayMapper.appendElementsToFile(fileName, newElements);

        int[] loaded = IntArrayMapper.loadArrayFromMappedFile(fileName);
        Assert.assertEquals(newLength, loaded.length);
        for (int i = 0; i < originalLength; i++) {
            Assert.assertEquals(original[i], loaded[i]);
        }
        for (int i = 0; i < newElements.length; i++) {
            Assert.assertEquals(newElements[i], loaded[originalLength + i]);
        }
    }

    // clean up
}
