import org.testng.annotations.*;

@Test
public class TestPath {
    public void run() throws Exception {
        String bcp = System.getProperty("sun.boot.class.path");
        String jcp = System.getProperty("java.class.path");
        System.out.println("boot class path: " + bcp);
        System.out.println("user class path: " + jcp);

        String path = bcp;

        if (path.matches(".*BootClassPathTest.work.classes.*")
                && path.matches(".*tools.jar.*")) {
            System.out.println("classes found on expected path");
        } else {
            throw new Exception("classes not found on expected path");
        }
    }
}

