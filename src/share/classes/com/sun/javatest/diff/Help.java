/*
 * Copyright (c) 2007, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.diff;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
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

import javax.help.DefaultHelpBroker;
import javax.help.HelpSet;
import javax.help.JHelpSearchNavigator;
import javax.swing.JFrame;
import javax.swing.JTextField;

import com.sun.javatest.regtest.Option;
import com.sun.javatest.util.ExitCount;
import com.sun.javatest.util.HelpTree;
import com.sun.javatest.util.I18NResourceBundle;
import com.sun.javatest.util.WrapWriter;

/**
 * Handles help options for main program
 */
public class Help {

    /** Creates a new instance of Help */
    public Help(List<Option> options) {
        this.options = options;
    }

    void setVersionFlag(boolean yes) {
        versionFlag = yes;
    }

//    void setReleaseNotes(boolean yes) {
//        releaseNotesFlag = yes;
//    }

    void setCommandLineHelpQuery(String query) {
        if (commandLineHelpQuery == null)
            commandLineHelpQuery = new ArrayList<String>();
        if (query != null)
            commandLineHelpQuery.addAll(Arrays.asList(query.trim().split("\\s+")));
    }

//    void setOnlineHelpQuery(String query) {
//
//        if (query == null || query.length() == 0) {
//            if (onlineHelpQuery == null)
//                onlineHelpQuery = "";
//        } else {
//            if (onlineHelpQuery == null)
//                onlineHelpQuery = query;
//            else
//                onlineHelpQuery += " " + query;
//        }
//    }

    void show(PrintStream out) {
        PrintWriter w = new PrintWriter(out);
        show(w);
        w.flush();
    }

    void show(PrintWriter out) {
//        if (releaseNotesFlag)
//            showReleaseNotes(out);

        if (versionFlag)
            showVersion(out);

        if (commandLineHelpQuery != null)
            showCommandLineHelp(out);

//        if (onlineHelpQuery != null)
//            showOnlineHelp(out);
    }

//    void showReleaseNotes(PrintWriter out) {
//        File docDir = getDocDir();
//        File notes = new File(docDir, "ReleaseNotes-jtreg.html");
//        if (notes.exists())
//            out.println(i18n.getString("help.releaseNotes", notes.getAbsolutePath()));
//        else
//            out.println(i18n.getString("help.cantFindReleaseNotes"));
//    }

    /**
     * Show version information for JavaTest.
     * @param out the stream to which to write the information
     */
    void showVersion(PrintWriter out) {
        Properties manifest = getManifestForClass(getClass());
        if (manifest == null)
            manifest = new Properties();

        String unknown = i18n.getString("help.version.unknown");

        // build properties, from manifest
        String prefix = "jtreg"; // base name of containing .jar file
        String product = "jtdiff"; // manifest.getProperty(productPrefix + "-Name", unknown);
        String version = manifest.getProperty(prefix + "-Version", unknown);
        String milestone = manifest.getProperty(prefix + "-Milestone", unknown);
        String build = manifest.getProperty(prefix + "-Build", unknown);
        String buildJavaVersion = manifest.getProperty(prefix + "-BuildJavaVersion", unknown);
        String buildDate = manifest.getProperty(prefix + "-BuildDate", unknown);

        String thisJavaHome = System.getProperty("java.home");
        String thisJavaVersion = System.getProperty("java.version");

        File classPathFile = getClassPathFileForClass(Main.class);
        String classPath = (classPathFile == null ? unknown : classPathFile.getPath());

        DateFormat df = DateFormat.getDateInstance(DateFormat.LONG);

        Object[] versionArgs = {
            product,
            version,
            milestone,
            build,
            classPath,
            thisJavaVersion,
            thisJavaHome,
            buildJavaVersion,
            buildDate
        };

        /*
         * Example format string:
         *
         * {0}, version {1} {2} {3}
         * Installed in {4}
         * Running on platform version {5} from {6}.
         * Built with {7} on {8}.
         *
         * Example output:
         *
         * jtdiff, version 3.2.2 dev b00
         * Installed in /tl/ws/jct-tools-322dev/dist/jtreg/lib/jtreg.jar
         * Running on platform version 1.5.0_06 from /opt/java/5.0/jre.
         * Built with 1.5.0_06 on 09/11/2006 07:52 PM.
         */

        out.println(i18n.getString("help.version.txt", versionArgs));
        out.println(i18n.getString("help.copyright.txt"));
    }

    private File getDocDir() {
        File classPathFile = getClassPathFileForClass(Main.class);
        if (classPathFile == null)
            return null;
        File lib = classPathFile.getParentFile();
        File home = lib.getParentFile();
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
        if (url.getProtocol().equals("file"))
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
                    if (u.equals(classPathEntry)) {
                        Properties p = new Properties();
                        InputStream in = url.openStream();
                        p.load(in);
                        in.close();
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
    void showCommandLineHelp(PrintWriter out) {
        HelpTree commandHelpTree = new HelpTree();

        Integer nodeIndent = Integer.getInteger("javatest.help.nodeIndent");
        if (nodeIndent != null)
            commandHelpTree.setNodeIndent(nodeIndent.intValue());

        Integer descIndent = Integer.getInteger("javatest.help.descIndent");
        if (descIndent != null)
            commandHelpTree.setDescriptionIndent(descIndent.intValue());

        // first, group the options by their group, and sort within group
        // by their first name
        Set<String> groups = new LinkedHashSet<String>();
        for (Option o: options)
            groups.add(o.group);
        Map<String, SortedMap<String, Option>> map =
                new LinkedHashMap<String, SortedMap<String, Option>>();
        for (String g: groups)
            map.put(g, new TreeMap<String, Option>(new CaseInsensitiveStringComparator()));
        for (Option o: options) {
            if (o.names.length > 0)
                map.get(o.group).put(o.names[0], o);
        }

        // now build the help tree nodes and add then into the primary help node
        for (String g: groups) {
            SortedMap<String, Option> optionsForGroup = map.get(g);
//                continue;
            List<HelpTree.Node> nodesForGroup = new ArrayList<HelpTree.Node>();
            for (Option o: optionsForGroup.values())
                nodesForGroup.add(createOptionHelpNode(o));
            HelpTree.Node groupNode = new HelpTree.Node(i18n, "help." + g.toString().toLowerCase(),
                    nodesForGroup.toArray(new HelpTree.Node[nodesForGroup.size()]));
            commandHelpTree.addNode(groupNode);
        }

        String progName = getProgramName();

        try {
            WrapWriter ww = new WrapWriter(out);

            if (commandLineHelpQuery == null || commandLineHelpQuery.size() == 0) {
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
            ww.write(i18n.getString("help.cmd.tail"));
            ww.write("\n\n");
            ww.write(i18n.getString("help.cmd.ant"));
            ww.write("\n\n");
            ww.write(i18n.getString("help.copyright.txt"));
            ww.write("\n\n");

            ww.flush();
        } catch (IOException e) {
            // should not happen, from PrintWriter
        }

    }

    private HelpTree.Node createOptionHelpNode(Option o) {
        String prefix = "help." + o.group.toString().toLowerCase() + "." + o.names[0];
        String arg = (o.argType == Option.ArgType.NONE ? null : i18n.getString(prefix + ".arg"));
        StringBuilder sb = new StringBuilder();
        for (String n: o.names) {
            if (sb.length() > 0)
                sb.append("  |  ");
            sb.append("-");
            sb.append(n);
            switch (o.argType) {
                case NONE:      break;
                case OLD:       // old is deprecated, so just show preferred format
                case STD:
                case FILE:
                    sb.append(":").append(arg);
                    break;

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

//        String cp = System.getProperty("java.class.path");
//        if (cp.indexOf(File.pathSeparator) == -1) {
//            File f = new File(cp);
//            if (f.getName().equals("jtreg.jar"))
//                return "java -jar jtreg.jar ";
//        }

        return "java " + Main.class.getName();
    }


//    /**
//     * Show the jtdiff online help, and exit when it is closed.
//     * @param out the stream to which to write the help
//     */
//    void showOnlineHelp(PrintWriter out) {
//
//        out.println(i18n.getString("help.onlineHelp.pleaseWait"));
//        out.flush();
//
//        // use a customized HelpBroker that will exit the VM when closed
//        URL u = HelpSet.findHelpSet(null, "com/sun/javatest/diff/help/jtdiff.hs");
//        if (u == null)
//            throw new AssertionError("cant find jtdiff helpset");
//
//        try {
//            HelpSet helpSet = new HelpSet(null, u);
//            CustomHelpBroker b = new CustomHelpBroker(helpSet);
//            if (onlineHelpQuery != null && onlineHelpQuery.length() > 0)
//                b.search(onlineHelpQuery);
//            else
//                b.setCurrentID("home");
//            b.setDisplayed(true);
//        } catch (HelpSetException e) {
//            // TO DO...
//        }
//    }

    private static class CustomHelpBroker
            extends DefaultHelpBroker {
        CustomHelpBroker(HelpSet hs) {
            super(hs);
            ExitCount.inc();
        }

        void search(String s) {
            initPresentation();
            JFrame frame = (JFrame) (getHelpWindow());
            Container root = frame.getContentPane();
            JHelpSearchNavigator searchNav =
                    (JHelpSearchNavigator) (findComponent(root, JHelpSearchNavigator.class));
            if (searchNav == null)
                return;
            JTextField searchField =
                    (JTextField) (findComponent(searchNav, JTextField.class));
            if (searchField == null)
                return;
            searchField.setText(s);
            searchField.postActionEvent();
            setCurrentView("Search"); // name defined in jthelp.hs
        }

        @Override
        public void setDisplayed(boolean b) {
            super.setDisplayed(b);
            // can't use setDefaultActionOnClose because of
            // JavaTestSecurityManager
            JFrame frame = (JFrame) (getHelpWindow());
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    ExitCount.dec();
                }
            });
        }

        private Component findComponent(Container cont, Class<?> targetClass) {
            for (int i = 0; i < cont.getComponentCount(); i++) {
                Component c = cont.getComponent(i);
                if (targetClass.isInstance(c))
                    return c;
                if (c instanceof Container) {
                    Component child = findComponent((Container) c, targetClass);
                    if (child != null)
                        return child;
                }
            }
            return null;
        }

        private Window getHelpWindow() {
            return getWindowPresentation().getHelpWindow();
        }
    }

    private static class CaseInsensitiveStringComparator implements Comparator<String> {
        public int compare(String s1, String s2) {
            if (s1 == null && s2 == null)
                return 0;

            if (s1 == null || s2 == null)
                return (s1 == null ? -1 : +1);

            return s1.compareToIgnoreCase(s2);
        }

    }

    private List<Option> options;
//    private boolean releaseNotesFlag;
    private boolean versionFlag;
    private List<String> commandLineHelpQuery;
//    private String onlineHelpQuery;

    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(Main.class);
}
