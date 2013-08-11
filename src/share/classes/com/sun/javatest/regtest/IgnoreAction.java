/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javatest.Status;

import static com.sun.javatest.regtest.RStatus.*;

/**
 * This class implements the "ignore" action as described by the JDK tag
 * specification.
 *
 * @author Iris A Garcia
 * @see Action
 */
public class IgnoreAction extends Action
{
    public static final String NAME = "ignore";

    /**
     * {@inheritdoc}
     * @return "ignore"
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * This method does initial processing of the options and arguments for the
     * action.  Processing is determined by the requirements of run().
     *
     * Verify options are of length 0.
     *
     * @param opts The options for the action.
     * @param args The arguments for the actions.
     * @param reason Indication of why this action was invoked.
     * @param script The script.
     * @exception  ParseException If the options or arguments are not expected
     *             for the action or are improperly formated.
     */
    @Override
    public void init(String[][] opts, String[] args, String reason,
                     RegressionScript script)
        throws ParseException
    {
        super.init(opts, args, reason, script);

        if (opts.length != 0)
            throw new ParseException(IGNORE_UNEXPECT_OPTS);
    } // init()

    /**
     * The method that does the work of the action.  The necessary work for the
     * given action is defined by the tag specification.
     *
     * An automatic error which causes all subsquent actions to be ingored. All
     * arguments are joined as part of the returned error message.  Equivalent
     * to "echo <word>*".
     *
     * @return     The result of the action.
     * @exception  TestRunException If an unexpected error occurs while running
     *             the test.
     */
    public Status run() throws TestRunException {
        startAction();

        Status status;
        if (script.isCheck())
            status = passed(CHECK_PASS);
        else switch (script.getIgnoreKind()) {
            case QUIET:
                throw new IllegalStateException();
            case ERROR:
                recorder.exec("# @ignore: " + StringArray.join(args) + "\nexit 1");
                if (args.length == 0)
                    status = error(IGNORE_TEST_IGNORED);
                else
                    status = error(IGNORE_TEST_IGNORED_C + StringArray.join(args));
                break;
            case RUN:
                recorder.exec("# @ignore: " + StringArray.join(args) + " (suppressed)");
                if (args.length == 0)
                    status = passed(IGNORE_TEST_SUPPRESSED);
                else
                    status = passed(IGNORE_TEST_SUPPRESSED_C + StringArray.join(args));
                break;
            default:
                throw new IllegalArgumentException();
        }

        endAction(status);
        return status;
    } // run()

    //----------member variables------------------------------------------------
}
