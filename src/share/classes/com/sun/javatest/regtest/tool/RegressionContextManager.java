/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import com.sun.javatest.exec.ContextManager;
import com.sun.javatest.exec.JavaTestMenuManager;
import com.sun.javatest.util.I18NResourceBundle;

/**
 * Support class to configure UI.
 */
// NOTE: this is not related to RegressionContext  in any way.
public class RegressionContextManager extends ContextManager {

    @Override
    public JavaTestMenuManager getMenuManager() {
        return new RegressionMenuManager();
    }

    static class RegressionMenuManager extends JavaTestMenuManager {

        @Override
        public JMenuItem[] getMenuItems(int position) {
            switch (position) {
                case JavaTestMenuManager.HELP_TESTSUITE:
                    return createHelpAboutItems();
                default:
                    return super.getMenuItems(position);
            }
        }

        JMenuItem[] createHelpAboutItems() {
            JMenuItem mi = new JMenuItem("About jtreg");
            mi.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    showAbout((JComponent) e.getSource());
                }
            });
            return new JMenuItem[] { mi };
        }

        void showAbout(JComponent parent) {
            Version v = Version.getCurrent();
            String title = String.format("%s %s %s %s",
                    v.product, v.version, v.milestone, v.build);
            String copyright = i18n.getString("help.copyright.txt");
            List<String> content = new ArrayList<>();
            content.add(title);
            content.addAll(Arrays.asList(copyright.split("\n")));
            URL logoURL = getClass().getResource("jtlogo.png");
            Image logoImage = Toolkit.getDefaultToolkit().getImage(logoURL);
            ImageIcon logo = new ImageIcon(logoImage);
            JOptionPane.showMessageDialog(parent,
                    content.toArray(),
                    "Regression Test Harness for the OpenJDK platform: jtreg",
                    JOptionPane.INFORMATION_MESSAGE,
                    logo);
        }
    }

    private static final I18NResourceBundle i18n =
            I18NResourceBundle.getBundleForClass(RegressionContextManager.class);

}
