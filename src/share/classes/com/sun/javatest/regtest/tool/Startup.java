/*
 * Copyright (c) 1996, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Label;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.net.URL;

/**
 * A lightweight class to display a startup (splash) image.
 * The class is intentionally as lightweight as possible,
 * to be fast during startup. It does not use Swing.
 */
public class Startup {
    private final String title;
    private final URL url;
    private final String line1;
    private final String line2;
    private final String line3;

    /**
     * Creates a Startup object, containing the values to be displayed.
     *
     * @param title the title for the frame
     * @param logo a URL for a logo to be displayed
     * @param line1 a line of text to be displayed at the top of the frame
     * @param line2 a line of text to be displayed in the center of the frame
     * @param line3  a line of text to be displayed at the bottom of the frame
     */
    public Startup(String title, URL logo, String line1, String line2, String line3) {
        this.title = title;
        this.url = logo;
        this.line1 = line1;
        this.line2 = line2;
        this.line3 = line3;
    }

    /**
     * Show the startup (splash) screen.
     * @return the frame containing the splash screen
     */
    Frame show() {
        final Image image = Toolkit.getDefaultToolkit().createImage(url);
        Canvas c = new Canvas() {
            private static final long serialVersionUID = 1L;
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(image.getWidth(null), image.getHeight(null));
            }

            @Override
            public void update(Graphics g) {
                paint(g);
            }

            @Override
            public void paint(Graphics g) {
                g.drawImage(image, 0, 0, null);
            }
        };
        c.setFocusable(false);


        try {
            MediaTracker t = new MediaTracker(c);
            t.addImage(image, 0);
            t.waitForAll();
        }
        catch (InterruptedException e) {
        }

        Frame frame = new Frame(title);
        frame.setBackground(new Color(0.97f, 0.97f, 0.97f));
        frame.add(new Label(line1, Label.CENTER), "North");
        frame.add(new Label(line2, Label.CENTER), "Center");
        frame.add(new Label(line3, Label.CENTER), "South");
        frame.add(new Label("           "), "East");
        frame.add(c, "West");
        frame.setResizable(false);

        frame.pack();

        Dimension size = frame.getSize();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        frame.setLocation(ge.getCenterPoint().x - size.width/2,
                          ge.getCenterPoint().y - size.height/2);
        frame.setVisible(true);

        return frame;
    }
}
