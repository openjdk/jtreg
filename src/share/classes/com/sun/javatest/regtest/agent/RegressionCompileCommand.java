/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.agent;


import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

// Derived from com.sun.javatest.lib.JavaCompileCommand,

import static com.sun.javatest.regtest.agent.AStatus.*;

// This is primarily a cut-n-paste copy of com.sun.javatest.lib.JavaCompileCommand,
// that provides a way to map compiler exit codes to a status.

/**
 * Invoke a Java compiler via reflection.
 * The compiler is assumed to have a constructor and compile method
 * matching the following signature:
 * <pre>
 * public class COMPILER {
 *    public static int compile(String[] args, PrintWriter out);
 * }
 * </pre>
 * The command is primarily intended for (but not limited to) the
 * compiler javac supplied with JDK.
 */
public class RegressionCompileCommand
{
    /**
     * A stand-alone entry point for this command. An instance of this
     * command is created, and its <code>run</code> method invoked,
     * passing in the command line args and <code>System.out</code> and
     * <code>System.err</code> as the two streams.
     * @param args command line arguments for this command.
     * @see #run
     */
    public static void main(String[] args) {
        PrintWriter out = new PrintWriter(System.out);
        PrintWriter err = new PrintWriter(System.err);
        AStatus s;
        try {
            RegressionCompileCommand c = new RegressionCompileCommand();
            s = c.run(args, out, err);
        }
        finally {
            out.flush();
            err.flush();
        }
        s.exit();
    }


    /**
     * Invoke a specified compiler, or the default, javac.
     * If the first word in the <code>args</code> array is "-compiler"
     * the second is interpreted as the class name for the compiler to be
     * invoked, optionally preceded by a name for the compiler, separated
     * from the class name by a colon.  If no -compiler is specified,
     * the default is `javac:com.sun.tools.javac.Main'. If -compiler is specified
     * but no compiler name is given before the class name, the default name
     * will be `java ' followed by the classname. For example, `-compiler Main'
     * will result in the class name being `Main' and the compiler name being
     * `java Main'. After determining the class and compiler name,
     * an instance of the compiler class will be created, passing it a stream
     * using the <code>ref</code> parameter, and the name of the compiler.
     * Then the `compile' method will be invoked, passing it the remaining
     * values of the `args' parameter.  If the compile method returns true,
     * the result will be a status of `passed'; if it returns `false', the
     * result will be `failed'. If any problems arise, the result will be
     * a status of `error'.
     * @param args An optional specification for the compiler to be invoked,
     *          followed by arguments for the compiler's compile method.
     * @param log  Not used.
     * @param ref  Passed to the compiler that is invoked.
     * @return `passed' if the compilation is successful; `failed' if the
     *          compiler is invoked and errors are found in the file(s)
     *          being compiler; or `error' if some more serios problem arose
     *          that prevented the compiler performing its task.
     */
    public AStatus run(String[] args, PrintWriter log, PrintWriter ref) {

        if (args.length == 0)
            return error("No args supplied");

        String compilerClassName = null;
        String compilerName = null;
        String[] options = null;

        // If we find a '-' in the args, what comes before it are
        // options for this class and what comes after it are args
        // for the compiler class. If don't find a '-', there are no
        // options for this class, and everything is handed off to
        // the compiler class

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-")) {
                options = new String[i];
                System.arraycopy(args, 0, options, 0, options.length);
                args = shift(args, i+1);
                break;
            }
        }

        if (options != null) {
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals("-compiler")) {
                    if (i + 1 == options.length)
                        return error("No compiler specified after -compiler option");

                    String s = options[++i];
                    int colon = s.indexOf(":");
                    if (colon == -1) {
                        compilerClassName = s;
                        compilerName = "java " + s;
                    }
                    else {
                        compilerClassName = s.substring(colon + 1);
                        compilerName = s.substring(0, colon);
                    }
                }
                else if (options[i].equals("-verbose"))
                    verbose = true;
                else
                    return error("Unrecognized option: " + options[i]);
            }
        }

        this.log = log;

        try {

            ClassLoader loader = null;

            Class<?> compilerClass;
            if (compilerClassName != null) {
                compilerClass = getClass(loader, compilerClassName);
                if (compilerClass == null)
                    return error("Cannot find compiler: " + compilerClassName);
            }
            else {
                compilerName = "javac";
                compilerClass = getClass(loader, "com.sun.tools.javac.Main");  // JDK1.3+
                if (compilerClass == null)
                    return error("Cannot find compiler");
            }

            loader = null;

            Object[] compileMethodArgs;
            Method compileMethod = getMethod(compilerClass, "compile", // JDK1.4+
                                             new Class<?>[] { String[].class, PrintWriter.class });
            if (compileMethod != null)
                compileMethodArgs = new Object[] { args, ref };
            else {
                return error("Cannot find compile method for " + compilerClass.getName());
            }

            if (!Modifier.isStatic(compileMethod.getModifiers())){
                return error("compile method is not static");
            }

            Object result;
            try {
                result = compileMethod.invoke(null, compileMethodArgs);
            }
            catch (Throwable t) {
                t.printStackTrace(log);
                return error("Error invoking compiler");
            }

            // result might be a boolean (old javac) or an int (new javac)
            if (result instanceof Boolean) {
                return getStatus((Boolean) result);
            }
            else if (result instanceof Integer) {
                return getStatus((Integer) result);
            }
            else
                return error("Unexpected return value from compiler: " + result);
        }
        finally {
            log.flush();
            ref.flush();
        }
    }

    protected AStatus getStatus(boolean ok) {
        return (ok ? passed : failed);
    }

    protected AStatus getStatus(int exitCode) {
        return (exitCode == 0 ? passed : failed);
    }

    private Class<?> getClass(ClassLoader loader, String name) {
        try {
            return (loader == null ? Class.forName(name) : loader.loadClass(name));
        }
        catch (ClassNotFoundException e) {
            return null;
        }
    }

    private Method getMethod(Class<?> c, String name, Class<?>[] argTypes) {
        try {
            return c.getMethod(name, argTypes);
        }
        catch (NoSuchMethodException e) {
            return null;
        }
        catch (Throwable t) {
            if (verbose)
                t.printStackTrace(log);
            return null;
        }
    }

    private static String[] shift(String[] args, int n) {
        String[] newArgs = new String[args.length - n];
        System.arraycopy(args, n, newArgs, 0, newArgs.length);
        return newArgs;
    }

    public static boolean defaultVerbose = Boolean.getBoolean("javatest.JavaCompileCommand.verbose");
    private boolean verbose = defaultVerbose;
    private PrintWriter log;

    private static final AStatus passed = passed("Compilation successful");
    private static final AStatus failed = failed("Compilation failed");
}
