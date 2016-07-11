/*
 * Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.javatest.util.I18NResourceBundle;

public class OptionDecoder {

    public OptionDecoder(List<Option> options) {
        for (Option o: options) {
            switch (o.argType) {
                case WILDCARD:
                    matchOptions.add(o);
                    break;

                case FILE:
                    fileOption = o;
                    break;

                case GNU:
                    for (String n: o.names) {
                        simpleOptions.put(n.toLowerCase(Locale.US), o);
                        if (n.matches("-[^-]")) {
                            matchOptions.add(o);
                        }
                    }
                    break;

                default:
                    for (String n: o.names) {
                        simpleOptions.put(n.toLowerCase(Locale.US), o);
                    }
            }
        }
    }

    public void decodeArgs(String[] args) throws BadArgs {
        decodeArgs(Arrays.asList(args));
    }

    public void decodeArgs(List<String> args) throws BadArgs {
        Iterator<String> iter = args.iterator();
        while (iter.hasNext()) {
            String arg = iter.next();
            if (arg.length() == 0)
                throw new BadArgs(i18n, "opt.empty");
            if (!arg.startsWith("-"))
                inFiles = true;
            if (inFiles) {
                fileOption.process(null, arg);
            } else
                decodeArg(arg, iter);
        }
    }

    /**
     *  opt ( [:=] value )
     *  0: all
     *  1: option name (before any separator)
     *  2: null or separator character
     *  3: null or value
     */
    Pattern optPattern = Pattern.compile("(-[-A-Za-z0-9/]+)(?:([:=])(.*))?");

    private void decodeArg(String arg, Iterator<String> iter) throws BadArgs {
        String name, sep, value;
        Matcher m = optPattern.matcher(arg);
        if (m.matches()) {
            name = m.group(1);
            sep = m.group(2);
            value = m.group(3);
        } else {
            name = arg;
            sep = null;
            value = null;
        }


        Option o = getOption(name);
        if (o == null) {
            throw new BadArgs(i18n, "opt.unknown", name);
        }

        switch (o.argType) {
            case NONE:      // -opt  (includes --opt -o)
                if (value != null)
                    throw new BadArgs(i18n, "opt.unexpected.value", arg);
                break;

            case GNU:       // --opt arg, --opt=arg, -o arg, -oarg
                if (sep == null) {
                    if (name.startsWith("--") || name.length() == 2) {
                        if (iter.hasNext()) {
                            value = iter.next();
                        } else {
                            throw new BadArgs(i18n, "opt.missing.value", arg);
                        }
                    } else {
                        value = arg.substring(2);
                    }
                } else if (!(name.startsWith("--") && sep.equals("="))) {
                        throw new BadArgs(i18n, "opt.bad.format", arg);
                }
                break;

            case STD:       // -opt:arg
                if (value == null)
                    throw new BadArgs(i18n, "opt.missing.value", arg);
                if (sep != null && !sep.equals(":"))
                    throw new BadArgs(i18n, "opt.bad.format", arg);
                break;

            case SEP:       // -opt arg
                if (value != null)
                    throw new BadArgs(i18n, "opt.bad.format", arg);
                if (iter.hasNext())
                    value = iter.next();
                else
                    throw new BadArgs(i18n, "opt.missing.value", arg);
                break;

            case OLD:       // -opt:arg or -opt arg
                if (value == null && iter.hasNext()) {
                    // warn against old style usage, or just accept it?
                    value = iter.next();
                }
                if (value == null)
                    throw new BadArgs(i18n, "opt.missing.value", arg);
                if (sep != null && !sep.equals(":"))
                    throw new BadArgs(i18n, "opt.bad.format", arg);
                break;

            case OPT:       // -opt or -opt:arg
                if (sep != null && !sep.equals(":"))
                    throw new BadArgs(i18n, "opt.bad.format", arg);
                break;

            case WILDCARD:  // -optarg
                // ignore sep and value
                value = o.getValue(arg);
                break;

            case REST:      // -opt rest,    allow opt:value rest
                value = (value == null ? "" : value + " ")
                        + join(iter, " ");
        }

        checkConflicts(o, name);

        if (debugOptions)
            System.err.println("OptionDecoder.decodeArg: " + name + " " + value);

        o.process(arg, value);
    }

    public void addFile(File file) throws BadArgs {
        fileOption.process(null, file.getPath());
    }

    public void addFile(String path) throws BadArgs {
        fileOption.process(null, path);
    }

    protected Option getOption(String name) {

        Option s = simpleOptions.get(name.toLowerCase());
        if (s != null)
            return s;

        for (Option m: matchOptions) {
            if (m.matches(name))
                return m;
        }

        return null;
    }

    protected void checkConflicts(Option o, String name) throws BadArgs {
        if (o.lockName != null) {
            String prev = locks.get(o.lockName);
            if (prev != null) {
                if (prev.equals(name))
                    throw new BadArgs(i18n, "opt.duplicate", name);
                else
                    throw new BadArgs(i18n, "opt.conflict", prev, name);
            }
            locks.put(o.lockName, name);
        }
    }

    private static String join(Iterator<?> iter, String sep) {
        StringBuilder sb = new StringBuilder();
        while (iter.hasNext()) {
            if (sb.length() > 0)
                sb.append(" ");
            sb.append(iter.next());
        }
        return sb.toString();
    }


    private Map<String, Option> simpleOptions = new HashMap<String, Option>();
    private List<Option> matchOptions = new ArrayList<Option>();
    private Option fileOption;

    private Map<String, String> locks = new HashMap<String, String>();
    private boolean inFiles;

    protected static boolean debugOptions = Boolean.getBoolean("javatest.regtest.debugOptions");
    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(Main.class);
}
