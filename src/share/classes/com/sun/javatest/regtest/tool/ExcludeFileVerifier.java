package com.sun.javatest.regtest.tool;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.file.Path;

public class ExcludeFileVerifier {
    private boolean hadErrors = false;
    public boolean getHadErrors() {
        return hadErrors;
    }
    public boolean verify(File file, List<String> validTestNames) {
        ArrayList<Check> checks = new ArrayList<Check>();
        checks.add(new LineFormatCheck());
        checks.add(new TestExistsCheck(validTestNames));

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line = null;
            int n = 0;
            while ((line = br.readLine()) != null)
            {
                n++;
                line = line.trim();
                if (lineIsComment(line)) continue;

                for(Check c : checks) {
                    boolean result = c.check(line);
                    if(!result)
                    if(!c.check(line)) {
                        System.out.println(file.getAbsolutePath() + " line " + n + ": " + c.error);
                        hadErrors = true;
                    }
                }
            }
            }
            catch (FileNotFoundException e) {
                System.out.println("File does not exist: "  + file.getAbsolutePath());
            }
            catch (IOException e) {
                System.out.println("File cannot be read: "  + file.getAbsolutePath());
            }
        return true;
    }

    static boolean lineIsComment(String line) {
        line = line.trim();
        if (line.equals("")) return true;
        if (line.charAt(0) == '#') return true;
        return false;
    }

    abstract class Check {
        public abstract String name();
        public abstract boolean check(String line);
        public String error = "no error (should never see this)";
    }

    class LineFormatCheck extends Check {
        public String name() {
            return "Check line format.";
        }

        public boolean check(String line) {
            if (line.split("\\s+").length < 3) {
                error = "Must follow format: <test-name> <bugid>(,<bugid>)* <platform>(,<platform>)* <description>";
                return false;
            }
            return true;
        }
    }

    class TestExistsCheck extends Check {
        private List<String> validTestNames;

        public TestExistsCheck(List<String> validTestNames) {
            this.validTestNames = validTestNames;
        }

        public String name() {
            return "Check that the test name exists.";
        }

        public boolean check(String line) {
            String[] words = line.split("\\s+");
            String fullTestName = words[0];
            if (!validTestNames.contains(fullTestName)) {
                error = "Test does not exist:\n>" + fullTestName + "<";
                return false;
            }
            return true;
        }
    }
}