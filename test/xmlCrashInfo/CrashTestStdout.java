/*
 * @test
 * @summary Test that simulates a JVM crash with output to stdout in a second run
 * @run main/othervm CrashTestStdout pass
 * @run main/othervm CrashTestStdout crash
 */

public class CrashTestStdout {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("pass")) {
            // First run passes
            System.out.println("First run completed successfully");
        } else {
            // Simulate crash output to stdout in the second run
            System.out.println("Running test...");
            System.out.println("#");
            System.out.println("# A fatal error has been detected by the Java Runtime Environment:");
            System.out.println("#");
            System.out.println("#  SIGBUS (0xa) at pc=0x00007f8b4c5e3a1b, pid=54321, tid=0x00007f8b3c0ff700");
            System.out.println("#");
            System.out.println("# JRE version: OpenJDK Runtime Environment (17.0.1+12) (build 17.0.1+12-LTS)");
            System.out.println("# Java VM: OpenJDK 64-Bit Server VM (17.0.1+12-LTS, mixed mode, sharing)");
            System.out.println("# Problematic frame:");
            System.out.println("# C  [libc.so.6+0x3a1b]  memcpy+0x1ab");
            System.out.println("#");

            // Exit with crash-like code
            System.exit(138); // SIGBUS exit code
        }
    }
}
