/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

// ERRORS FROM JAVA.LANG
// derived from Error()

/*
 * @test Method.invoke() correctly throws java.lang.Error
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.Error
 */
/*
 * @test Method.invoke() correctly throws java.lang.ClassCircularityError
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.ClassCircularityError
 */
/*
 * @test Method.invoke() correctly throws java.lang.AbstractMethodError
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.AbstractMethodError
 */
/*
 * @test Method.invoke() correctly throws java.lang.ExceptionInInitializerError
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.ExceptionInInitializerError
 */
/*
 * @test Method.invoke() correctly throws java.lang.IllegalAccessError
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.IllegalAccessError
 */
/*
 * @test Method.invoke() correctly throws java.lang.IncompatibleClassChangeError
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.IncompatibleClassChangeError
 */
/*
 * @test Method.invoke() correctly throws java.lang.InstantiationError
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.InstantiationError
 */
/*
 * @test Method.invoke() correctly throws java.lang.InternalError
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.InternalError
 */
/*
 * @test Method.invoke() correctly throws java.lang.LinkageError
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.LinkageError
 */
/*
 * @test Method.invoke() correctly throws java.lang.NoClassDefFoundError
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.NoClassDefFoundError
 */
/*
 * @test Method.invoke() correctly throws java.lang.NoSuchMethodError
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.NoSuchMethodError
 */
/*
 * @test Method.invoke() correctly throws java.lang.OutOfMemoryError
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.OutOfMemoryError
 */
/*
 * @test Method.invoke() correctly throws java.lang.StackOverflowError
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.StackOverflowError
 */
/*
 * @test Method.invoke() correctly throws java.lang.UnknownError
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.UnknownError
 */
/*
 * @test Method.invoke() correctly throws java.lang.UnsatisfiedLinkError
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.UnsatisfiedLinkError
 */
/*
 * @test Method.invoke() correctly throws java.lang.VerifyError
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.VerifyError
 */


// derived from Exception()
/*
 * @test Method.invoke() correctly throws java.lang.Exception
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.Exception
 */
/*
 * @test Method.invoke() correctly throws java.lang.ArithmeticException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.ArithmeticException
 */
/*
 * @test Method.invoke() correctly throws java.lang.ArrayStoreException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.ArrayStoreException
 */
/*
 * @test Method.invoke() correctly throws java.lang.ClassCastException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.ClassCastException
 */
/*
 * @test Method.invoke() correctly throws java.lang.ClassNotFoundException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.ClassNotFoundException
 */
/*
 * @test Method.invoke() correctly throws java.lang.CloneNotSupportedException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.CloneNotSupportedException
 */
/*
 * @test Method.invoke() correctly throws java.lang.IllegalAccessException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.IllegalAccessException
 */
/*
 * @test Method.invoke() correctly throws java.lang.IllegalArgumentException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.IllegalArgumentException
 */
/*
 * @test Method.invoke() correctly throws java.lang.IllegalMonitroStateException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.IllegalMonitorStateException
 */
/*
 * @test Method.invoke() correctly throws java.lang.IllegalThreadStateException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.IllegalThreadStateException
 */
/*
 * @test Method.invoke() correctly throws java.lang.IndexOutOfBoundsException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.IndexOutOfBoundsException
 */
/*
 * @test Method.invoke() correctly throws java.lang.InstantiationException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.InstantiationException
 */
/*
 * @test Method.invoke() correctly throws java.lang.InterruptedException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.InterruptedException
 */
/*
 * @test Method.invoke() correctly throws java.lang.NegativeArraySizeException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.NegativeArraySizeException
 */
/*
 * @test Method.invoke() correctly throws java.lang.NullPointerException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.NullPointerException
 */
/*
 * @test Method.invoke() correctly throws java.lang.NumberFormatException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.NumberFormatException
 */
/*
 * @test Method.invoke() correctly throws java.lang.RuntimeException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.RuntimeException
 */
/*
 * @test Method.invoke() correctly throws java.lang.SecurityException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.lang.SecurityException
 */

// ERRORS FROM JAVA.IO

/*
 * @test Method.invoke() correctly throws java.io.IOException
 * @summary Passed: Execution failed as expected
 * @run main/fail Exceptions java.io.IOException
 */

import java.lang.reflect.Constructor;

public class Exceptions
{
    public static void main (String [] args) throws Throwable {
        Throwable obj;
        try {
            Class c = Class.forName(args[0]);
            Constructor cr = c.getDeclaredConstructor(new Class[] {String.class});
            obj = (Throwable) cr.newInstance(new Object[] {"Success creating exception"});
        } catch (Throwable e) {
            System.err.println("Couldn't create exception of type " + args[0]);
            e.printStackTrace();
            return;
        }
        throw obj;
    }
}
