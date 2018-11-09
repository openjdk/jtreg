/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.regtest.config;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.sun.javatest.TestDescription;
import com.sun.javatest.TestFilter;

/**
 * A test filter that caches its results.
 */
public abstract class CachingTestFilter extends TestFilter {
    private final String name;
    private final String description;
    private final String reason;

    public class Entry {
        public final TestDescription td;
        public final Boolean value;
        Entry(TestDescription td, Boolean v) {
            this.td = td;
            this.value = v;
        }
    }
    private final Map<String, Entry> cache = new HashMap<String, Entry>();

    /**
     * Creates a CachingTestFilter.
     *
     * @param name the name of this filter
     * @param description a description of this filter
     * @param reason the reason to give when not accepting a test
     */
    CachingTestFilter(String name, String description, String reason) {
        this.name = name;
        this.description = description;
        this.reason = reason;
    }

    /**
     * Returns the cache key to use for a test description.
     *
     * @param td the test description
     * @return the key to use when looking up values in the cache
     */
    protected abstract String getCacheKey(TestDescription td);

    /**
     * Returns the value to be used as the result the {@code accept} method.
     *
     * @param td the test description
     * @return the value
     */
    protected abstract boolean getCacheableValue(TestDescription td) throws Fault;

    /**
     * Returns the unmodifiable collection of entries in the cache.
     * @return the entries
     */
    public Collection<Entry> getCacheEntries() {
        return Collections.unmodifiableCollection(cache.values());
    }

    /**
     * Clears all entries from the cache.
     */
    public void clear() {
        cache.clear();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getReason() {
        return reason;
    }

    /**
     * Returns whether or not this filter accepts a test description.
     *
     * If there is not already a value for the test description in the cache,
     * the value will be determined by calling {@code getCacheableValue}.
     *
     * @param td the test description
     * @return whether or not this filter accepts the test description
     * @throws Fault if an error occurs.
     */
    @Override
    public final boolean accepts(TestDescription td) throws Fault {
        String key = getCacheKey(td);
        Entry e = cache.get(key);
        if (e == null) {
            cache.put(key, e = new Entry(td, getCacheableValue(td)));
        }
        return e.value;
    }
}
