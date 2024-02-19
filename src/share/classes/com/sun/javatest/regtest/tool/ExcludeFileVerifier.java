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
                if (lineIsComment(line.trim())) continue;
                for(Check c : checks) {
                    if(!c.check(line.trim())) {
                        System.out.println(file.getAbsolutePath() + " line " + n + " is invalid. Reason:");
                        System.out.println(c.description());
                        System.out.println("Line contents:");
                        System.out.println("--------------");
                        System.out.println(line);
                        System.out.println("--------------");
                        hadErrors = true;
                        break;
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
        public abstract String description();
        public abstract boolean check(String line);
    }

    class LineFormatCheck extends Check {
        public String description() {
            return "Must follow: <test-name> <bugid>(,<bugid>)* <platform>(,<platform>)* <description>";
        }

        public boolean check(String line) {
            // TODO a regex
            return (line.split("\\s+").length >= 3);
        }
    }

    class TestExistsCheck extends Check {
        private List<String> validTestNames;

        public TestExistsCheck(List<String> validTestNames) {
            this.validTestNames = validTestNames;
        }

        public String description() {
            return "The fully qualified test must exists.";
        }

        public boolean check(String line) {
            String[] words = line.split("\\s+");
            String fullTestName = words[0];
            return validTestNames.contains(fullTestName);
        }
    }
}