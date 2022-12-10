/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.nodes.unary;

import java.util.Arrays;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.r.runtime.MessageData;
import com.oracle.truffle.r.runtime.RError;

/**
 * Cast nodes behave like unary nodes, but in many cases it is useful to have a specific type for
 * casts.
 */
public abstract class CastNode extends UnaryNode {

    @CompilationFinal private static boolean isTesting = false;
    private static String lastWarning;

    private final ValueProfile classProfile = ValueProfile.createClassProfile();

    public static void testingMode() {
        isTesting = true;
    }

    public Object doCast(Object value) {
        return execute(classProfile.profile(value));
    }

    public abstract Object execute(Object value);

    /**
     * For testing purposes only, returns the last warning message (only when {@link #testingMode()}
     * was invoked before).
     */
    public static String getLastWarning() {
        return lastWarning;
    }

    public static void clearLastWarning() {
        lastWarning = null;
    }

    @SuppressWarnings({"unchecked"})
    private static Object[] substituteArgs(Object arg, MessageData message) {
        Object[] messageArgs = message.getMessageArgs();
        Object[] newMsgArgs = Arrays.copyOf(messageArgs, messageArgs.length);

        for (int i = 0; i < messageArgs.length; i++) {
            final Object msgArg = messageArgs[i];
            if (msgArg instanceof Function) {
                newMsgArgs[i] = ((Function<Object, Object>) msgArg).apply(arg);
            }
        }
        return newMsgArgs;
    }

    protected RuntimeException handleArgumentError(Object arg, MessageData message) {
        CompilerDirectives.transferToInterpreter();
        Object[] args = substituteArgs(arg, message);
        if (isTesting) {
            throw new IllegalArgumentException(String.format(message.getMessage().message, args));
        } else {
            throw RError.error(getErrorContext(), message.getMessage(), args);
        }
    }

    protected void handleArgumentWarning(Object arg, MessageData message) {
        CompilerDirectives.transferToInterpreter();
        if (message == null) {
            return;
        }
        Object[] args = substituteArgs(arg, message);
        if (isTesting) {
            lastWarning = String.format(message.getMessage().message, args);
        } else {
            // cannot use method in RBaseNode because of varargs
            RError.warning(getErrorContext(), message.getMessage(), args);
        }
    }
}
