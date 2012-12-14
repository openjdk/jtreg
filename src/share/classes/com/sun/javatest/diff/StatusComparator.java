/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.javatest.diff;

import java.util.Comparator;
import com.sun.javatest.TestResult;
import com.sun.javatest.Status;

/**
 * A comparator for the status contained in a test result.
 */
public class StatusComparator implements Comparator<TestResult> {

    /** Creates a new instance of StatusComparator */
    public StatusComparator() {
    }

    /** Creates a new instance of StatusComparator */
    public StatusComparator(boolean includeReason) {
        this.includeReason = includeReason;
    }

    public int compare(TestResult o1, TestResult o2) {
        int t1 = getType(o1);
        int t2 = getType(o2);

        if (t1 < t2)
            return -1;

        if (t1 > t2)
            return +1;

        if (!includeReason)
            return 0;

        String r1 = getReason(o1);
        String r2 = getReason(o2);
        return r1.compareTo(r2);
    }

    private static int getType(TestResult tr) {
        if (tr == null)
            return Status.NOT_RUN;
        Status s = tr.getStatus();
        return (s == null ? Status.NOT_RUN : s.getType());
    }

    private static String getReason(TestResult tr) {
        if (tr == null)
            return "";
        Status s = tr.getStatus();
        return (s == null ? "" : s.getReason());
    }

    private boolean includeReason;
}
