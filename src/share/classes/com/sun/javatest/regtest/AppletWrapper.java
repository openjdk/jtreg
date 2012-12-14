/*
 * Copyright 1998-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.javatest.regtest;

import java.applet.Applet;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Dictionary;
import java.util.Hashtable;

import com.sun.javatest.Status;

/**
  * This class is the wrapper for all applet tests.
  *
  * @author Iris A Garcia
  */
public class AppletWrapper
{
    public static void main(String [] args) {
        String [] appArgs;
        try {
            FileReader in = new FileReader(args[0]);

            StringWriter out = new StringWriter();
            char [] buf = new char[1024];
            int howMany;

            while ((howMany = in.read(buf)) > 0)
                out.write(buf, 0, howMany);
            out.close();
            in.close();
            appArgs = StringArray.splitTerminator("\0", out.toString());

            // order determined by the associated write in
            // AppletAction.runOtherJVM()
            int i = 0;
            className = appArgs[i++];
            sourceDir = appArgs[i++];
            classDir  = appArgs[i++];
            classpath = appArgs[i++];
            manual = appArgs[i++];
            body   = appArgs[i++];
            appletParams = stringToDictionary(appArgs[i++]);
            appletAtts   = stringToDictionary(appArgs[i++]);

        } catch (IOException e) {
            status = Status.failed("JavaTest Error:  Can't read applet args file.");
            status.exit();
        }

        AppletThreadGroup tg = new AppletThreadGroup();
        Thread t = new Thread(tg, new AppletRunnable(), "AppletThread");
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            status = Status.failed("Thread interrupted: " + t.toString());
            status.exit();
        }
        // never get here because t.run() always calls Status.exit() in the
        // non-interrupted case
    } // main()

    private static Dictionary stringToDictionary(String s) {
        String [] pairs = StringArray.splitTerminator("\034", s);
        Dictionary retVal = new Hashtable(3);
        for (int i = 0; i < pairs.length; i+=2)
            retVal.put(pairs[i], pairs[i+1]);
        return retVal;
    } // stringToDictionary()

//     //  For printing debug messages
//     private static void Msg(String s) {
//      System.out.println(Thread.currentThread().getName() + ": " + s);
//      System.out.println(Thread.currentThread().getThreadGroup());
//     } // Msg()

    static class AppletRunnable implements Runnable
    {
        public void run() {
            waiter = new AppletWaiter();

            int width  = Integer.parseInt((String) appletAtts.get("width"));
            int height = Integer.parseInt((String) appletAtts.get("height"));
            AppletFrame app = new AppletFrame(className, body, manual, width, height);

            // center the frame
            app.pack();
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Dimension fSize = app.getSize();
            app.setLocation(screenSize.width/2  - fSize.width/2,
                            screenSize.height/2 - fSize.height/2);

            // We want to show the frame which contains the running applet as
            // soon as possible.  If the tests runs a dialog in the init() or
            // start() that would end up blocking, our frame will still be
            // shown.
            app.setVisible(true);

            app.getApplet().init();
            validate(app.getApplet());
            app.getApplet().start();

            app.setVisible(true);
            validate(app);

            if (manual.equals("novalue") || manual.equals("unset")) {
                // short pause for vaguely automated stuff, per the spec
                // which requires a delay of "a few seconds" at this point
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    status = Status.failed("Thread interrupted: " + e.toString());
                    status.exit();
                }
                // just in case the system is slow, ensure paint is called
                app.getApplet().paint(app.getApplet().getGraphics());

            } else {
                // wait for user to click on "Pass", "Fail", or "Done"
                waiter.waitForDone();
            }

            app.getApplet().stop();
            app.getApplet().destroy();

            app.dispose();

            //Toolkit.getDefaultToolkit().beep();
            status.exit();
        } // run()

        private void validate(final Component c) {
            try {
                Class eventQueueClass = EventQueue.class;
                Method isDispatchThread  = eventQueueClass.getMethod("isDispatchThread", new Class[] {});
                Method invokeAndWait = eventQueueClass.getMethod("invokeAndWait", new Class[] { Runnable.class });
                if (!((Boolean) (isDispatchThread.invoke(null, new Object[] { }))).booleanValue()) {
                    invokeAndWait.invoke(null, new Object[] { new Runnable() {
                            public void run() {
                                c.validate();
                            }
                        }});
                    return;
                }
            }
            catch (NoSuchMethodException e) {
                // must be JDK 1.1 -- fallthrough
            }
            catch (Throwable t) {
                t.printStackTrace();
            }
            c.validate();

        }
    } // class AppletRunnable


    /**
     * The actual applet being tested is run in its own thread group.
     * We need to do this so that we can implement
     * ThreadGroup.uncaughtException() so that we may catch all exceptions from
     * the test (including those from subsidiary threads).
     */
    static class AppletThreadGroup extends ThreadGroup
    {
        AppletThreadGroup() {
            super("AppletThreadGroup");
        } // AppletThreadGroup()

        public void uncaughtException(Thread t, Throwable e) {
            // !!!! major simplification - only works for otherJVM
            // don't have to worry about whether the exception occurred due to
            // our own cleanup since we always exit at the first exception
            if (e instanceof ThreadDeath)
                return;
            e.printStackTrace();
            status = Status.failed("Applet thread threw exception: " + e.toString());
            status.exit();
        } // uncaughtException()

    } // class AppletThreadGroup

    /**
     * A GUI object that contains the applet under test and any other GUI
     * elements as specified by the JDK Test Framework.
     */
    static class AppletFrame extends Frame implements ActionListener
    {
        /**
         * Create the AppletFrame frame which contains the running applet and
         * any instructions, buttons, etc.
         *
         * @param className The name of the applet class which must extend
         *         java.awt.Applet.
         * @param text   A string containing the test instructions from the body
         *         of the HTML file.
         * @param manual From the provided test description, an indicator of the
         *         type of the test ("novalue", "unset", "yesno", or "done").
         * @param width The width obtained from the HTML applet tag's "width"
         *         attribute.  The initial width of the applet.
         * @param height The height obtained from the HTML applet tag's "height"
         *         attribute.  The initial height of the applet.
         */
        public AppletFrame(String className, String text, String manual, int width, int height) {
            super(className);

            GridBagLayout gridbag = new GridBagLayout();
            setLayout(gridbag);

            { // "runnning applet:" label
                GridBagConstraints c  = new GridBagConstraints();
                c.anchor = GridBagConstraints.WEST;
                c.gridx = 0;
                c.gridy = 0;
                c.gridwidth = 2;
                c.weightx = 0.0;
                makeLabel("running applet:", gridbag, c);
            }

            { // the applet
                GridBagConstraints c  = new GridBagConstraints();
                c.insets = new Insets(3,3,3,3);
                c.gridx  = 0;
                c.gridy  = 1;
                c.gridwidth = 2;
                c.fill = GridBagConstraints.BOTH;
                c.weightx = 1.0;
                c.weighty = 1.0;
                makeApplet(className, gridbag, c, width, height);
            }

            // consider adding this anonymous class to makeApplet() or just
            // moving it down  to after the layout stuff
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent event) {
                    dispose();
                    status = Status.failed("Test canceled at user request");
                    status.exit();
                }
            });

            if (! manual.equals("novalue") && ! manual.equals("unset")) {
                { // "applet size:" label and CheckBoxGroup
                    GridBagConstraints c  = new GridBagConstraints();
                    c.insets = new Insets(-3,-3,-3,-3);
                    c.gridx = 0;
                    c.gridy = 2;
                    c.gridwidth = 1;
                    c.weightx = 0.0;
                    c.anchor  = GridBagConstraints.WEST;
                    String [] boxNames = {"fixed", "variable"};
                    makeCheckboxPanel(boxNames, gridbag, c);
                }

                /// TAG-SPEC: If /manual is specified, the the HTML file itself will be
                /// displayed.
                { // "html file instructions:" label
                    GridBagConstraints c  = new GridBagConstraints();
                    c.anchor = GridBagConstraints.WEST;
                    c.gridx = 0;
                    c.gridy = 3;
                    c.gridwidth = 2;
                    makeLabel("html file instructions:", gridbag, c);
                }

                { // The body of the html file.
                    GridBagConstraints c  = new GridBagConstraints();
                    c.insets = new Insets(3,3,3,3);
                    c.fill = GridBagConstraints.HORIZONTAL;
                    c.gridx = 0;
                    c.gridy = 4;
                    c.gridwidth = 2;
                    makeTextArea(text, gridbag, c);
                }

                { // The buttons.
                    GridBagConstraints c  = new GridBagConstraints();
                    c.insets = new Insets(3,3,3,3);
                    c.gridx = 0;
                    c.gridy = 5;
                    if (manual.equals("yesno")) {
                        c.gridwidth = 1;
                        makeButton("Pass", gridbag, c);
                        c.gridx = 1;
                        makeButton("Fail", gridbag, c);
                    } else {
                        c.gridwidth = 2;
                        makeButton("Done", gridbag, c);
                    }
                }
            }

            // Now that we've figured out what we want to display, let the
            // layout manager figure out how it wants to display it.
            validate();
        } // AppletFrame()

        /**
         * This method is an event handler for our button events.
         *
         * @param e The button event.
         */
        public void actionPerformed(ActionEvent e) {
            if (e.getActionCommand().equals("Done")) {
                status = Status.passed("");
            } else if (e.getActionCommand().equals("Pass")) {
                status = Status.passed("");
            } else if (e.getActionCommand().equals("Fail")) {
                status = Status.failed("");
            } else {
                status = Status.failed("Unexpected result");
            }
            // time to go home
            waiter.done();
        } // actionPerformed()

        //----------accessor methods--------------------------------------------

        public Applet getApplet() {
            return applet;
        } // getApplet()

        //----------internal methods--------------------------------------------

        private void makeApplet(String className, GridBagLayout gridbag, GridBagConstraints c,
                                int width, int height) {
            try {
                Class cls = Class.forName(className);
                applet = (Applet) cls.newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
                status = Status.error("Unable to instantiate: " + className +
                                      " does not extend Applet");
                status.exit();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                status = Status.error("Illegal access to test: " + className);
                status.exit();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                //status = Status.error("JavaTest Error: Class not found: " +
                //                      className);
                status = Status.error(e.getMessage());
                status.exit();
            }

            // create the panel where the test applet will reside
            appletPanel = new AppletPanel(applet, width, height);
            gridbag.setConstraints(appletPanel, c);
            add(appletPanel, c);
        } // makeApplet()

        private void makeButton(String name, GridBagLayout gridbag, GridBagConstraints c) {
            Button button = new Button(name);
            gridbag.setConstraints(button, c);
            add(button);
            // add a listener to receive events from the button
            button.addActionListener(this);
        } // makeButton()

        private void makeLabel(String name, GridBagLayout gridbag, GridBagConstraints c) {
            Label label = new Label(name);
            label.setFont(new Font("Dialog", Font.BOLD, 12));
            gridbag.setConstraints(label, c);
            add(label);
        } // makeLabel()

        private void makeTextArea(String name, GridBagLayout gridbag, GridBagConstraints c) {
            TextArea textArea = new TextArea(name, 16, 80);
            gridbag.setConstraints(textArea, c);
            textArea.setEditable(false);
            add(textArea);
        } // makeTextArea()

        private void makeCheckboxPanel(String [] name, GridBagLayout gridbag, GridBagConstraints c) {
            CheckboxPanel p = new CheckboxPanel(appletPanel, name);
            gridbag.setConstraints(p, c);
            add(p);
        } // makeCheckboxPanel

        //----------member variables--------------------------------------------

        private Applet applet;
    } // class AppletFrame

    //----------misc static----------------------------------------------------

    private static final String FILESEP  = System.getProperty("file.separator");
    private static final String PATHSEP  = System.getProperty("path.separator");
    private static final String JAVAHOME = System.getProperty("java.home");

    //----------member variables-----------------------------------------------

    private static Status status = Status.passed("");

    private static AppletWaiter waiter;
    private static AppletPanel appletPanel;

    private static String className;
    private static String sourceDir;
    private static String classDir;
    private static String classpath;
    private static String manual;
    private static String body;
    private static Dictionary appletParams;
    private static Dictionary appletAtts;
} // class AppletWrapper

/**
 * This is the panel which contains the checkboxes which control the behaviour
 * of the AppletPanel as resize events are sent to the AppletFrame frame.
 */
class CheckboxPanel extends Panel
{
    public CheckboxPanel(AppletPanel appletPanel, String [] boxNames) {
        panel = appletPanel;

        CheckboxGroup group = new CheckboxGroup();
        b1 = new Checkbox(boxNames[0], true, group);
        b2 = new Checkbox(boxNames[1], false, group);

        Label label = new Label("applet size:");
        label.setFont(new Font("Dialog", Font.BOLD, 12));
        add(label);

        add(b1);
        add(b2);

        b1.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent event) {
                panel.setFixedSize();
            }
        });

        b2.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent event) {
                panel.setVariableSize();
            }
        });
    } // makeCheckBoxGroup()

    private Checkbox b1, b2;
    private AppletPanel panel;

} // class CheckboxPanel

/**
 * This is the panel which contains the test applet.
 */
class AppletPanel extends Panel
{
    AppletPanel(Applet applet, int width, int height) {
        layout = new GridBagLayout();
        setLayout(layout);

        int inc = 2 * borderWidth;
        pSize = new Dimension(width + inc, height + inc);
        mSize = new Dimension(width + inc, height + inc);
        outerAppletPanel = this;
        nestedAppletPanel = new NestedAppletPanel(applet);
        setFixedSize();
        this.applet = applet;
        add(nestedAppletPanel);
    } // AppletPanel()

    /**
     * Method called if the "fixed" checkbox has been selected.  The size of the
     * applet will remain fixed regardless of resize events to the AppletFrame
     * frame.
     */
    public void setFixedSize() {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0.0;
        c.weighty = 0.0;

        // trick:  To cause a GUI component resize to take effect immediately,
        // remove it, change its constraints, then add it back.
        remove(nestedAppletPanel);
        layout.setConstraints(nestedAppletPanel, c);
        add(nestedAppletPanel);
        // may not be necessary since we should get auto validation on add
        validate();
    } // setFixedSize()

    /**
     * Method called if the "variable" checkbox has been selected.  The applet
     * will expand to fill the available space.  Resizing the frame will resize
     * the applet.
     */
    public void setVariableSize() {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.weighty = 1.0;

        // trick:  To cause a GUI component resize to take effect immediately,
        // remove it, change its constraints, then add it back.
        remove(nestedAppletPanel);
        layout.setConstraints(nestedAppletPanel, c);
        add(nestedAppletPanel);
        // may not be necessary since we should get auto validation on add
        validate();
    } // setVariableSize()

    //----------layout manager callbacks----------------------------------------

    public Dimension getMinimumSize() {
        return mSize;
    } // getMinimumSize()

    public Dimension getPreferredSize() {
        return pSize;
    } // getPreferredSize()

    public void setSize(int width, int height) {
        if (initialSize == null)
            initialSize = getSize();
        else if (initialSize != getSize()) {
            pSize = mSize = getSize();
            getParent().invalidate();
            getParent().validate();
        }
    } // setSize()

    public Insets getInsets() {
        return new Insets(0, 0, 0, 0);
    } // getInsets()

    //----------internal classes------------------------------------------------

    /**
     * Class used to manage the geometry of the test applet and to draw a box
     * around the space available for the applet.
     */
    class NestedAppletPanel extends Panel
    {
        NestedAppletPanel(Applet applet) {
            setLayout(new BorderLayout());
            add(applet, "Center");
        } // NestedAppletPanel()

        public void paint(Graphics g) {
            g.drawRect(0, 0, getSize().width -1, getSize().height -1);
            super.paint(g);
        } // paint()

        public Dimension getMinimumSize() {
            return mSize;
        } // getMinimumSize()

        public Dimension getPreferredSize() {
            return pSize;
        } // getPreferrredSize()

        public Insets getInsets() {
            return new Insets(borderWidth, borderWidth, borderWidth, borderWidth);
        } // getInsets()
     } // class NestedAppletPanel

    //----------member variables------------------------------------------------

    private Dimension initialSize = null;
    private Dimension pSize;
    private Dimension mSize;
    private static final int borderWidth = 3;
    private Panel nestedAppletPanel;
    private Panel outerAppletPanel;
    private Applet applet;
    private GridBagLayout layout;

} // class AppletPanel

/**
 * Wait until the user indicates that the test has completed.
 */
class AppletWaiter
{
    public AppletWaiter() {
//      done = false;
    } // AppletWaiter()

    public synchronized void waitForDone() {
        while (!done) {
            try {
                wait();
            } catch (InterruptedException e) {
                Status.failed("Thread interrupted: " + e.toString()).exit();
            }
        }
    } // waitForDone();

    public synchronized void done() {
        done = true;
        notify();
    } // done()

    private boolean done = false;
} // class AppletWaiter
