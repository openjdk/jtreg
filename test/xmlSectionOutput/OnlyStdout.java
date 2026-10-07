/*
 * @test
 * @summary Test that system-out in XML is populated from a section with stdout output.
 *          system-err should be empty when only stdout is written.
 * @run main OnlyStdout
 */

public class OnlyStdout {
    public static void main(String[] args) {
        System.out.println("STDOUT_MARKER_ONLY_STDOUT");
    }
}
