/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javatest.diff;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A Map from a key to a possibly sparse array of values.
 */
public class MultiMap<K, V> implements Map<K, MultiMap.Entry<V>> {
    public static class Entry<V> {

        private Entry(MultiMap<?, ?> t) {
            table = t;
        }

        V get(int index) {
            return (index < list.size() ? list.get(index) : null);
        }

        int getSize() {
            return table.getColumns();
        }

        void put(int index, V value) {
            if (index >= table.getColumns())
                throw new IndexOutOfBoundsException();

            if (list == null)
                list = new ArrayList<V>(index);

            if (index < list.size())
                list.set(index, value);
            else {
                while (index > list.size())
                    list.add(null);
                list.add(value);
            }
        }

        boolean allEqual(Comparator<V> c) {
            if (list.size() == 0)
                return true;
            int size = table.getColumns();
            V v0 = list.get(0);
            for (int i = 1; i < size; i++) {
                V v = get(i);
                if (c.compare(v, v0) != 0)
                    return false;
            }
            return true;
        }

        private List<V> list;
        private MultiMap<?, ?> table;
    }

    /** Creates a new instance of MultiMap */
    public MultiMap() {
        names = new ArrayList<String>();
        map = new TreeMap<K, Entry<V>>();
    }

    int getColumns() {
        return names.size();
    }

    String getColumnName(int index) {
        return names.get(index);
    }

    int addColumn(String name) {
        names.add(name);
        return names.size() - 1;
    }

    void addColumn(String name, Map<K, V> map) {
        addColumn(name, map.entrySet());
    }

    void addColumn(String name, Iterable<Map.Entry<K, V>> iter) {
        int index = addColumn(name);
        for (Map.Entry<K, V> e: iter)
            addRow(index, e.getKey(), e.getValue());
    }

    void addRow(int index, K k, V v) {
        Entry<V> de = get(k);
        if (de == null)
            put(k, de = new Entry<V>(this));
        de.put(index, v);
    }

    public int size() {
        return map.size();
    }

    public Entry<V> get(Object path) {
        return map.get(path);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    public Entry<V> put(K key, Entry<V> value) {
        return map.put(key, value);
    }

    public Entry<V> remove(Object key) {
        return map.remove(key);
    }

    public void putAll(Map<? extends K, ? extends Entry<V>> t) {
        map.putAll(t);
    }

    public void clear() {
        map.clear();
    }

    public Set<Map.Entry<K, Entry<V>>> entrySet() {
        return map.entrySet();
    }

    public Set<K> keySet() {
        return map.keySet();
    }

    public Collection<Entry<V>> values() {
        return map.values();
    }

    private List<String> names;
    private TreeMap<K, Entry<V>> map;
}
