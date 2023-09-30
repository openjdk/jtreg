package com.sun.javatest.regtest.config;

import com.sun.javatest.CrashOnlyParameters;
import com.sun.javatest.CrashOnlyTestSuite;
import com.sun.javatest.InterviewParameters;
import com.sun.javatest.TestSuite;

import java.util.function.Consumer;

public class CrashOnlyParametersImpl extends RegressionParameters implements CrashOnlyParameters
{

    /**
     * Creates an object to handle the parameters for a test run.
     *
     * @param tag       a string to identify the set of parameters
     * @param testSuite the test suite
     * @param logger    an object to which to write logging messages
     * @throws InterviewParameters.Fault if a problem occurs while creating this object
     */
    public CrashOnlyParametersImpl(String tag, RegressionTestSuite testSuite, Consumer<String> logger) throws Fault {
        super(tag, testSuite, logger);
    }

    public CrashOnlyParametersImpl(RegressionParameters regPar) throws Fault {
        super(regPar);
    }


    @Override
    public RegressionTestSuite getTestSuite() {
        return super.getTestSuite();
    }
}