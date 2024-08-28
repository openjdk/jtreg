/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.testng.IConfigurationListener;
import org.testng.IMethodInstance;
import org.testng.IMethodInterceptor;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestNGListener;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.TestNG;
import org.testng.TestNGException;
import org.testng.reporters.XMLReporter;

import static org.testng.ITestResult.FAILURE;
import static org.testng.ITestResult.SKIP;
import static org.testng.ITestResult.SUCCESS;
import static org.testng.ITestResult.SUCCESS_PERCENTAGE_FAILURE;

/**
 * TestRunner to run TestNG tests.
 */
public class TestNGRunner implements MainActionHelper.TestRunner {

    public static void main(String... args) throws Exception {
        main(null, args);
    }

    public static void main(ClassLoader loader, String... args) throws Exception {
        if (args.length != 3) {
            throw new Error("wrong number of arguments");
        }
        String testName = args[0];
        boolean mixedMode = Boolean.parseBoolean(args[1]);
        String moduleClassName = args[2];
        int sep = moduleClassName.indexOf('/');
        String moduleName = (sep == -1) ? null : moduleClassName.substring(0, sep);
        String className = (sep == -1) ? moduleClassName : moduleClassName.substring(sep + 1);
        //Class<?> mainClass = (loader == null) ? Class.forName(className) : loader.loadClass(className);
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
            cl = TestNGRunner.class.getClassLoader();
        }
        Class<?> mainClass = Class.forName(className, false, cl);
        String testQuery = System.getProperty("test.query");
        RegressionListener listener = new RegressionListener();
        TestNG testng = new TestNG(false);
        testng.setMixed(mixedMode);
        testng.setDefaultSuiteName(testName);
        testng.setTestClasses(new Class<?>[]{mainClass});
        if (testQuery != null) {
            testng.setMethodInterceptor(new FilterMethods(className, testQuery));
        }
        testng.addListener((ITestNGListener) listener); // recognizes both ITestListener and IConfigurationListener
        testng.addListener(new XMLReporter());
        testng.setOutputDirectory(new File(".").getPath()); // current dir, i.e. scratch dir
        testng.run();
        int configFailureCount = listener.configFailureCount.get();
        int testFailureCount = listener.failureCount.get();
        if (configFailureCount > 0 || testFailureCount > 0) {
            throw new Exception("config failures: " + configFailureCount
                    + ", test failures: " + testFailureCount);
        }
    }

    public static class RegressionListener
            implements ITestListener, IConfigurationListener {
        enum InfoKind { CONFIG, TEST }

        private final AtomicInteger count = new AtomicInteger();
        private final AtomicInteger successCount = new AtomicInteger();
        private final AtomicInteger failureCount = new AtomicInteger();
        private final AtomicInteger skippedCount = new AtomicInteger();
        private final AtomicInteger configSuccessCount = new AtomicInteger();
        private final AtomicInteger configFailureCount = new AtomicInteger();
        private final AtomicInteger configSkippedCount = new AtomicInteger();
        private final AtomicInteger failedButWithinSuccessPercentageCount = new AtomicInteger();
        // keeps track of the test start time for each test
        private final Map<ITestResult, Long> startTimeNanos =
                Collections.synchronizedMap(new IdentityHashMap<>());

        @Override
        public void onTestStart(ITestResult itr) {
            count.incrementAndGet();
            // Although testng itself provides getStartMillis() and getEndMillis()
            // on ITestResult for duration tracking, the testng implementation uses
            // System.currentTimeMillis(). We instead prefer using System.nanoTime() API
            // for duration tracking.
            startTimeNanos.put(itr, System.nanoTime());
        }

        @Override
        public void onTestSuccess(ITestResult itr) {
            successCount.incrementAndGet();
            report(InfoKind.TEST, itr);
        }

        @Override
        public void onTestFailure(ITestResult itr) {
            failureCount.incrementAndGet();
            report(InfoKind.TEST, itr);
        }

        @Override
        public void onTestSkipped(ITestResult itr) {
            Throwable t = itr.getThrowable();
            if (t != null && !(t instanceof SkipException)) {
                onTestFailure(itr);
                return;
            }
            skippedCount.incrementAndGet();
            report(InfoKind.TEST, itr);
        }

        @Override
        public void onTestFailedButWithinSuccessPercentage(ITestResult itr) {
            failedButWithinSuccessPercentageCount.incrementAndGet();
            report(InfoKind.TEST, itr);
        }

        @Override
        public void onStart(ITestContext itc) {
        }

        @Override
        public void onFinish(ITestContext itc) {
        }

        @Override
        public void onConfigurationSuccess(ITestResult itr) {
            configSuccessCount.incrementAndGet();
            report(InfoKind.CONFIG, itr);
        }

        @Override
        public void onConfigurationFailure(ITestResult itr) {
            configFailureCount.incrementAndGet();
            report(InfoKind.CONFIG, itr);
        }

        @Override
        public void onConfigurationSkip(ITestResult itr) {
            configSkippedCount.incrementAndGet();
            report(InfoKind.CONFIG, itr);
        }

        void report(InfoKind k, ITestResult itr) {
            Throwable t = itr.getThrowable();
            String suffix;
            if (t != null  && itr.getStatus() != SUCCESS) {
                // combine in stack trace so we can issue single println
                // threading may otherwise result in interleaved output
                StringWriter trace = new StringWriter();
                try (PrintWriter pw = new PrintWriter(trace)) {
                    t.printStackTrace(pw);
                }

                suffix = "\n" + trace;
            } else {
                suffix = "\n";
            }
            Long startNanos = startTimeNanos.remove(itr);
            Duration duration = startNanos == null
                    ? Duration.ZERO
                    : Duration.ofNanos(System.nanoTime() - startNanos);
            long durationMillis = duration.toMillis();
            System.out.print(k.toString().toLowerCase()
                    + " " + itr.getMethod().getConstructorOrMethod().getDeclaringClass().getName()
                    + "." + itr.getMethod().getMethodName()
                    + formatParams(itr)
                        + ": " + statusToString(itr.getStatus())
                        + " [" + durationMillis + "ms]"
                        + suffix);
        }

        private String formatParams(ITestResult itr) {
            StringBuilder sb = new StringBuilder(80);
            sb.append('(');
            String sep = "";
            for(Object arg : itr.getParameters()) {
                sb.append(sep);
                formatParam(sb, arg);
                sep = ", ";
            }
            sb.append(")");
            return sb.toString();
        }

        private void formatParam(StringBuilder sb, Object param) {
            if (param instanceof String) {
                sb.append('"');
                sb.append((String) param);
                sb.append('"');
            } else {
                String value = String.valueOf(param);
                if (value.length() > 30) {
                   sb.append(param.getClass().getName());
                   sb.append('@');
                   sb.append(Integer.toHexString(System.identityHashCode(param)));
                } else {
                   sb.append(value);
                }
            }
        }

        private String statusToString(int s) {
            switch (s) {
                case SUCCESS:
                    return "success";
                case FAILURE:
                    return "failure";
                case SKIP:
                    return "skip";
                case SUCCESS_PERCENTAGE_FAILURE:
                    return "success_percentage_failure";
                default:
                    return "??";
            }
        }
    }

    private static class FilterMethods implements IMethodInterceptor {

        private final String testClass;
        private final String testQuery;

        public FilterMethods(String testClass, String testQuery) {
            this.testClass = testClass;
            this.testQuery = testQuery;
        }

        @Override
        public List<IMethodInstance> intercept(List<IMethodInstance> ms, ITestContext c) {
            List<IMethodInstance> result =
                    ms.stream()
                      .filter(mi -> testQuery.equals(mi.getMethod()
                                                       .getMethodName()))
                      .collect(Collectors.toList());

            if (result.isEmpty()) {
                throw new TestNGException("Could not find method with name [" + testQuery + "] in class [" + testClass + "]");
            }

            return result;
        }
    }
}
