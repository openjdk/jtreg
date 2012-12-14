/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.text.*;

import com.sun.javatest.regtest.Main;

/**
 * This is a simple GUI framework to wrap around jtreg to test
 * that it handles interrupts correctly.  Run it with jtreg args,
 * or fill in the args in the GUI, then click on Start and sometime
 * later, click on Interrupt.
 */
public class InterruptTest
{
    public static void main(String... args) {
        new InterruptTest().run(args);
    }

    public void run(String... args) {
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());

        p.add(createArgsPanel(args), BorderLayout.NORTH);
        p.add(createTextPanel(), BorderLayout.CENTER);
        p.add(createButtonPanel(), BorderLayout.SOUTH);

        JFrame f = new JFrame();
        f.setContentPane(p);
        f.pack();
        f.setVisible(true);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    JPanel createArgsPanel(String... args) {
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());
        p.add(new JLabel("args: "), BorderLayout.WEST);
        argsField = new JTextField(64);
        argsField.setText(join(args));
        p.add(argsField, BorderLayout.CENTER);
        return p;
    }

    JScrollPane createTextPanel() {
        textArea = new JTextArea(30, 64);
        return new JScrollPane(textArea);
    }

    JPanel createButtonPanel() {
        final String START = "Start";
        final String INTERRUPT = "Interrupt";
        ActionListener listener = new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    String cmd = e.getActionCommand();
                    if (cmd.equals(START))
                        doStart();
                    else if (cmd.equals(INTERRUPT))
                        doInterrupt();
                }
            };
        JPanel p = new JPanel();
        p.setLayout(new FlowLayout());
        JButton start = new JButton("Start");
        start.addActionListener(listener);
        p.add(start);
        JButton interrupt = new JButton("Interrupt");
        interrupt.addActionListener(listener);
        p.add(interrupt);
        return p;
    }

    synchronized private void doStart() {
        final String[] args = argsField.getText().split(" +");
        if (worker != null)
            log("already started and still running\n");
        else {
            worker = new Thread() {
                    public void run() {
                        log("jtreg starting\n");
                        try {
                            new Main(log, log).run(args);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        } finally {
                            done();
                        }
                    };
                };
            worker.start();
        }
    }

    synchronized private void done() {
        log("jtreg exited\n");
        worker = null;
    }

    synchronized void doInterrupt() {
        if (worker == null)
            log("jtreg not running\n");
        else {
            worker.interrupt();
            log("jtreg interrupted\n");
        }
    }

    void log(final String text) {
        if (!EventQueue.isDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        log(text);
                    }
                });
            return;
        }

        Document d = textArea.getDocument();
        try {
            d.insertString(d.getLength(), text, null);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private String join(String... args) {
        StringBuffer sb = new StringBuffer();
        for (String a: args) {
            if (sb.length() > 0)
                sb.append(" ");
            if (a.length() > 0)
                sb.append(a);
        }
        return sb.toString();
    }

    private JTextField argsField;
    private JTextArea textArea;
    private Thread worker;

    private PrintWriter log = new PrintWriter(new Writer() {
            public void write(char[] buf, int offset, int length) {
                String s = new String(buf, offset, length);
                log(s);
            }
            public void flush() { }
            public void close() { }
        });
}
