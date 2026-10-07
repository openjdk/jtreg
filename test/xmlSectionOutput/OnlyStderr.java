/*
 * @test
 * @summary Test that system-err in XML is populated from a section with only stderr output.
 *          The section-selection guard must not skip this section just because stdout is empty.
 * @run main OnlyStderr
 */

public class OnlyStderr {
    public static void main(String[] args) {
        System.err.println("STDERR_MARKER_ONLY_STDERR");
    }
}
