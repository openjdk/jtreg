package com.sun.javatest.regtest.config;

import com.sun.javatest.*;

import java.io.File;

public class CrashOnlyTestSuite extends RegressionTestSuite {

    /**
     * Creates a {@code RegressionTestSuite} object for the test suite identified by a given path.
     *
     * @param testSuiteRoot the root directory of the test suite
     * @param errHandler    a handler that can be used to report any problems encountered by
     *                      the test suite's test handler
     * @throws Fault if there are problems reading the {@code TEST.ROOT} file.
     */
    public CrashOnlyTestSuite(File testSuiteRoot, TestFinder.ErrorHandler errHandler) throws Fault {
        super(testSuiteRoot, errHandler);
    }

    @Override
    public TestRunner createTestRunner() {
        return new CrashOnlyTestRunner();
    }

    public interface ParametersFactory {
        CrashOnlyParametersImpl create(com.sun.javatest.CrashOnlyTestSuite ts) throws TestSuite.Fault;
    }

    /**public class ParametersFactory {
        @Override
        CrashOnlyParametersImpl create(RegressionTestSuite ts) throws TestSuite.Fault;
    }

    private static RegressionTestSuite.ParametersFactory factory;

    @Override
    public RegressionParameters createInterview() throws TestSuite.Fault {
        try {
            return (factory != null) ? factory.create(this) // expected case
                    : new RegressionParameters("regtest", this, System.err::println); // fallback
        } catch (InterviewParameters.Fault e) {
            throw new TestSuite.Fault(i18n, "suite.cantCreateInterview", e.getMessage());
        }
    }*/
}