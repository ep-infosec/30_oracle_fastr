/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.test.engine.interop;

import com.oracle.truffle.r.test.generate.FastRSession;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class RInteropSimpleTest {

    protected static Context context;

    @BeforeClass
    public static void before() {
        context = FastRSession.getContextBuilder("R", "llvm").build();
        context.eval("R", "1"); // initialize context
        context.enter();
    }

    @AfterClass
    public static void after() {
        context.leave();
        context.close();
    }

    @Test
    public void testVector() throws Exception {
        // R vectors behave like polyglot arrays

        ////////////////
        // read
        ////////////////
        Value vector = evalR("c(1L, 2L, 3L)");
        assertTrue(vector.hasArrayElements());
        assertEquals(3, vector.getArraySize());
        assertTrue(vector.getArrayElement(0).fitsInInt());
        assertEquals(1, vector.getArrayElement(0).asInt());
        assertTrue(vector.getArrayElement(2).fitsInInt());
        assertEquals(3, vector.getArrayElement(2).asInt());

        // non scalar vectors cannot be unboxed
        assertFalse(vector.fitsInInt());

        // scalar vectors can be directly unboxed
        Value scalarVector = evalR("c(123L)");
        assertTrue(scalarVector.fitsInInt());
        assertEquals(123, scalarVector.asInt());

        scalarVector = evalR("123L");
        assertTrue(scalarVector.fitsInInt());
        assertEquals(123, scalarVector.asInt());

        // but scalar vectors also behave like a polyglot array with size 1
        scalarVector = evalR("c(123L)");
        assertTrue(scalarVector.hasArrayElements());
        assertEquals(1, scalarVector.getArraySize());
        assertEquals(123, scalarVector.getArrayElement(0).asInt());

        // a scalar vector that contains NA is Null
        scalarVector = evalR("c(NA_integer_)");
        assertTrue(scalarVector.isNull());
        assertTrue(scalarVector.getArrayElement(0).isNull());

        ////////////////
        // write
        ////////////////
        vector = evalR("c(1L, 2L, 3L)");
        try {
            // writing vector elements isn't possible
            vector.setArrayElement(0, 123);
            fail();
        } catch (UnsupportedOperationException e) {
        }

        ////////////////
        // remove
        ////////////////
        vector = evalR("c(1L, 2L, 3L)");
        try {
            // removing vector elements isn't possible
            vector.removeArrayElement(0);
            fail();
        } catch (UnsupportedOperationException e) {
        }

        ////////////////
        // NA values
        ////////////////
        vector = evalR("c(1L, NA, 3L)");
        assertTrue(vector.hasArrayElements());
        // R NA values are represented as Null
        assertTrue(vector.getArrayElement(1).isNull());

    }

    @Test
    public void testComplexVector() throws Exception {
        // R complex vectors have array elements like any other vector
        Value vector = evalR("c(1+1i, 2+1i, 3+1i)");
        assertTrue(vector.hasArrayElements());
        assertEquals(3, vector.getArraySize());

        // but the array elements can't be represented like a polyglot number
        // the real and imaginary parts are represented by the members 're' and 'im'
        assertFalse(vector.getArrayElement(0).isNumber());
        assertTrue(vector.getArrayElement(0).hasMembers());
        assertTrue(vector.getArrayElement(0).hasMember("re"));
        assertTrue(vector.getArrayElement(0).hasMember("im"));
        assertEquals(1, vector.getArrayElement(0).getMember("re").asDouble(), 0);
        assertEquals(1, vector.getArrayElement(0).getMember("im").asDouble(), 0);

        // a scalar complex vector is polyglot Null if at least its real or imaginary part are NA,
        // there are no members in such a case
        vector = evalR("complex(real=NA, imaginary=1)");
        assertTrue(vector.isNull());
        assertTrue(vector.getArrayElement(0).isNull());
        assertFalse(vector.getArrayElement(0).hasMembers());
        evalR("complex(real=1, imaginary=NA)");
        assertTrue(vector.isNull());
        assertTrue(vector.getArrayElement(0).isNull());
        assertTrue(vector.getArrayElement(0).isNull());
        assertFalse(vector.getArrayElement(0).hasMembers());
        evalR("complex(real=NA, imaginary=NA)");
        assertTrue(vector.isNull());
        assertTrue(vector.getArrayElement(0).isNull());
        assertFalse(vector.getArrayElement(0).hasMembers());
    }

    @Test
    public void testNull() throws Exception {
        Value nill = evalR("NULL");

        // null is null
        assertTrue(nill.isNull());
    }

    @Test
    public void testNULLvsNA() throws Exception {
        // both R NULL and NA are represented like a polyglot Null
        // but the R function 'is.null' returns true only for NULL, not NA
        Value risnull = evalR("is.null");

        Value nill = evalR("NULL");
        assertTrue(nill.isNull());
        assertTrue(risnull.execute(nill).asBoolean());

        Value na = evalR("NA");
        assertTrue(na.isNull());
        assertFalse(risnull.execute(na).asBoolean());

        // the R is.na function isn't that suitable together with NULL
        Value risna = evalR("is.na");
        assertTrue(risna.execute(na).asBoolean());

        assertFalse(risna.execute(nill).isBoolean());
        // because is.na(NULL) returns an empty logical vector
        assertEquals(0, risna.execute(nill).getArraySize());
    }

    @Test
    public void testList() throws Exception {
        // R lists behave like polyglot arrays,
        // but also can provide members

        ////////////////
        // read
        ////////////////

        // named or not named, list elements can always be accessed like array elements
        Value list = evalR("list(1L, 2L, 3L)");
        assertTrue(list.hasArrayElements());
        assertEquals(3, list.getArraySize());
        assertTrue(list.getArrayElement(0).fitsInInt());
        assertEquals(1, list.getArrayElement(0).asInt());
        assertTrue(list.getArrayElement(2).fitsInInt());
        assertEquals(3, list.getArrayElement(2).asInt());

        // named elements can also be accessed like members
        list = evalR("list(first=1L, 2L, third=c(1L, 2L, 3L))");
        assertTrue(list.hasMembers());
        assertTrue(list.hasMember("first"));
        assertTrue(list.hasMember("third"));
        assertTrue(list.getMember("first").fitsInInt());
        assertEquals(1, list.getArrayElement(0).asInt());

        assertTrue(list.getMember("third").hasArrayElements());
        assertTrue(list.getMember("third").getArrayElement(1).fitsInInt());
        assertEquals(2, list.getMember("third").getArrayElement(1).asInt());

        // note one difference to vectors
        Value vector = evalR("c(1L, 2L, 3L)");
        // a particular vector element is returned as a polyglot primitive
        assertFalse(vector.getArrayElement(0).hasArrayElements());
        assertEquals(1, vector.getArrayElement(0).asInt());
        // while a scalar list element is returned as a polyglot array
        list = evalR("list(first=1L, 2L, 3L)");
        assertTrue(list.getMember("first").hasArrayElements());
        assertEquals(1, list.getMember("first").getArraySize());
        assertEquals(1, list.getMember("first").getArrayElement(0).asInt());
        assertTrue(list.getArrayElement(0).hasArrayElements());
        assertEquals(1, list.getArrayElement(0).getArrayElement(0).asInt());

        ////////////////
        // write
        ////////////////
        list = evalR("list(1L, 2L, 3L)");
        try {
            // writing list elements isn't possible
            list.setArrayElement(0, 123);
            fail();
        } catch (UnsupportedOperationException e) {
        }
        try {
            // writing list members isn't possible
            list.putMember("firts", 666);
            fail();
        } catch (UnsupportedOperationException e) {
        }

        ////////////////
        // remove
        ////////////////
        list = evalR("list(1L, 2L, 3L)");
        try {
            // removing list elements isn't possible
            list.removeArrayElement(0);
            fail();
        } catch (UnsupportedOperationException e) {
        }
        list = evalR("list(first=1L, 2L, 3L)");
        try {
            // removing list members isn't possible
            list.removeMember("first");
            fail();
        } catch (UnsupportedOperationException e) {
        }

        ////////////////
        // invoke
        ////////////////
        list = evalR("list(1, 2, fn=function() {42})");
        // list elements which are functions can be invoked
        assertEquals(42, list.invokeMember("fn").asInt());
    }

    @Test
    public void testEnvironment() throws Exception {
        Value env = evalR("e <- new.env(); e$i <- 42L; e");

        // environment elemenents are accessed like members

        ////////////////
        // read
        ////////////////
        assertTrue(env.hasMembers());
        assertEquals(42, env.getMember("i").asInt());

        ////////////////
        // write
        ////////////////
        env.putMember("i", 43);
        assertEquals(43, env.getMember("i").asInt());

        // add a new element
        assertFalse(env.hasMember("iii"));
        env.putMember("iii", 442);
        assertEquals(442, env.getMember("iii").asInt());

        ////////////////
        // remove
        ////////////////
        env = evalR("e <- new.env(); e$i <- 42L; e");
        assertTrue(env.hasMember("i"));
        env.removeMember("i");
        assertFalse(env.hasMember("i"));

        ////////////////
        // environment locking
        ////////////////

        // enviroments elements can be locked
        env = evalR("e <- new.env(); e$i <- 42; lockBinding('i', e); e");
        try {
            env.putMember("i", 43);
            fail();
        } catch (UnsupportedOperationException e) {
        }
        try {
            env.removeMember("i");
            fail();
        } catch (UnsupportedOperationException e) {
        }

        // a whole enviroment can be locked and then no elements can be added or removed
        env = evalR("e <- new.env(); e$i <- 42; lockEnvironment(e); e");
        try {
            assertFalse(env.hasMember("newelemenet"));
            env.putMember("newelemenet", 43);
            fail();
        } catch (UnsupportedOperationException e) {
        }
        try {
            assertTrue(env.hasMember("i"));
            env.removeMember("i");
            fail();
        } catch (UnsupportedOperationException e) {
        }

        ////////////////
        // invoke
        ////////////////
        env = evalR("e <- new.env(); e$fn <- function(x) {x}; e");
        // environment elements which are functions can be invoked
        assertEquals(42, env.invokeMember("fn", 42).getArrayElement(0).asInt());
    }

    @Test
    public void testS4() throws Exception {
        Value env = evalR("setClass('test', representation(i = 'integer', fn = 'function'));" +
                        "new('test', i=42L, fn = function(x) {x})");

        // RS4Object elemenents are accessed like members

        ////////////////
        // read
        ////////////////
        assertTrue(env.hasMembers());
        assertTrue(env.hasMember("i"));
        assertEquals(42, env.getMember("i").asInt());

        ////////////////
        // write
        ////////////////
        env.putMember("i", 43);
        assertEquals(43, env.getMember("i").asInt());

        try {
            // adding new S4 members isn't possible
            env.putMember("nneeww", 42);
            fail();
        } catch (UnsupportedOperationException e) {
        }

        ////////////////
        // remove
        ////////////////
        try {
            // removing S4 members isn't possible
            env.removeMember("i");
            fail();
        } catch (UnsupportedOperationException e) {
        }

        ////////////////
        // invoke
        ////////////////
        // S4 elements which are functions can be invoked
        assertEquals(42, env.invokeMember("fn", 42).getArrayElement(0).asInt());
    }

    @Test
    public void testFunction() throws Exception {
        Value func = evalR("function(x) {x}");

        // functions can be executed
        assertTrue(func.canExecute());
        assertEquals(42, func.execute(42).getArrayElement(0).asInt());
    }

    @Test
    public void testToString() throws Exception {
        assertEquals("[1] 1", evalR("1").toString());
        assertEquals("[1] TRUE", evalR("TRUE").toString());
        assertEquals("[1] NA", evalR("NA").toString());
        // NA scalar value:
        assertEquals("NA", evalR("NA").getArrayElement(0).toString());
        // @formatter:off
        String dataFrameExpected =
                "  x y\n" +
                "1 1 a\n" +
                "2 2 b\n" +
                "3 3 c";
        // @formatter:on
        assertEquals(dataFrameExpected, evalR("data.frame(x = 1:3, y = c('a', 'b', 'c'))").toString());
    }

    @Test
    public void testArrayProxy() throws Exception {

        // How R treats ProxyArray or any array like polyglot values
        // Note: R has 1-based indexing, any indexing in R when applied to polyglot objects is
        // converted to 0-based

        // the semantics of R operations that normally update vectors are different for Truffle
        // Objects. Truffle Objects have reference semantics, i.e., changing one reference to the
        // object changes other references like with Java objects.
        Object[] array = new Object[]{1, 2, 4};
        ProxyArray proxyArray = ProxyArray.fromArray(array);
        Value fun = evalR("function(x) { z <- x; z[[1]] <- 42L; x[[1L]]; }");
        assertEquals(42, fun.execute(proxyArray).asInt());

        // if you want to have R semantics for your Truffle Object, you can use one of the as.{xyz}
        // R functions to lazily convert them to given R type. The result will behave exactly like
        // given R type but under the hood it will delegate to the proxy. In R vectors have value
        // semantics, so the change to "rx" in the example below is not going to change the
        // underlying Truffle Object but instead it makes a copy and updates that copy, like with a
        // normal R vector.
        array = new Object[]{1, 2, 4};
        proxyArray = ProxyArray.fromArray(array);
        fun = evalR("function(x) { rx <- as.integer(x); rx[[1L]] <- 42L; x[[1L]]; }");
        assertEquals(1, fun.execute(proxyArray).asInt());
        assertEquals("original array was not updated", 1, array[0]);
    }

    @Test
    public void testMapProxy() throws Exception {

        // How R treats MapProxy or any object like polyglot values

        // Such objects behave mostly like R lists:
        Map<String, Object> map = new HashMap<>();
        map.put("field1", 42);
        map.put("field2", "string");
        ProxyObject mapProxy = ProxyObject.fromMap(map);
        assertEquals(42, evalR("function(x) { x$field1 }").execute(mapProxy).asInt());
        assertEquals("string", evalR("function(x) { x$field2 }").execute(mapProxy).asString());

        // However, they have reference semantics, so updating one reference updates other
        // references like with objects in Java. See also the same principle in testArrayProxy
        assertEquals("other string", evalR("function(x) { y <- x; y$field1 <- 'other string'; x$field1 }").execute(mapProxy).asString());
        assertEquals("other string", ((Value) map.get("field1")).asString());

        // if you want to have R semantics for your Truffle Object, you can use one of the as.list
        // function. See also the same principle in testArrayProxy
        assertEquals("other string", evalR("function(x) { y <- as.list(x); y$field1 <- 33L; x$field1 }").execute(mapProxy).asString());
        assertEquals("original map was also not updated", "other string", ((Value) map.get("field1")).asString());
    }

    @Test
    public void testPolyglotBindings() throws Exception {

        // R also supports polyglot bindings via `import` and `export` built-ins

        Map<String, Object> map = new HashMap<>();
        UserPojo pojo = new UserPojo("Rehor", 42);
        map.put("java", pojo);
        context.getPolyglotBindings().putMember("myMap", ProxyObject.fromMap(map));

        // Import bindings exported by other languages or embedder
        Value value = evalR("map <- import('myMap'); map['r'] <- 'R writes to map'; map['java'];");
        assertEquals(pojo, value.as(UserPojo.class));
        assertEquals("R writes to map", ((Value) map.get("r")).asString());

        // Export R values to other languages or the embedder
        evalR("export('r_exports', list(foo = 'bar'))");
        assertEquals("bar", context.getPolyglotBindings().getMember("r_exports").getMember("foo").asString());
    }

    protected Value evalR(String srcTxt) throws Exception {
        return context.eval("R", srcTxt);
    }

    private static class UserPojo {
        @SuppressWarnings("unused") public String name;
        @SuppressWarnings("unused") public int age;

        UserPojo(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }
}
