/*
 * Copyright 2015-2025 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

/*
 * Based on https://github.com/junit-team/junit-framework/commits/main/junit-jupiter-api/src/main/java/org/junit/jupiter/api/AssertionUtils.java
 */

package org.junit.jupiter.api;

import static java.util.stream.Collectors.joining;

import java.util.Deque;
import java.util.function.Supplier;

import org.junit.platform.commons.util.UnrecoverableExceptions;
import org.opentest4j.AssertionFailedError;

/**
 * {@code AssertionUtils} is a collection of utility methods that are common to
 * all assertion implementations.
 *
 * @since 5.0
 */
class AssertionUtils {

    private AssertionUtils() {
        /* no-op */
    }

    static void fail() {
        throw new AssertionFailedError();
    }

    static void fail(String message) {
        throw new AssertionFailedError(message);
    }

    static void fail(String message, Throwable cause) {
        throw new AssertionFailedError(message, cause);
    }

    static void fail(Throwable cause) {
        throw new AssertionFailedError(null, cause);
    }

    static void fail(Supplier<String> messageSupplier) {
        throw new AssertionFailedError(nullSafeGet(messageSupplier));
    }

    static String nullSafeGet(Supplier<String> messageSupplier) {
        return (messageSupplier != null ? messageSupplier.get() : null);
    }

    static String getCanonicalName(Class<?> clazz) {
        try {
            String canonicalName = clazz.getCanonicalName();
            return (canonicalName != null ? canonicalName : clazz.getTypeName());
        }
        catch (Throwable t) {
            UnrecoverableExceptions.rethrowIfUnrecoverable(t);
            return clazz.getTypeName();
        }
    }

    static String formatIndexes(Deque<Integer> indexes) {
        if (indexes == null || indexes.isEmpty()) {
            return "";
        }
        String indexesString = indexes.stream().map(Object::toString).collect(joining("][", "[", "]"));
        return " at index " + indexesString;
    }

    static boolean floatsAreEqual(float value1, float value2, float delta) {
        assertValidDelta(delta);
        return floatsAreEqual(value1, value2) || Math.abs(value1 - value2) <= delta;
    }

    static void assertValidDelta(float delta) {
        if (Float.isNaN(delta) || delta < 0.0) {
            failIllegalDelta(String.valueOf(delta));
        }
    }

    static void assertValidDelta(double delta) {
        if (Double.isNaN(delta) || delta < 0.0) {
            failIllegalDelta(String.valueOf(delta));
        }
    }

    static boolean floatsAreEqual(float value1, float value2) {
        return Float.floatToIntBits(value1) == Float.floatToIntBits(value2);
    }

    static boolean doublesAreEqual(double value1, double value2, double delta) {
        assertValidDelta(delta);
        return doublesAreEqual(value1, value2) || Math.abs(value1 - value2) <= delta;
    }

    static boolean doublesAreEqual(double value1, double value2) {
        return Double.doubleToLongBits(value1) == Double.doubleToLongBits(value2);
    }

    static boolean objectsAreEqual(Object obj1, Object obj2) {
        if (obj1 == null) {
            return (obj2 == null);
        }
        if (obj1.getClass().isArray() && (obj2 != null && obj2.getClass().isArray())) {
            throw new AssertionError("Should have used `assertArrayEquals()`?!");
        }
        return obj1.equals(obj2);
    }

    private static void failIllegalDelta(String delta) {
        fail("positive delta expected but was: <" + delta + ">");
    }

}
