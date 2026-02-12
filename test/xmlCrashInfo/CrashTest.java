/*
 * @test
 * @summary Test that simulates a JVM crash for XML output verification
 * @run main/othervm CrashTest
 */

public class CrashTest {
    public static void main(String[] args) {
        // Simulate crash output to stderr
        System.err.println("Running test...");
        System.err.println("#");
        System.err.println("# A fatal error has been detected by the Java Runtime Environment:");
        System.err.println("#");
        System.err.println("#  SIGSEGV (0xb) at pc=0x00007f8b4c5e3a1b, pid=12345, tid=0x00007f8b3c0ff700");
        System.err.println("#");
        System.err.println("# JRE version: OpenJDK Runtime Environment (11.0.12+7) (build 11.0.12+7-LTS)");
        System.err.println("# Java VM: OpenJDK 64-Bit Server VM (11.0.12+7-LTS, mixed mode, tiered, compressed oops, g1 gc, linux-amd64)");
        System.err.println("# Problematic frame:");
        System.err.println("# V  [libjvm.so+0x5e3a1b]  JavaThread::run()+0x1ab");
        System.err.println("#");
        System.err.println("# Core dump will be written. Default location: /tmp/core.12345");
        System.err.println("#");
        
        // Exit with crash-like code
        System.exit(139); // SIGSEGV exit code
    }
}
