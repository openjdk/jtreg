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

import com.sun.javatest.Status;
import com.sun.javatest.TestResult;

/**
 * This class implements the "ignore" action as described by the JDK tag
 * specification.
 *
 * @author Iris A Garcia
 * @see Action
 */
public class IgnoreAction extends Action
{
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
    public void init(String [][] opts, String [] args, String reason,
                     RegressionScript script)
        throws ParseException
    {
        this.script = script;
        this.reason = reason;

        if (opts.length != 0)
            throw new ParseException(IGNORE_UNEXPECT_OPTS);

        this.args = args;
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
        section = startAction("ignore", args, reason);

        Status status;
        if (script.isCheck())
            status = Status.passed(CHECK_PASS);
        else switch (script.getIgnoreKind()) {
            case QUIET:
                throw new IllegalStateException();
            case ERROR:
                if (args.length == 0)
                    status = Status.error(IGNORE_TEST_IGNORED);
                else
                    status = Status.error(IGNORE_TEST_IGNORED_C + StringArray.join(args));
                break;
            case RUN:
                if (args.length == 0)
                    status = Status.passed(IGNORE_TEST_SUPPRESSED);
                else
                    status = Status.passed(IGNORE_TEST_SUPPRESSED_C + StringArray.join(args));
                break;
            default:
                throw new IllegalArgumentException();
        }
        endAction(status, section);
        return status;
    } // run()

    //----------member variables------------------------------------------------

    private String [] args;

    private TestResult.Section section;
}
