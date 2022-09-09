/*
 * Copyright (c) 2006, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.javatest.regtest.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import com.sun.javatest.regtest.agent.SearchPath;
import com.sun.javatest.util.HelpTree;
import com.sun.javatest.util.I18NResourceBundle;
import com.sun.javatest.util.WrapWriter;

/**
 * Handles help options for main program
 */
public class Help {
    interface VersionHelper {
        void showVersion(PrintWriter out);
    }

    /**
     * Creates a new instance of Help
     * @param options the options to be documented in the help output
     */
    public Help(List<Option> options) {
        this.options = options;
    }

    boolean isEnabled() {
        return versionFlag
                || releaseNotesFlag
                || tagSpecFlag
                || (commandLineHelpQuery != null);
    }

    void addVersionHelper(VersionHelper h) {
        versionHelpers.add(h);
    }

    void addJarVersionHelper(final String name, final File jar, final String pomProperties) {
        addVersionHelper(new Help.VersionHelper() {
            @Override
            public void showVersion(PrintWriter out) {
                try (JarFile j = new JarFile(jar)) {
                    String v = (String) j.getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION);
                    if (v == null && pomProperties != null) {
                        Properties p = new Properties();
                        try (InputStream in = j.getInputStream(j.getEntry(pomProperties))) {
                            p.load(in);
                            v = p.getProperty("version");
                        }
                    }
                    out.println(name + ": version " + (v == null ? "unknown" : v)); // need i18n
                } catch (IOException e) {
                }
            }
        });
    }

    void addPathVersionHelper(final String name, final SearchPath path) {
        addVersionHelper(new Help.VersionHelper() {
            @Override
            public void showVersion(PrintWriter out) {
                // print list of jar files in path
                out.println(name + ": " + path.asList().stream()
                        .map(Path::getFileName)
                        .map(Object::toString)
                        .collect(Collectors.joining(", ")));
                try {
                    // look inside jar metadata for details for those jar files that do
                    // not seem to have a version in their filename
                    for (Path jar: path.asList()) {
                        String fn = jar.getFileName().toString();
                        if (!fn.matches("(?i)[a-z0-9-_]+-[0-9](\\.[0-9]+)+\\.jar")) {
                            try (JarFile j = new JarFile(jar.toFile())) {
                                Attributes attrs = j.getManifest().getMainAttributes();
                                String v = attrs.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                                if (v == null) {
                                    v = attrs.getValue("Bundle-Version");
                                }
                                String suffix = (path.asList().size() == 1)
                                        ? "" : " (" + jar.getFileName() + ")";
                                out.println(name + suffix + ": version " + (v == null ? "unknown" : v)); // need i18n
                            }
                        }
                    }
                } catch (IOException e) {
                }
            }
        });
    }

    void setVersionFlag(boolean yes) {
        versionFlag = yes;
    }

    void setReleaseNotes(boolean yes) {
        releaseNotesFlag = yes;
    }

    void setTagSpec(boolean yes) {
        tagSpecFlag = yes;
    }

    void setCommandLineHelpQuery(String query) {
        if (commandLineHelpQuery == null)
            commandLineHelpQuery = new ArrayList<>();
        if (query != null && query.trim().length() > 0)
            commandLineHelpQuery.addAll(Arrays.asList(query.trim().split("\\s+")));
    }

    void show(PrintStream out) {
        PrintWriter w = new PrintWriter(out);
        show(w);
        w.flush();
    }

    boolean show(PrintWriter out) {
        boolean gui = false;

        if (releaseNotesFlag)
            showReleaseNotes(out);

        if (tagSpecFlag)
            showTagSpec(out);

        if (versionFlag)
            showVersion(out);

        if (commandLineHelpQuery != null)
            showCommandLineHelp(out);

        return gui;
    }

    void showReleaseNotes(PrintWriter out) {
        File homeDir = getHomeDir();
        File notes = new File(homeDir, "README");
        if (notes.exists())
            out.println(i18n.getString("help.releaseNotes", notes.getAbsolutePath()));
        else
            out.println(i18n.getString("help.cantFindReleaseNotes"));
    }

    void showTagSpec(PrintWriter out) {
        File docDir = getDocDir();
        File tagSpec = new File(docDir, "tag-spec.txt");
        try (BufferedReader in = new BufferedReader(new FileReader(tagSpec))) {
            String line;
            while ((line = in.readLine()) != null)
                out.println(line);
        } catch (FileNotFoundException e) {
            out.println(i18n.getString("help.cantFindSpec"));
        } catch (IOException e) {
            out.println(i18n.getString("help.cantReadSpec", e));
        }
    }

    /**
     * Show version information for JavaTest.
     * @param out the stream to which to write the information
     */
    void showVersion(PrintWriter out) {
        Version v = Version.getCurrent();

        String unknown = i18n.getString("help.version.unknown");

        // build properties, from manifest
        String prefix = "jtreg"; // base name of containing .jar file
        String product = v.getProperty(prefix + "-Name", unknown);

        String versionString = v.versionString; // use new-style if available
        if (versionString == null) {
            // old-style
            String version = v.getProperty(prefix + "-Version", unknown);
            String milestone = v.getProperty(prefix + "-Milestone", unknown);
            String build = v.getProperty(prefix + "-Build", unknown);
            versionString = String.format("version %s %s %s", version, milestone, build);
        }

        String buildJavaVersion = v.getProperty(prefix + "-BuildJavaVersion", unknown);
        String buildDate = v.getProperty(prefix + "-BuildDate", unknown);

        String thisJavaHome = System.getProperty("java.home");
        String thisJavaVersion = System.getProperty("java.version");

        File classPathFile = getClassPathFileForClass(Tool.class);
        String classPath = (classPathFile == null ? unknown : classPathFile.getPath());

        DateFormat df = DateFormat.getDateInstance(DateFormat.LONG);

        Object[] versionArgs = {
            product,
            versionString,
            classPath,
            thisJavaVersion,
            thisJavaHome,
            buildJavaVersion,
            buildDate
        };

        /*
         * Example format string:
         *
         * {0}, version {1}
         * Installed in {2}
         * Running on platform version {3} from {4}.
         * Built with {5} on {6}.
         *
         * Example output:
         *
         * jtreg, version 3.2.2 dev b00
         * Installed in /tl/ws/jct-tools-322dev/dist/jtreg/lib/jtreg.jar
         * Running on platform version 1.5.0_06 from /opt/java/5.0/jre.
         * Built with 1.5.0_06 on 09/11/2006 07:52 PM.
         */

        out.println(i18n.getString("help.version.txt", versionArgs));
        out.println(i18n.getString("help.copyright.txt"));
        for (VersionHelper h: versionHelpers)
            h.showVersion(out);
    }

    private File getHomeDir() {
        File classPathFile = getClassPathFileForClass(Tool.class);
        if (classPathFile == null)
            return null;
        File lib = classPathFile.getParentFile();
        File home = lib.getParentFile();
        return home;
    }

    private File getDocDir() {
        File home = getHomeDir();
        File doc = new File(new File(home, "doc"), "jtreg");
        if (doc.exists())
            return doc;
        return null;
    }

    private URL getClassPathEntryForClass(Class<?> c) {
        try {
            URL url = c.getResource("/" + c.getName().replace('.', '/') + ".class");
            if (url.getProtocol().equals("jar")) {
                String path = url.getPath();
                int sep = path.lastIndexOf("!");
                return new URL(path.substring(0, sep));
            }
        } catch (MalformedURLException ignore) {
        }
        return null;
    }

    private File getClassPathFileForClass(Class<?> c) {
        URL url = getClassPathEntryForClass(c);
        if (url != null && url.getProtocol().equals("file"))
            return new File(url.getPath());
        return null;
    }

    private Properties getManifestForClass(Class<?> c) {
        URL classPathEntry = getClassPathEntryForClass(c);
        if (classPathEntry == null)
            return null;

        try {
            Enumeration<URL> e = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (e.hasMoreElements()) {
                URL url = e.nextElement();
                if (url.getProtocol().equals("jar")) {
                    String path = url.getPath();
                    int sep = path.lastIndexOf("!");
                    URL u = new URL(path.substring(0, sep));
                    if (u.equals(classPathEntry )) {
                        Properties p = new Properties();
                        try (InputStream in = url.openStream()) {
                            p.load(in);
                        }
                        return p;
                    }
                }
            }
        } catch (IOException ignore) {
        }
        return null;
    }


    /**
     * Print out info about the options accepted by the command line decoder.
     * @param out A stream to which to write the information.
     */
    public void showCommandLineHelp(PrintWriter out) {
        HelpTree commandHelpTree = new HelpTree();

        // Try to override the default comparator
        try{
            Field f = HelpTree.class.getDeclaredField("nodeComparator");
            f.setAccessible(true);
            f.set(commandHelpTree, new NodeComparator());
        } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException ignore) {
        }

        Integer nodeIndent = Integer.getInteger("javatest.help.nodeIndent");
        if (nodeIndent != null)
            commandHelpTree.setNodeIndent(nodeIndent);

        Integer descIndent = Integer.getInteger("javatest.help.descIndent");
        if (descIndent != null)
            commandHelpTree.setDescriptionIndent(descIndent);

        // first, group the options by their group, and sort within group
        // by their first name
        Set<String> groups = new LinkedHashSet<>();
        for (Option o: options)
            groups.add(o.group);
        Map<String, SortedMap<String, Option>> map = new LinkedHashMap<>();
        for (String g: groups)
            map.put(g, new TreeMap<>(new CaseInsensitiveStringComparator()));
        for (Option o: options) {
            if (o.names.length > 0)
                map.get(o.group).put(o.names[0], o);
        }

        // now build the help tree nodes and add then into the primary help node
        for (String g: groups) {
            SortedMap<String, Option> optionsForGroup = map.get(g);
            if (optionsForGroup.isEmpty())
                continue;
            List<HelpTree.Node> nodesForGroup = new ArrayList<>();
            for (Option o: optionsForGroup.values())
                nodesForGroup.add(createOptionHelpNode(o));
            HelpTree.Node groupNode = new HelpTree.Node(i18n, "help." + g.toLowerCase(),
                    nodesForGroup.toArray(new HelpTree.Node[nodesForGroup.size()]));
            commandHelpTree.addNode(groupNode);
        }

        HelpTree.Node testsNode = new HelpTree.Node(i18n, "help.tests",
                //new String[] { "at", "groups", "summary" },
                new HelpTree.Node(i18n, "help.tests", "at"),
                new HelpTree.Node(i18n, "help.tests", "groups"),
                new HelpTree.Node(i18n, "help.tests.summary",
                        new String[] { "directory", "file", "group", "at-file" }));
        commandHelpTree.addNode(testsNode);

        String progName = getProgramName();

        try {
            WrapWriter ww = new WrapWriter(out);

            if (commandLineHelpQuery == null || commandLineHelpQuery.isEmpty()) {
                // no keywords given
                ww.write(i18n.getString("help.cmd.proto", progName));
                ww.write("\n\n");
                ww.write(i18n.getString("help.cmd.introHead"));
                ww.write('\n');
                commandHelpTree.writeSummary(ww);
            } else if (commandLineHelpQuery.contains("all")) {
                // -help all
                ww.write(i18n.getString("help.cmd.proto", progName));
                ww.write("\n\n");
                ww.write(i18n.getString("help.cmd.fullHead"));
                ww.write('\n');
                commandHelpTree.write(ww);
            } else {
                String[] query = commandLineHelpQuery.toArray(new String[commandLineHelpQuery.size()]);
                HelpTree.Selection s = commandHelpTree.find(query);
                if (s != null)
                    commandHelpTree.write(ww, s);
                else {
                    ww.write(i18n.getString("help.cmd.noEntriesFound"));
                    ww.write("\n\n");
                    ww.write(i18n.getString("help.cmd.summaryHead"));
                    ww.write('\n');
                    commandHelpTree.writeSummary(ww);
                }
            }

            ww.write('\n');
            ww.write(i18n.getString("help.copyright.txt"));
            ww.write("\n\n");

            ww.flush();
        } catch (IOException e) {
            // should not happen, from PrintWriter
        }

    }

    private HelpTree.Node createOptionHelpNode(Option o) {
        String prefix = "help." + o.group.toLowerCase() + "." +
                o.names[0].replaceAll("^-+", "").replaceAll("[^A-Za-z0-9.]+", "_");
        String arg = (o.argType == Option.ArgType.NONE ? null : i18n.getString(prefix + ".arg"));
        StringBuilder sb = new StringBuilder();
        for (String n: o.names) {
            if (sb.length() > 0)
                sb.append("  |  ");
            sb.append(n);
            switch (o.argType) {
                case NONE:
                    break;

                case OLD:       // old is deprecated, so just show preferred format
                case STD:
                case FILE:
                    sb.append(":").append(arg);
                    break;

                case GNU:
                case SEP:
                case REST:
                    sb.append(" ").append(arg);
                    break;

                case WILDCARD:
                    sb.append(arg);
                    break;

                case OPT:
                    sb.append("  |  -").append(n).append(":").append(arg);
                    break;

                default:
                    throw new AssertionError();
            }
        }

        String name = sb.toString();
        String desc = i18n.getString(prefix + ".desc");
        String[] values = o.getChoices();
        if (values == null || values.length == 0)
            return new HelpTree.Node(name, desc);
        else {
            HelpTree.Node[] children = new HelpTree.Node[values.length];
            for (int i = 0; i < children.length; i++)
                children[i] = new HelpTree.Node(values[i], i18n.getString(prefix + "." + values[i] + ".desc"));
            return new HelpTree.Node(name, desc, children);
        }
    }

    private static String getProgramName() {
        String p = System.getProperty("program");
        if (p != null)
            return p;

        List<Path> cp = new SearchPath(System.getProperty("java.class.path")).asList();
        if (cp.size() == 1 && cp.get(0).getFileName().toString().equals("jtreg.jar")) {
            return "java -jar jtreg.jar ";
        }

        return "java " + Tool.class.getName();
    }

    private static class NodeComparator implements Comparator<HelpTree.Node> {
        @Override
        public int compare(HelpTree.Node n1, HelpTree.Node n2) {
            int v = n1.getName().replaceAll("^-+", "")
                    .compareToIgnoreCase(n2.getName().replaceAll("^-+", ""));
            return (v != 0 ? v : n1.getDescription().compareToIgnoreCase(n2.getDescription()));
        }
    }

    private static class CaseInsensitiveStringComparator implements Comparator<String> {
        @Override
        public int compare(String s1, String s2) {
            if (s1 == null && s2 == null)
                return 0;

            if (s1 == null || s2 == null)
                return (s1 == null ? -1 : +1);

            return s1.replaceAll("^-*", "")
                    .compareToIgnoreCase(s2.replaceAll("^-*", ""));
        }

    }

    private final List<Option> options;
    private final List<VersionHelper> versionHelpers = new ArrayList<>();
    private boolean releaseNotesFlag;
    private boolean tagSpecFlag;
    private boolean versionFlag;
    private List<String> commandLineHelpQuery;

    private static final I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(Help.class);
}
