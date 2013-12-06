/*
 * @test
 * @run testng TestNGTest
 */

import org.testng.annotations.*;

public class TestNGTest {
    private static final String markerText =
        "Lorem ipsum dolor sit amet, consectetur adipisicing elit.";

    @Test
    public void test() {
        System.out.println(markerText);
    }
}
