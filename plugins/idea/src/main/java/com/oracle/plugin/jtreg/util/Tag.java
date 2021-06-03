/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.plugin.jtreg.util;

/**
 * This class models a jtreg test tag. For a full specification of jtreg tags please refer to the following
 * document: {@link "http://openjdk.java.net/jtreg/tag-spec.html"}.
 */
public class Tag {
    private final int start;
    private final int end;
    private final int tagStart;
    private final int tagEnd;
    private final String name;
    private final String value;

    public Tag(int start, int end, int tagStart, int tagEnd, String name, String value) {
        this.start = start;
        this.end = end;
        this.tagStart = tagStart;
        this.tagEnd = tagEnd;
        this.name = name;
        this.value = value;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public int getTagStart() {
        return tagStart;
    }

    public int getTagEnd() {
        return tagEnd;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

}
