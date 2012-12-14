/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javatest.TestDescription;
import com.sun.javatest.TestFilter;
import com.sun.javatest.util.DynamicArray;

/**
 * A test filter that supports observers.
 */
public class ObservableTestFilter extends TestFilter {
    private TestFilter delegate;
    private Observer[] observers = new Observer[0];

    ObservableTestFilter(TestFilter delegate) {
        if (delegate == null)
            throw new NullPointerException();
        this.delegate = delegate;
    }

    void addObserver(Observer o) {
        if (o == null)
            throw new NullPointerException();
        observers = (Observer[]) DynamicArray.append(observers, o);
    }

    void removeObserver(Observer o) {
        observers = (Observer[]) DynamicArray.remove(observers, o);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public String getReason() {
        return delegate.getReason();
    }

    @Override
    public boolean accepts(TestDescription td) throws Fault {
        if (delegate.accepts(td)) {
            return true;
        } else {
            // protect against removing observers during notification
            Observer[] stableObservers = observers;
            for (int i = stableObservers.length - 1; i >= 0; i--)
                stableObservers[i].rejected(td, this);
            return false;
        }
    }
}
