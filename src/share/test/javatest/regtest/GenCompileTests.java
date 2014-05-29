
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class GenCompileTests {
    public static void main(String... args) throws IOException {
        GenCompileTests g = new GenCompileTests();
        g.run(new File(args[0]), Integer.parseInt(args[1]));
    }

    void run(File dir, int count) throws IOException {
        writeFile(dir, "TEST.ROOT", "TestNG.dirs=testng");
        genBuildTests(new File(dir, "build"), count);
        genCompileTests(new File(dir, "compile"), count);
        genTestNGTests(new File(dir, "testng"), count);
    }

    void genBuildTests(File dir, int count) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("/* @test\n");
        sb.append(" * @build");
        for (int i = 0; i < count; i++) {
            writeFile(dir, "C" + i + ".java", "class C" + i + " { }");
            sb.append(" C").append(i);
        }
        sb.append("\n"
                + "@run main C"
                + "*/\n"
                + "public class C { public static void main(String... args) { } }");
        writeFile(dir, "C.java", sb.toString());
    }

    void genCompileTests(File dir, int count) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("/* @test\n");
        sb.append(" * @compile");
        for (int i = 0; i < count; i++) {
            writeFile(dir, "C" + i + ".java", "class C" + i + " { }");
            sb.append(" C").append(i).append(".java");
        }
        sb.append("\n"
                + "*/\n");
        writeFile(dir, "C.java", sb.toString());
    }

    void genTestNGTests(File dir, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            writeFile(dir, "C" + i + ".java",
                    "import org.testng.annotations.*;\n"
                    + "@Test class C" + i + " { }");
        }
    }

    void writeFile(File dir, String name, String body) throws IOException {
        dir.mkdirs();
        FileWriter out = new FileWriter(new File(dir, name));
        out.write(body);
        out.close();
    }
}
