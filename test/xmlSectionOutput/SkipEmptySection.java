/*
 * @test
 * @summary Test that XMLWriter skips sections where both stdout and stderr are empty
 *          and picks the first section that has non-empty output.
 *          The compile action produces no stdout/stderr; only the main run does.
 *          Before the fix, getOutput() would return "" from the compile section.
 * @compile SkipEmptySection.java
 * @run main SkipEmptySection
 */

public class SkipEmptySection {
    public static void main(String[] args) {
        System.out.println("STDOUT_MARKER_SKIP_EMPTY");
        System.err.println("STDERR_MARKER_SKIP_EMPTY");
    }
}
