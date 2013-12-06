/*
 * @test
 * @library /lib
 * @build MarkerText
 * @run testng TestNGLibTest
 */

import org.testng.annotations.*;

public class TestNGLibTest {
    @Test
    public void test() {
        System.out.println(MarkerText.get());
    }
}
