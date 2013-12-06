import java.io.*;
import java.util.*;

public class RerunTest2 {
    public static void main(String... args) {
        try {
            new RerunTest2().run(args);
        } catch (Exception e) {
            System.err.println(e);
            System.exit(1);
        }
    }

    void run(String... args) throws Exception {
        File testSuite = new File(args[0]);
        for (String mode: new String[] { "othervm", "samevm", "agentvm" })
            test(testSuite, mode);
        if (errors > 0)
            throw new Exception(errors + " errors found");
    }

    void test(File testSuite, String mode) throws Exception {
        System.out.println("test " + mode);

        File initialWorkDir = new File(mode, "work");
        File initialReportDir = new File(mode, "report");
        runTests(testSuite, initialWorkDir, initialReportDir, mode);

        for (String test: getTests(initialReportDir)) {
            File testWorkDir = new File(mode,
                test.replaceAll("\\.java$", "").replaceAll("[^A-Za-z]+", "_"));
            rerunTest(testSuite, test, initialWorkDir, testWorkDir);
        }

        System.out.println();
    }

    void runTests(File ts, File wd, File rd, String mode) throws Exception {
        String[] args = {
            "-w", wd.getPath(),
            "-r", rd.getPath(),
            "-" + mode,
            ts.getPath()
        };
        int rc = new com.sun.javatest.regtest.Main().run(args);
        if (rc != 0)
            throw new Exception("jtreg: exit code " + rc);
    }


    private static final String markerText = "Lorem ipsum";

    void rerunTest(File testSuite, String name, File wd, File dir) throws Exception {
        System.out.println("rerun " + name);
        File script = getRerunScript(testSuite, name, wd, dir);
        Process p = new ProcessBuilder("sh", script.getAbsolutePath())
                .directory(dir)
                .redirectErrorStream(true)
                .start();
        boolean foundMarker = false;
        String line;
        BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        BufferedWriter out = new BufferedWriter(new FileWriter(new File(dir, "rerun.log")));
        try {
            while ((line = in.readLine()) != null) {
                out.write(line);
                out.newLine();
                if (line.contains(markerText))
                    foundMarker = true;
            }
        } finally {
            in.close();
            out.close();
        }
        //System.out.println("rerun exit code: " + p.waitFor());
        if (!foundMarker)
            error(dir.getParent() + ", " + name + ": marker text not found");
    }

    File getRerunScript(File testSuite, String name, File wd, File dir) throws Exception {
        String[] args = {
            "-w", wd.getPath(),
            "-show:rerun",
            "-dir:" + testSuite.getPath(),
            name
        };
        StringWriter outs = new StringWriter();
        PrintWriter out = new PrintWriter(outs);
        StringWriter errs = new StringWriter();
        PrintWriter err = new PrintWriter(outs);
        int rc;
        try {
            rc = new com.sun.javatest.regtest.Main(out,err).run(args);
        } finally {
            err.close();
            out.close();
            writeFile(new File(dir, "show-rerun-out.txt"), outs.toString());
            writeFile(new File(dir, "show-rerun-err.txt"), errs.toString());
        }
//      if (rc != 0)
//          throw new Exception("jtreg: exit code " + rc);
        return new File(dir, "show-rerun-out.txt");
    }

    List<String> getTests(File rd) throws IOException {
        List<String> tests = new ArrayList<String>();
        File summary = new File(rd, "text/summary.txt");
        BufferedReader in = new BufferedReader(new FileReader(summary));
        try {
            String line;
            while ((line = in.readLine()) != null)
                tests.add(line.substring(0, line.indexOf(' ')));
        } finally {
            in.close();
        }
        return tests;
    }

    void writeFile(File f, String s) throws IOException {
        f.getParentFile().mkdirs();
        FileWriter w = new FileWriter(f);
        try {
            w.write(s);
        } finally {
            w.close();
        }
    }

    void error(String msg) {
        System.out.println("Error: " + msg);
        errors++;
    }

    int errors;
}
