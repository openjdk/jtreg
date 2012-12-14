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

/* @test
 * @summary Error: Parse Exception: No classname provided for `compile'
 * @compile
 */

/* @test
 * @summary Error: Parse Exception: Bad option for compile: bad_opt
 * @compile/bad_opt BadTag.java
 */

/* @test
 * @summary Error: Parse Exception: Bad integer specification: asdf
 * @compile/timeout=asdf BadTag.java
 */

/* @test
 * @summary Error: Parse Exception: No reference file name
 * @compile/ref= BadTag.java
 */

/* @test
 * @summary Error: Parse Exception: Can't find reference file: .../bad_ref
 * @compile/ref=bad_ref BadTag.java
 */


/* @test
 * @summary Error: Parse Exception: No classname provided for `compile'
 * @run compile
 */

/* @test
 * @summary Error: Parse Exception: Bad option for compile: bad_opt
 * @run compile/bad_opt BadTag.java
 */

/* @test
 * @summary Error: Parse Exception: Bad integer specification: asdf
 * @run compile/timeout=asdf BadTag.java
 */

/* @test
 * @summary Error: Parse Exception: No reference file name
 * @run compile/ref= BadTag.java
 */

/* @test
 * @summary Error: Parse Exception: Can't find reference file: .../bad_ref
 * @run compile/ref=bad_ref BadTag.java
 */

/* @test
 * @summary Error: Parse Exception: No classname ending with `.java' found
 * @run compile BadTag
 */
