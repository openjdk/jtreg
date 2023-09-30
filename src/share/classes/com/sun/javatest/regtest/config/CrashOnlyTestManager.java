package com.sun.javatest.regtest.config;

import com.sun.javatest.TestFinder;

import java.io.PrintWriter;
import java.nio.file.Path;

public class CrashOnlyTestManager extends TestManager{

    public CrashOnlyTestManager(PrintWriter out, Path baseDir, TestFinder.ErrorHandler errHandler) {
        super(out, baseDir, errHandler);
    }
}
