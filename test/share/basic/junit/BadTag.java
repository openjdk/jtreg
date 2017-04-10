/*
 * Copyright (c) 1998, 2009, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Error: Parse Exception: No class provided for `junit'
 * @run junit
 */

/* @test
 * @summary Error: Parse Exception: Arguments to `manual' option not supported:  bad_arg
 * @run junit/manual=bad_arg BadTag
 */

/* @test
 * @summary Error: Parse Exception: Bad integer specification: bruno
 * @run junit/timeout=bruno BadTag
 */

/* @test
 * @summary Error: Parse Exception: Bad option for junit: bad_opt
 * @run junit/bad_opt BadTag
 */

/* @test
 * @summary Error: Parse Exception: -vmopt: vm option(s) found, need to specify /othervm
 * @run junit -vmopt BadTag
 */

/* @test
 * @summary Error: Parse Exception: No class provided for `junit'
 * @run junit/othervm -vmopt
 */

// NOTE: The following two tests should fail for different reasons when the test
// version of JDK is changed to JDK1.2.

/* @test
 * @summary Error: Parse Exception: Option not allowed using provided test JDK: secure
 * @run junit/secure=secure BadTag
 */

/* @test
 * @summary Error: Parse Exception: Option not allowed using provided test JDK: policy
 * @run junit/policy=strict.policy BadTag
 */
