import java.io.PrintStream;
import org.testng.annotations.*;

public class LibTest {
    @Test
    public void run() {
        PrintStream out = System.out;
        new L1(out);
        new L2(out);
        new L3(out);
        new L4(out);
        new L5(out);
        new L6(out);
    }
}

