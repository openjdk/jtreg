/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TestRunner to run JUnit tests.
 */
public class JUnitRunner implements MainActionHelper.TestRunner {
    // error message for when "NoClassDefFoundError" are raised accessing JUnit classes
    private static final String JUNIT_NO_DRIVER = "No JUnit driver -- install JUnit JAR file(s) next to jtreg.jar";
    // this is a temporary flag while transitioning from JUnit 4 to 5
    private static final boolean JUNIT_RUN_WITH_JUNIT_4 = Flags.get("runWithJUnit4");

    private static final String JUNIT_SELECT_PREFIX = "junit-select:";

    public static void main(String... args) throws Exception {
        main(null, args);
    }

    public static void main(ClassLoader loader, String... args) throws Exception {
        if (args.length != 2) {
            throw new Error("wrong number of arguments");
        }
        // String testName = args[0];  // not used
        String moduleClassName = args[1];
        int sep = moduleClassName.indexOf('/');
        String moduleName = (sep == -1) ? null : moduleClassName.substring(0, sep);
        String className = (sep == -1) ? moduleClassName : moduleClassName.substring(sep + 1);
        //            Class<?> mainClass = (loader == null) ? Class.forName(className) : loader.loadClass(className);
        ClassLoader cl;
        if (moduleName != null) {
            Class<?> layerClass;
            try {
                layerClass = Class.forName("java.lang.ModuleLayer");
            } catch (ClassNotFoundException e) {
                layerClass = Class.forName("java.lang.reflect.Layer");
            }
            Method bootMethod = layerClass.getMethod("boot");
            Object bootLayer = bootMethod.invoke(null);
            Method findLoaderMth = layerClass.getMethod("findLoader", String.class);
            cl = (ClassLoader) findLoaderMth.invoke(bootLayer, moduleName);
        } else if (loader != null) {
            cl = loader;
        } else {
            cl = JUnitRunner.class.getClassLoader();
        }
        Class<?> mainClass = Class.forName(className, false, cl);
        if (JUNIT_RUN_WITH_JUNIT_4) {
            runWithJUnit4(mainClass);
        } else {
            runWithJUnitPlatform(mainClass);
        }
    }

    private static void runWithJUnit4(Class<?> mainClass) throws Exception {
        org.junit.runner.Result result;
        try {
            result = org.junit.runner.JUnitCore.runClasses(mainClass);
        } catch (NoClassDefFoundError ex) {
            throw new Exception(JUNIT_NO_DRIVER, ex);
        }
        if (!result.wasSuccessful()) {
            for (org.junit.runner.notification.Failure failure : result.getFailures()) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                try {
                    pw.println("JavaTest Message: JUnit Failure: " + failure);
                    failure.getException().printStackTrace(pw);
                } finally {
                    pw.close();
                }
                System.err.println(sw.toString());
            }
            throw new Exception("JUnit test failure");
        }
    }

    private static void runWithJUnitPlatform(Class<?> mainClass) throws Exception {
        // https://junit.org/junit5/docs/current/user-guide/#launcher-api-execution
        Thread.currentThread().setContextClassLoader(mainClass.getClassLoader());
        try {
            String testQueryStr = System.getProperty("test.query");
            DiscoverySelector selector;
            if (testQueryStr != null && !testQueryStr.isEmpty()) {
                if (testQueryStr.startsWith(JUNIT_SELECT_PREFIX)) {
                    // https://junit.org/junit5/docs/current/user-guide/#running-tests-discovery-selectors
                    String selectorStr = testQueryStr.substring(JUNIT_SELECT_PREFIX.length());
                    selector = DiscoverySelectors.parse(selectorStr)
                            .orElseThrow(() -> new IllegalArgumentException("Selector can not be parsed: " + selectorStr));
                } else {
                    // legacy, assume method name
                    selector = DiscoverySelectors.selectMethod(mainClass, testQueryStr);
                }
            } else {
                selector = DiscoverySelectors.selectClass(mainClass);
            }
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(selector)
                    .build();

            SummaryGeneratingListener summaryGeneratingListener = new SummaryGeneratingListener();

            AgentVerbose verbose = AgentVerbose.ofStringRepresentation(System.getProperty("test.verbose"));

            LauncherConfig launcherConfig = LauncherConfig.builder()
                .addTestExecutionListeners(new PrintingListener(System.err, verbose))
                .addTestExecutionListeners(summaryGeneratingListener)
                .build();

            try (LauncherSession session = LauncherFactory.openSession(launcherConfig)) {
                session.getLauncher().execute(request);
            }

            TestExecutionSummary summary = summaryGeneratingListener.getSummary();
            System.err.println(summarize(summary));

            if (summary.getTotalFailureCount() > 0) {
                throw new Exception("JUnit test failure");
            }

        } catch (NoClassDefFoundError ex) {
            throw new Exception(JUNIT_NO_DRIVER, ex);
        }
    }

    static String summarize(TestExecutionSummary summary) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            if (summary.getTotalFailureCount() > 0) {
                pw.println("JavaTest Message: JUnit Platform Failure(s): " + summary.getTotalFailureCount());
            }

            // The format of the following output is assumed in the JUnit SummaryReporter
            pw.println();
            pw.print("[ JUnit Containers: ");
            pw.print("found " + summary.getContainersFoundCount());
            pw.print(", started " + summary.getContainersStartedCount());
            pw.print(", succeeded " + summary.getContainersSucceededCount());
            pw.print(", failed " + summary.getContainersFailedCount());
            pw.print(", aborted " + summary.getContainersAbortedCount());
            pw.print(", skipped " + summary.getContainersSkippedCount());
            pw.println("]");
            pw.print("[ JUnit Tests: ");
            pw.print("found " + summary.getTestsFoundCount());
            pw.print(", started " + summary.getTestsStartedCount());
            pw.print(", succeeded " + summary.getTestsSucceededCount());
            pw.print(", failed " + summary.getTestsFailedCount());
            pw.print(", aborted " + summary.getTestsAbortedCount());
            pw.print(", skipped " + summary.getTestsSkippedCount());
            pw.println("]");
        }
        return sw.toString();
    }

    static class PrintingListener implements TestExecutionListener {

        final PrintWriter printer;
        final Lock lock;
        final AgentVerbose verbose;
        final Map<UniqueId, Long> startNanosByUniqueId = new ConcurrentHashMap<>();

        PrintingListener(PrintStream stream, AgentVerbose verbose) {
            this(new PrintWriter(stream, true), verbose);
        }

        PrintingListener(PrintWriter printer, AgentVerbose verbose) {
            this.printer = printer;
            this.lock = new ReentrantLock();
            this.verbose = verbose;
        }

        @Override
        public void executionSkipped(TestIdentifier identifier, String reason) {
            if (verbose.passMode == AgentVerbose.Mode.NONE) return;
            if (identifier.isTest()) {
                String status = "SKIPPED";
                String source = toSourceString(identifier);
                String name = identifier.getDisplayName();
                lock.lock();
                try {
                    printer.printf("%-10s %s '%s' %s%n", status, source, name, reason);
                }
                finally {
                    lock.unlock();
                }
            }
        }

        @Override
        public void executionStarted(TestIdentifier identifier) {
            startNanosByUniqueId.put(identifier.getUniqueIdObject(), System.nanoTime());
            if (verbose.passMode == AgentVerbose.Mode.NONE) return;
            if (identifier.isTest()) {
                String status = "STARTED";
                String source = toSourceString(identifier);
                String name = identifier.getDisplayName();
                lock.lock();
                try {
                    printer.printf("%-10s %s '%s'%n", status, source, name);
                }
                finally {
                    lock.unlock();
                }
            }
        }

        @Override
        public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
            TestExecutionResult.Status status = result.getStatus();
            if (status == TestExecutionResult.Status.SUCCESSFUL) {
                if (verbose.passMode == AgentVerbose.Mode.NONE) return;
            }
            Long startNanos = startNanosByUniqueId.remove(identifier.getUniqueIdObject());
            Duration duration = startNanos == null
                    ? Duration.ZERO
                    : Duration.ofNanos(System.nanoTime() - startNanos);
            lock.lock();
            try {
                if (status == TestExecutionResult.Status.ABORTED) {
                    result.getThrowable().ifPresent(printer::println); // not the entire stack trace
                }
                if (status == TestExecutionResult.Status.FAILED) {
                    result.getThrowable().ifPresent(throwable -> throwable.printStackTrace(printer));
                }
                if (identifier.isTest()) {
                    String source = toSourceString(identifier);
                    String name = identifier.getDisplayName();
                    long millis = duration.toMillis();
                    printer.printf("%-10s %s '%s' [%dms]%n", status, source, name, millis);
                }
            }
            finally {
                lock.unlock();
            }
        }

        @Override
        public void reportingEntryPublished(TestIdentifier identifier, ReportEntry entry) {
            if (verbose.passMode == AgentVerbose.Mode.NONE) return;
            lock.lock();
            try {
                printer.println(identifier.getDisplayName() + " -> " + entry.getTimestamp());
                entry.getKeyValuePairs().forEach((key, value) -> printer.println(key + " -> " + value));
            }
            finally {
                lock.unlock();
            }
        }

        private static String toSourceString(TestIdentifier identifier) {
            Optional<TestSource> optionalTestSource = identifier.getSource();
            if (!optionalTestSource.isPresent()) return "<no test source>";
            TestSource testSource = optionalTestSource.get();
            if (testSource instanceof MethodSource) {
                MethodSource source = (MethodSource) testSource;
                return source.getClassName() + "::" + source.getMethodName();
            }
            return testSource.toString();
        }
    }
}
