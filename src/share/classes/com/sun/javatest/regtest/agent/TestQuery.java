/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestQuery {
    private final String className;
    private final String methodName;
    private final List<String> paramTypeNames;

    private TestQuery(String className, String methodName, List<String> paramTypeNames) {
        this.className = Objects.requireNonNull(className);
        this.methodName = methodName;
        this.paramTypeNames = paramTypeNames;
    }

    // <class name>[::<method name>[([<param type>[...,<param type>]])]]
    public static TestQuery parse(String queryString) {
        String[] parts = queryString.split("::", 2);
        String className = parts[0];
        String methodName = null;
        List<String> paramTypeNames = null;
        if (parts.length > 1) { // method name present
            parts = parts[1].split("\\(", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid query format: " + queryString);
            }
            methodName = parts[0];
            String types = parts[1].substring(0, parts[1].length() - 1); // drop trailing ')'
            if (types.isEmpty()) {
                paramTypeNames = Collections.emptyList();
            } else {
                String[] typeNames = types.split(",");
                paramTypeNames = Collections.unmodifiableList(Arrays.asList(typeNames));
            }
        }

        return new TestQuery(className, methodName, paramTypeNames);
    }

    public String className() {
        return className;
    }

    public Optional<String> methodName() {
        return Optional.ofNullable(methodName);
    }

    public Optional<List<String>> paramTypeNames() {
        return Optional.ofNullable(paramTypeNames);
    }

    @Override
    public String toString() {
        return className()
                + methodName().map(mn -> "::" + mn).orElse("")
                + paramTypeNames().map(pn -> '(' + String.join(",", pn) + ')').orElse("");
    }
}
