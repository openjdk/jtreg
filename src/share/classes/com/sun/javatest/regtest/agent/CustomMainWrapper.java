package com.sun.javatest.regtest.agent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;

public interface CustomMainWrapper {
    static CustomMainWrapper getInstance(String className) {
        try {
            Class<? extends CustomMainWrapper> clz = Class.forName(className).asSubclass(CustomMainWrapper.class);
            Constructor<? extends CustomMainWrapper> ctor = clz.getDeclaredConstructor();
            return ctor.newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    Thread createThread(ThreadGroup tg, Runnable task);

    List<String> getAdditionalVMOpts();
}

class VirtualMainWrapper  implements CustomMainWrapper {
    private ThreadFactory factory;

    private List<String>vmOpts = new ArrayList<>();

    public VirtualMainWrapper() {
        System.setProperty("main.wrapper", "Virtual");
        vmOpts.add("--enable-preview");
        try {
            factory = VirtualAPI.instance().factory(true);
        } catch (ExceptionInInitializerError e) {
            // we are in driver mode
            factory = null;
        }

    }

    @Override
    public Thread createThread(ThreadGroup tg, Runnable task) {
        return factory == null ? new Thread(tg, task) : factory.newThread(task);
    }

    @Override
    public List<String> getAdditionalVMOpts() {
        return vmOpts;
    }
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
