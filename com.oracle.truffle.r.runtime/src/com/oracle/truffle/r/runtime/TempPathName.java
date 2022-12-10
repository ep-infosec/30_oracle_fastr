/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Random;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.ffi.BaseRFFI;

/**
 *
 * As per the GnuR spec, the tempdir() directory is identified on startup. It <b>must</b>be
 * initialized before the first RFFI call as the value is available in the R FFI.
 *
 */
public class TempPathName implements RContext.ContextState {
    private static final String RANDOM_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final int RANDOM_CHARACTERS_LENGTH = RANDOM_CHARACTERS.length();
    private static final int RANDOM_LENGTH = 12; // as per GnuR
    private String tempDirPath;

    private static Random rand;

    private static Random getRandom() {
        if (rand == null) {
            /* We don't want random seeds in the image heap. */
            rand = new Random();
        }
        return rand;
    }

    @Override
    public RContext.ContextState initialize(RContext context) {
        if (context.getKind() == RContext.ContextKind.SHARE_PARENT_RW) {
            // share tempdir with parent
            tempDirPath = context.getParent().stateTempPath.tempDirPath;
            return this;
        }
        String startingTempDir = Utils.getUserTempDir();
        TruffleFile startingTempDirPath = context.getSafeTruffleFile(startingTempDir).resolve("Rtmp");
        // ensure absolute, to avoid problems with R code does a setwd
        if (!startingTempDirPath.isAbsolute()) {
            startingTempDirPath = startingTempDirPath.getAbsoluteFile();
        }
        String t = (String) BaseRFFI.MkdtempRootNode.create().getCallTarget().call(startingTempDirPath.toString() + "XXXXXX");
        if (t != null) {
            tempDirPath = t;
        } else {
            RSuicide.rSuicide("cannot create 'R_TempDir'");
        }

        return this;
    }

    @Override
    @TruffleBoundary
    public void beforeDispose(RContext context) {
        if (context.getKind() == RContext.ContextKind.SHARE_PARENT_RW) {
            return;
        }
        try {
            FileSystemUtils.walkFileTree(context.getSafeTruffleFile(tempDirPath), new DeleteVisitor());
        } catch (Throwable e) {
            // unexpected and we are exiting anyway
        }
    }

    public static String tempDirPath(RContext context) {
        return context.stateTempPath.tempDirPath;
    }

    public static String tempDirPathChecked(RContext ctx) {
        String path = tempDirPath(ctx);
        TruffleFile tFile = ctx.getSafeTruffleFile(path);
        if (!tFile.isDirectory()) {
            if (ctx.getKind() == RContext.ContextKind.SHARE_PARENT_RW) {
                RContext parentCtx = ctx.getParent();
                parentCtx.stateTempPath.initialize(parentCtx);
            }
            ctx.stateTempPath.initialize(ctx);
            path = tempDirPath(ctx);
        }
        return path;
    }

    public static TempPathName newContextState() {
        return new TempPathName();
    }

    @TruffleBoundary
    public static String createNonExistingFilePath(RContext ctx, String pattern, String tempDir, String fileExt) {
        while (true) {
            StringBuilder sb = new StringBuilder(ctx.getSafeTruffleFile(tempDir).resolve(pattern).toString());
            appendRandomString(sb);
            if (fileExt.length() > 0) {
                sb.append(fileExt);
            }
            String path = sb.toString();
            if (!ctx.getSafeTruffleFile(path).exists()) {
                return path;
            }
        }
    }

    private static void appendRandomString(StringBuilder sb) {
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            sb.append(RANDOM_CHARACTERS.charAt(getRandom().nextInt(RANDOM_CHARACTERS_LENGTH)));
        }
    }

    private static final class DeleteVisitor extends SimpleFileVisitor<TruffleFile> {

        @Override
        public FileVisitResult visitFile(TruffleFile file, BasicFileAttributes attrs) throws IOException {
            return del(file);
        }

        @Override
        public FileVisitResult postVisitDirectory(TruffleFile dir, IOException exc) throws IOException {
            return del(dir);
        }

        private static FileVisitResult del(TruffleFile p) throws IOException {
            p.delete();
            return FileVisitResult.CONTINUE;
        }
    }
}
