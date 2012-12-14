/*
 * Copyright (c) 2000, 2007, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest;

import java.security.Permission;
import java.util.PropertyPermission;

import com.sun.javatest.JavaTestSecurityManager;

public class RegressionSecurityManager extends JavaTestSecurityManager {
    /**
     * Try to install a copy of this security manager. If another security manager is
     * already installed, the install will fail;  a warning message wil, be written to
     * the console if the previously installed security manager is not a subtype of
     * com.sun.javatest.JavaTestSecurityManager.
     * The install can be suppressed by setting the system property
     *  "javatest.security.noSecurityManager" to true.
     */
    public static void install() {
        try {
            // install our own permissive security manager, to prevent anyone else
            // installing a less permissive one.
            final String noSecurityMgr = "javatest.security.noSecurityManager";
            if (Boolean.getBoolean(noSecurityMgr)) {
                System.err.println();
                System.err.println("     ---- WARNING -----");
                System.err.println();
                System.err.println("JavaTest did not install its own Security Manager");
                System.err.println("because the property " + noSecurityMgr + " was set.");
                System.err.println("This is not a fatal error, but it may affect the");
                System.err.println("execution of sameJVM tests");
                System.err.println();
            }
            else
                System.setSecurityManager(new RegressionSecurityManager());
        }
        catch (SecurityException e) {
            SecurityManager sm = System.getSecurityManager();
            if (!(sm instanceof JavaTestSecurityManager)) {
                System.err.println();
                System.err.println("     ---- WARNING -----");
                System.err.println();
                System.err.println("JavaTest could not install its own Security Manager");
                System.err.println("because of the following exception:");
                System.err.println("     " + e);
                System.err.println("This is not a fatal error, but it may affect the");
                System.err.println("execution of sameJVM tests");
                System.err.println();
            }
        }
    }

    @Override
    public void checkExec(String cmd) {
        if (allowExec == false) {
            if (verbose) {
                System.err.println(getClass().getName() + ": subprocess creation forbidden");
                new Throwable().printStackTrace();
            }
            throw new SecurityException("Subprocess creation forbidden by JavaTest");
        }
    }

    // In jdk1.1.x, calls to setErr(), setOut(), and setIn() call
    // System.checkIO() which checks Security.checkExec().  This
    // unfortunately means that we need this access when we
    // redirect test output during a "same JVM test".
    // ... However, JavaTest 3.x assumes JDK 1.4, so this is no longer an issue.
    // See setAllowSetIO().   If necessary, setAllowSetIO could check the java version
    // and delegate to this, but this is unlikely to be necessary.
    public boolean setAllowExec(boolean bool) {
        boolean prev = allowExec;
        allowExec = bool;
        return prev;
    }

    private boolean allowExec = true; // no overrides on this one; API control only

    @Override
    public void checkPermission(Permission perm) {
        // allow most stuff, but limit as appropriate
        if (perm instanceof RuntimePermission) {
            if (perm.getName().equals("setIO")) {
                if (!allowSetIO)
                    // is this right or should we really restrict this more?
                    super.checkPermission(new java.lang.RuntimePermission("setIO"));
            } else if (perm.getName().equals("exitVM")) {
                checkExit(0);
            } else if (perm.getName().equals("createSecurityManager")) {
                //super.checkPermission(new java.lang.RuntimePermission("createSecurityManager"));
            } else if (perm.getName().equals("setSecurityManager")) {
                if (!allowSetSecurityManager)
                    forbid(perm);
                //super.checkPermission(new java.lang.RuntimePermission("setSecurityManager"));
                // if the security manager is changed, we have no way of tracking whether
                // properties get modified, so we have to assume they may
                propertiesModified = true;
            }
        }
        else if (perm instanceof PropertyPermission) {
            //super.checkPermission(perm);
            if (((PropertyPermission) (perm)).getActions().contains("write")) {
                propertiesModified = true;
            }
        }
    }

    void forbid(Permission perm) throws SecurityException {
        if (verbose) {
            System.err.println(getClass().getName() + ": " + perm);
            Thread.dumpStack();
        }
        throw new SecurityException("Action forbidden by jtreg: " + perm.getName());
    }

    private boolean propertiesModified;

    @Override
    public synchronized void checkPropertiesAccess() {
        super.checkPropertiesAccess();
        propertiesModified = true;
    }

    boolean isPropertiesModified() {
        return propertiesModified;
    }

    @Override
    public boolean setAllowPropertiesAccess(boolean b) {
        return super.setAllowPropertiesAccess(b);

    }

    void resetPropertiesModified() {
        propertiesModified = false;
    }

    public boolean setAllowSetIO(boolean bool) {
        boolean prev = allowSetIO;
        allowSetIO = bool;
        return prev;
    }

    private boolean allowSetIO = false;

    public boolean setAllowSetSecurityManager(boolean bool) {
        boolean prev = allowSetSecurityManager;
        allowSetSecurityManager = bool;
        return prev;
    }

    private boolean allowSetSecurityManager = false;


    static private boolean verbose =
        Boolean.getBoolean("javatest.security.verbose");
}
