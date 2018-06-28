/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes("*")
@SupportedOptions({"repLog", "repOut", "repErr"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class AnnoProc extends AbstractProcessor {
    private static final String lorem =
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor "
        + "incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis "
        + "nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. "
        + "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore "
        + "eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, "
        + "sunt in culpa qui officia deserunt mollit anim id est laborum.";
    public boolean process(Set<? extends TypeElement> elems, RoundEnvironment round) {
        for (int i = 0; i < getOpt("repLog"); i++) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, lorem);
        }
        for (int i = 0; i < getOpt("repOut"); i++) {
            System.out.println(lorem);
        }
        for (int i = 0; i < getOpt("repErr"); i++) {
            System.err.println(lorem);
        }
        return false;
    }

    private int getOpt(String name) {
        Map<String,String> options = processingEnv.getOptions();
        String value = options.get(name);
        return value == null ? 0 : Integer.valueOf(value);
    }
}
