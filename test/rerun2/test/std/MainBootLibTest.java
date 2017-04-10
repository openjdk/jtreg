/*
 * @test
 * @library /lib
 * @build java.lang.MarkerText
 * @run main/bootclasspath MainBootLibTest
 */

public class MainBootLibTest {
    public static void main(String... args) {
        System.out.println(java.lang.MarkerText.get());
    }
}
