/*
 * @test
 * @library /lib
 * @build java.lang.MarkerText
 * @run testng/bootclasspath TestNGBootLibTest
 */

import org.testng.annotations.*;

public class TestNGBootLibTest {
    @Test
    public void test() {
        System.out.println(java.lang.MarkerText.get());
    }
}
