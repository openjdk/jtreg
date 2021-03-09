/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.exec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sun.javatest.regtest.config.ParseException;

/**
 * This class implements the "driver" action, which is a variation of "main".
 *
 * @see MainAction
 */
public class DriverAction extends MainAction
{
    public static final String NAME = "driver";

    /**
     * {@inheritDoc}
     * @return "driver"
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * This method does initial processing of the options and arguments for the
     * action.  Processing is determined by the requirements of run().
     *
     * Verify arguments are not of length 0 and separate them into the options
     * to java, the classname, and the parameters to the named class.
     *
     * Verify that the options are valid for the "driver" action.
     *
     * @param opts The options for the action.
     * @param args The arguments for the actions.
     * @param reason Indication of why this action was invoked.
     * @param script The script.
     * @exception  ParseException If the options or arguments are not expected
     *             for the action or are improperly formated.
     */
    @Override
    public void init(Map<String,String> opts, List<String> args, String reason,
                     RegressionScript script)
        throws ParseException
    {
        if (args.isEmpty()) {
            throw new ParseException(DRIVER_NO_CLASSNAME);
        } else {
            String cn = args.get(0);
            if (cn.startsWith("-"))
                throw new ParseException(DRIVER_UNEXPECT_VMOPT);
        }

        for (Map.Entry<String,String> e: opts.entrySet()) {
            String optName  = e.getKey();
            if (optName.equals("fail")
                    || optName.equals("timeout"))
                continue;
            throw new ParseException(optName + " not supported");
        }

        super.init(opts, args, reason, script);
    } // init()

    @Override
    List<String> filterJavaOpts(List<String> args) {
        List<String> results = new ArrayList<>();
        int i = 0, n = args.size();
        while (i < n) {
            String arg = args.get(i);
            if (arg.startsWith("-D")) {
                results.add(arg);
            } else if (i < n - 1) {
                switch (arg) {
                    case "--module-path":
                    case "--add-modules":
                    case "--add-exports":
                    case "--add-opens":
                        results.add(arg);
                        results.add(args.get(++i));
                }
            }
            ++i;
        }
        return results;
    }
}
