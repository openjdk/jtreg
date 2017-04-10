package java.util;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class java_util_TestNGTest {
    @Test
    public void test() {
        System.err.println(getClass().getName());
        Thread.dumpStack();
        Assert.assertTrue(true, "assert true");
    }
}
