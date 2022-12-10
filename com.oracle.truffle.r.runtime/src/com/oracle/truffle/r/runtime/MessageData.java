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
package com.oracle.truffle.r.runtime;

import com.oracle.truffle.r.runtime.RError.Message;

/**
 * Value type that holds data necessary for error/warning message from a cast pipeline.
 */
public final class MessageData {
    private final RError.Message message;
    private final Object[] messageArgs;

    public MessageData(Message message, Object... messageArgs) {
        this.message = message;
        this.messageArgs = messageArgs;
        assert message != null;
    }

    public Message getMessage() {
        return message;
    }

    public Object[] getMessageArgs() {
        return messageArgs;
    }

    /**
     * Helper method for operation that is often performed with {@link MessageData}.
     */
    public static MessageData getFirstNonNull(MessageData... messages) {
        for (MessageData message : messages) {
            if (message != null) {
                return message;
            }
        }
        throw RInternalError.shouldNotReachHere("at least the last message must not be null");
    }
}
