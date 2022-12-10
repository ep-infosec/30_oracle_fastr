/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.runtime.RVisibility.CUSTOM;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.MODIFIES_STATE;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.READS_STATE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.function.visibility.SetVisibilityNode;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RLocale;
import com.oracle.truffle.r.runtime.ROptions;
import com.oracle.truffle.r.runtime.ROptions.ContextStateImpl;
import com.oracle.truffle.r.runtime.ROptions.OptionsException;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RStringVector;

public class OptionsFunctions {

    @RBuiltin(name = "options", visibility = CUSTOM, kind = INTERNAL, parameterNames = {"..."}, behavior = MODIFIES_STATE)
    public abstract static class Options extends RBuiltinNode.Arg1 {

        @Child private SetVisibilityNode visibility = SetVisibilityNode.create();

        private final ConditionProfile argNameNull = ConditionProfile.createBinaryProfile();

        static {
            Casts.noCasts(Options.class);
        }

        @Specialization
        @TruffleBoundary
        protected RList options(@SuppressWarnings("unused") RMissing x) {
            Set<Map.Entry<String, Object>> optionSettings = getRContext().stateROptions.getValues();
            Object[] data = new Object[optionSettings.size()];
            String[] names = new String[data.length];

            ArrayList<Map.Entry<String, Object>> entries = new ArrayList<>(optionSettings);
            Locale locale = getRContext().stateRLocale.getLocale(RLocale.COLLATE);
            Collator collator = locale == Locale.ROOT || locale == null ? null : RLocale.getOrderCollator(locale);
            Collections.sort(entries, new Comparator<Map.Entry<String, Object>>() {
                @Override
                public int compare(Map.Entry<String, Object> o1, Map.Entry<String, Object> o2) {
                    return RLocale.compare(collator, o1.getKey(), o2.getKey());
                }
            });

            int i = 0;
            for (Map.Entry<String, Object> entry : entries) {
                names[i] = entry.getKey();
                data[i] = entry.getValue();
                i++;
            }
            return RDataFactory.createList(data, RDataFactory.createStringVector(names, RDataFactory.COMPLETE_VECTOR));
        }

        @Specialization(guards = "isMissing(args)")
        protected Object optionsMissing(VirtualFrame frame, @SuppressWarnings("unused") RArgsValuesAndNames args) {
            visibility.execute(frame, true);
            return options(RMissing.instance);
        }

        private static final class ResultWithVisibility {
            private final RList value;
            private final boolean visible;

            protected ResultWithVisibility(RList value, boolean visible) {
                this.value = value;
                this.visible = visible;
            }
        }

        @Specialization(guards = "!isMissing(args)")
        protected Object options(VirtualFrame frame, RArgsValuesAndNames args) {
            try {
                ResultWithVisibility result = optionsInternal(args);
                visibility.execute(frame, result.visible);
                return result.value;
            } catch (OptionsException ex) {
                throw error(ex);
            }
        }

        @TruffleBoundary
        private ResultWithVisibility optionsInternal(RArgsValuesAndNames args) throws OptionsException {
            boolean visible = true;
            ROptions.ContextStateImpl options = getRContext().stateROptions;
            Object[] values = args.getArguments();
            ArgumentsSignature signature = args.getSignature();
            ArrayList<Object> data = new ArrayList<>(values.length);
            ArrayList<String> names = new ArrayList<>(values.length);
            for (int i = 0; i < values.length; i++) {
                String argName = signature.getName(i);
                Object value = values[i];
                if (argNameNull.profile(argName == null)) {
                    // getting
                    String optionName = null;
                    if (value instanceof RStringVector) {
                        // ignore rest (cf GnuR)
                        optionName = ((RStringVector) value).getDataAt(0);
                    } else if (value instanceof String) {
                        optionName = (String) value;
                    } else if (value instanceof RList) {
                        // setting
                        // named lists are set the "as-is", which makes the options hierarchical
                        // not named list is un-listed and each value (with its name) is used as
                        // "top-level" option
                        handleUnnamedList(data, names, (RList) value, options);
                        // any settings means result is invisible
                        visible = false;
                    } else {
                        throw error(Message.INVALID_UNNAMED_ARGUMENT);
                    }
                    if (optionName != null) {
                        Object optionVal = options.getValue(optionName);
                        data.add(optionVal == null ? RNull.instance : optionVal);
                        names.add(optionName);
                    }
                } else {
                    // setting
                    Object previousVal = options.getValue(argName);
                    data.add(previousVal == null ? RNull.instance : previousVal);
                    names.add(argName);
                    options.setValue(argName, value);
                    // any settings means result is invisible
                    visible = false;
                }
            }
            RList result = RDataFactory.createList(data.toArray(), RDataFactory.createStringVector(names.toArray(new String[names.size()]), RDataFactory.COMPLETE_VECTOR));
            return new ResultWithVisibility(result, visible);
        }

        private static void handleUnnamedList(ArrayList<Object> data, ArrayList<String> names, RList list, ContextStateImpl options) throws OptionsException {
            RStringVector thisListnames;
            Object nn = list.getNames();
            if (nn instanceof RStringVector) {
                thisListnames = (RStringVector) nn;
            } else {
                throw RError.error(RError.SHOW_CALLER, Message.LIST_NO_VALID_NAMES);
            }
            for (int j = 0; j < list.getLength(); j++) {
                String name = thisListnames.getDataAt(j);
                Object previousVal = options.getValue(name);
                data.add(previousVal == null ? RNull.instance : previousVal);
                names.add(name);
                options.setValue(name, list.getDataAtAsObject(j));
            }
        }

        boolean isMissing(RArgsValuesAndNames args) {
            return args.isEmpty();    // length() == 1 && args.getValue(0) == RMissing.instance;
        }
    }

    @RBuiltin(name = "getOption", kind = INTERNAL, parameterNames = "x", behavior = READS_STATE)
    public abstract static class GetOption extends RBuiltinNode.Arg1 {

        static {
            Casts casts = new Casts(GetOption.class);
            casts.arg("x").defaultError(RError.Message.MUST_BE_STRING, "x").mustBe(stringValue()).asStringVector().findFirst();
        }

        @TruffleBoundary
        @Specialization
        protected Object getOption(RStringVector x) {
            if (x.getLength() != 1) {
                throw error(RError.Message.MUST_BE_STRING);
            }
            ROptions.ContextStateImpl options = getRContext().stateROptions;
            return options.getValue(x.getDataAt(0));
        }

        @Fallback
        protected Object getOption(@SuppressWarnings("unused") Object x) {
            throw error(RError.Message.MUST_BE_STRING);
        }
    }
}
