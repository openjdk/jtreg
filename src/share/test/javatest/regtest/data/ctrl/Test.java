/*
 * @test
 */

public class Test {
    public static void main(String... args) {
        for (int i = 0; i < 40; i++) {
            if (i % 8 == 0) System.out.print(String.format("%3o: ", i));
            System.out.print((char) i);
            if (i % 8 == 7) System.out.println();
        }
        System.out.println("\u00E2");
        System.out.println("\\u0000\\u0001\\u0002");
    }
}
