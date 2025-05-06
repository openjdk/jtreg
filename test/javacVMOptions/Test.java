/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @compile Test.java
 * @compile -J-DmyProperty=123 -processor Test -proc:only Test.java
 * @compile Test.java -XDrawDiagnostic
 */

import java.util.Objects;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

public class Test extends AbstractProcessor {
    ProcessingEnvironment pEnv;

    public void init(ProcessingEnvironment pEnv) {
        this.pEnv = pEnv;
    }

    public boolean process(Set<? extends TypeElement> elems, RoundEnvironment rEnv) {
        String myProperty = System.getProperty("myProperty");
        System.out.println("myProperty: " + myProperty);
        checkEqual(myProperty, "123");
        return true;
    }

    private void checkEqual(String found, String expect) {
        if (!Objects.equals(found, expect)) {
            System.err.println("Error: mismatch");
            System.err.println("  Expect: " + expect);
            System.err.println("  Found:  " + found);
            pEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "Command line not as expected.");
        }
    }
}

