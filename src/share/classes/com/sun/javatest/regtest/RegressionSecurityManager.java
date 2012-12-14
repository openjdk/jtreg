/*
 * Copyright 2000-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

    static private boolean allowExec = true; // no overrides on this one; API control only


    public void checkPermission(Permission perm) {
        // allow most stuff, but limit as appropriate
        if (perm instanceof RuntimePermission) {
            if (perm.getName().equals("setIO")) {
                if (!allowSetIO)
                    // is this right or should we really restrict this more?
                    super.checkPermission(new java.lang.RuntimePermission("setIO"));
            }
            else if (perm.getName().equals("exitVM"))
                checkExit(0);
            else if (perm.getName().equals("createSecurityManager"))
                super.checkPermission(new java.lang.RuntimePermission("createSecurityManager"));
        }
        else if (perm instanceof PropertyPermission) {
            if (((PropertyPermission)(perm)).getActions().equals("read,write"))
                checkPropertiesAccess();
        }
    }

    private boolean propertiesAccessed;

    public synchronized void checkPropertiesAccess() {
        super.checkPropertiesAccess();
        propertiesAccessed = true;
    }

    boolean isPropertiesAccessed() {
        return propertiesAccessed;
    }

    void resetPropertiesAccessed() {
        propertiesAccessed = false;
    }

    public boolean setAllowSetIO(boolean bool) {
        boolean prev = allowSetIO;
        allowSetIO = bool;
        return prev;
    }

    static private boolean allowSetIO = false;


    static private boolean verbose =
        Boolean.getBoolean("javatest.security.verbose");
}
