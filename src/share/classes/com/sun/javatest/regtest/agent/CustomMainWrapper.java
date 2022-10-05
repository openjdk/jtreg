package com.sun.javatest.regtest.agent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ThreadFactory;

public interface CustomMainWrapper {
    static CustomMainWrapper getInstance(String className) {
        return new CustomMainWrapper() {
            @Override
            public Thread createThread(ThreadGroup tg, Runnable task) {
                try {
                    return VirtualAPI.instance().factory(true).newThread(task);
                } catch (ExceptionInInitializerError e) {
                    // we are in driver mode
                    return new Thread(tg, task);
                }
            }
        };
    }

    Thread createThread(ThreadGroup tg, Runnable task);
}

class VirtualAPI {

    private MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();

    private ThreadFactory virtualThreadFactory;
    private ThreadFactory platformThreadFactory;


    VirtualAPI() {
        try {

            Class<?> vbuilderClass =Class.forName("java.lang.Thread$Builder$OfVirtual");
            Class<?> pbuilderClass =Class.forName("java.lang.Thread$Builder$OfPlatform");


            MethodType vofMT = MethodType.methodType(vbuilderClass);
            MethodType pofMT = MethodType.methodType(pbuilderClass);

            MethodHandle ofVirtualMH =  publicLookup.findStatic(Thread.class, "ofVirtual", vofMT);
            MethodHandle ofPlatformMH =  publicLookup.findStatic(Thread.class, "ofPlatform", pofMT);

            Object virtualBuilder = ofVirtualMH.invoke();
            Object platformBuilder = ofPlatformMH.invoke();

            MethodType factoryMT = MethodType.methodType(ThreadFactory.class);
            MethodHandle vfactoryMH =  publicLookup.findVirtual(vbuilderClass, "factory", factoryMT);
            MethodHandle pfactoryMH =  publicLookup.findVirtual(pbuilderClass, "factory", factoryMT);

            virtualThreadFactory = (ThreadFactory) vfactoryMH.invoke(virtualBuilder);
            platformThreadFactory = (ThreadFactory) pfactoryMH.invoke(platformBuilder);

        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static VirtualAPI instance = new VirtualAPI();

    public static VirtualAPI instance() {
        return instance;
    }

    public ThreadFactory factory(boolean isVirtual) {
        return isVirtual ? virtualThreadFactory : platformThreadFactory;
    }
}
