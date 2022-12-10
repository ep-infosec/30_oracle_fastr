/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Set;
import java.util.WeakHashMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.runtime.RSrcref.SrcrefFields;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.env.REnvironment;

/**
 * A facade for the creation of Truffle {@link Source} objects, which is complicated in R due the
 * the many different ways in which sources can be created. Particularly tricky are sources created
 * from deparsing functions from (binary-form) packages and from the {@code source} builtin, as
 * these are presented as text strings despite being associated with files.
 *
 * We separate sources as <i>internal</i> or <i>external</i>, where the former will return
 * {@code true} to {@link Source#isInternal()}. External sources always correspond to some external
 * data source, e.g., file, url, even if they are not created using the standard methods in
 * {@link Source}. An internal source will always return {@code null} to {@link Source#getURI} but
 * {@link Source#getName} should return a value that indicates how/why the source was created, a
 * value from {@link Internal}.
 *
 */
public class RSource {
    /**
     * Collection of strings that are used to indicate {@link Source} instances that have internal
     * descriptions.
     */
    public enum Internal {

        UNIT_TEST("<unit_test>"),
        SHELL_INPUT("<shell_input>"),
        EXPRESSION_INPUT("<expression_input>"),
        GET_ECHO("<get_echo>"),
        GET_CONTINUE_PROMPT("<get_continue_prompt>"),
        QUIT_EOF("<<quit_eof>>"),
        STARTUP_SHUTDOWN("<startup/shutdown>"),
        REPL_WRAPPER("<repl wrapper>"),
        EVAL_WRAPPER("<eval wrapper>"),
        NO_SOURCE("<no source>"),
        CONTEXT_EVAL("<context_eval>"),
        RF_FINDFUN("<Rf_findfun>"),
        BROWSER_INPUT("<browser_input>"),
        CLEAR_WARNINGS("<clear_warnings>"),
        DEPARSE("<deparse>"),
        GET_CONTEXT("<get_context>"),
        DEBUGTEST_FACTORIAL("<factorial.r>"),
        DEBUGTEST_DEBUG("<debugtest.r>"),
        DEBUGTEST_EVAL("<evaltest.r>"),
        TCK_INIT("<tck_initialization>"),
        PACKAGE("<package:%s deparse>"),
        DEPARSE_ERROR("<package_deparse_error>"),
        LAPPLY("<lapply>"),
        R_PARSEVECTOR("<R_ParseVector>"),
        PAIRLIST_DEPARSE("<pairlist deparse>"),
        INIT_EMBEDDED("<init embedded>"),
        R_IMPL("<internal R code>");

        public final String string;

        Internal(String text) {
            this.string = text;
        }
    }

    /**
     * A weak map associating {@link Source} objects to corresponding origin (= {@link TruffleFile}
     * or {@link URL}, ...).
     */
    private static final WeakHashMap<Object, WeakReference<Source>> deserializedSources = new WeakHashMap<>();

    /**
     * Create an (external) source from the {@code text} that is known to originate from the file
     * system path {@code path}.
     */
    public static Source fromFileName(RContext context, String text, String path, boolean internal) throws URISyntaxException {
        TruffleFile file = context.getSafeTruffleFile(path).getAbsoluteFile();
        URI uri = new URI("file://" + file.getPath());
        return Source.newBuilder(RRuntime.R_LANGUAGE_ID, text, path).content(text).uri(uri).internal(internal).build();
    }

    /**
     * Create a cached source from {@code text} and {@code name}.
     */
    public static Source fromText(String text, String name) {
        return getCachedByOrigin(text, origin -> Source.newBuilder(RRuntime.R_LANGUAGE_ID, text, name).build());
    }

    /**
     * Create an {@code internal} source from {@code text} and {@code description}.
     */
    public static Source fromTextInternal(String text, Internal description) {
        return fromTextInternal(text, description, RRuntime.R_LANGUAGE_ID);
    }

    /**
     * Create an {@code internal} source from {@code text} and {@code description}.
     */
    public static Source fromTextInternalInvisible(String text, Internal description) {
        return fromTextInternalInvisible(text, description, RRuntime.R_LANGUAGE_ID);
    }

    /**
     * Create an {@code internal} source from {@code text} and {@code description} of given
     * {@code languageId}.
     */

    public static Source fromTextInternal(String text, Internal description, String languageId) {
        return Source.newBuilder(languageId, text, description.string).internal(true).interactive(true).build();
    }

    /**
     * Create an {@code internal} source from {@code text} and {@code description} of given
     * {@code languageId}.
     */

    public static Source fromTextInternalInvisible(String text, Internal description, String languageId) {
        return Source.newBuilder(languageId, text, description.string).internal(true).build();
    }

    /**
     * Create an {@code internal} source for a deparsed package from {@code text} and
     * {@code packageName}.
     */
    public static Source fromPackageTextInternal(String text, String packageName) {
        String name = String.format(Internal.PACKAGE.string, packageName);
        return Source.newBuilder(RRuntime.R_LANGUAGE_ID, text, name).build();
    }

    /**
     * Create an {@code internal} source for a deparsed package from {@code text} when the function
     * name might be known. If {@code functionName} is not {@code null}, use it as the "name" for
     * the source, else default to {@link #fromPackageTextInternal(String, String)}.
     */
    public static Source fromPackageTextInternalWithName(String text, String packageName, String functionName) {
        if (functionName == null) {
            return fromPackageTextInternal(text, packageName);
        } else {
            return Source.newBuilder(RRuntime.R_LANGUAGE_ID, text, packageName + "::" + functionName).build();
        }
    }

    /**
     * Create an (external) source from the file system path {@code path}.
     */
    public static Source fromFileName(RContext context, String path, boolean internal) throws IOException {
        final TruffleFile file = context.getSafeTruffleFile(path);
        return getCachedByOrigin(file, origin -> Source.newBuilder(RRuntime.R_LANGUAGE_ID, file).internal(internal).build());
    }

    /**
     * Create an (external) source from an R srcfile (
     * {@link RSrcref#createSrcfile(RContext, TruffleFile, Set)}).
     */
    public static Source fromSrcfile(RContext context, REnvironment env) throws IOException {
        TruffleFile file = context.getSafeTruffleFile(getPath(env, SrcrefFields.filename.name()));
        if (file.isAbsolute()) {
            return fromFileName(context, file.toString(), false);
        }
        TruffleFile resolved = file;
        if (!file.isAbsolute()) {
            for (String libPath : RContext.getInstance().libraryPaths) {
                resolved = context.getSafeTruffleFile(libPath).resolve(file.getPath());
                if (resolved.exists()) {
                    break;
                }
            }
        } else {
            resolved = file;
        }
        return fromFileName(context, resolved.toString(), false);
    }

    private static String getPath(REnvironment env, String name) {
        Object o = env.get(name);
        if (o instanceof RStringVector) {
            return ((RStringVector) o).getDataAt(0);
        }
        return (String) o;
    }

    /**
     * Create an unknown source with the given name.
     */
    public static SourceSection createUnknown(String name) {
        return Source.newBuilder(RRuntime.R_LANGUAGE_ID, "", name).build().createSection(0, 0);
    }

    /**
     * If {@code source} was created with {@link #fromPackageTextInternal} return the
     * "package:name", else {@code null}. This can be used to access the corresponding R
     * environment.
     */
    public static String getPackageName(Source source) {
        String sourceName = source.getName();
        if (sourceName.startsWith("<package:")) {
            return sourceName.substring(1, sourceName.lastIndexOf(' '));
        } else {
            return null;
        }
    }

    /**
     * If {@code source} is "internal", return {@code null} else return the file system path
     * corresponding to the associated {@link URI}.
     */
    public static String getPath(Source source) {
        if (source == null || source.isInternal()) {
            return null;
        }
        URI uri = source.getURI();
        assert uri != null;
        return uri.getPath();
    }

    /**
     * Like {@link #getPath(Source)} but ignoring if {@code source} is "internal".
     */
    public static String getPathInternal(Source source) {
        if (source == null) {
            return null;
        }
        URI uri = source.getURI();
        assert uri != null;
        return uri.getPath();
    }

    /**
     * Always returns a non-null string even for internal sources.
     */
    public static String getOrigin(Source source) {
        String path = RSource.getPath(source);
        if (path == null) {
            return source.getName();
        } else {
            return path;
        }
    }

    private static <T, E extends Exception> Source getCachedByOrigin(T origin, SourceGenerator<T, E> generator) throws E {
        CompilerAsserts.neverPartOfCompilation();
        Source src;
        synchronized (deserializedSources) {
            WeakReference<Source> weakReference = deserializedSources.get(origin);
            src = weakReference != null ? weakReference.get() : null;
            if (src == null) {
                src = generator.apply(origin);
                deserializedSources.put(origin, new WeakReference<>(src));
            }
        }
        return src;
    }

    @FunctionalInterface
    private interface SourceGenerator<T, E extends Exception> {
        Source apply(T origin) throws E;
    }
}
