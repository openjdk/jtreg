/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/** JDK 1.1 factory for normalized status objects. */
public class RStatus {

    private RStatus() { }

    static Status passed(String msg) {
        return Status.passed(normalize(msg));
    }

    static Status failed(String msg) {
        return Status.failed(normalize(msg));
    }

    static Status error(String msg) {
        return Status.error(normalize(msg));
    }

    static Status createStatus(int code, String msg) {
        return new Status(code, normalize(msg));
    }

    static Status normalize(Status s) {
        return new Status(s.getType(), normalize(s.getReason()));
    }

    // equivalent to msg.trim().replaceAll("\\s+", " ");
    private static String normalize(String msg) {
        boolean ok = true;
        boolean prevIsWhite = false;
        for (int i = 0; ok && i < msg.length(); i++) {
            char ch = msg.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (prevIsWhite || ch != ' ' || i == 0) {
                    ok = false;
                    break;
                }
                prevIsWhite = true;
            } else {
                prevIsWhite = false;
            }
        }
        if (prevIsWhite)
            ok = false;
        if (ok)
            return msg;

        StringBuilder sb = new StringBuilder();
        boolean needWhite = false;
        for (int i = 0; i < msg.length(); i++) {
            char ch = msg.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (sb.length() > 0)
                    needWhite = true;
            } else {
                if (needWhite)
                    sb.append(' ');
                sb.append(ch);
                needWhite = false;
            }
        }
        return sb.toString();
    }
}
