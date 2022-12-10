/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.TruffleFile;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.oracle.truffle.r.runtime.ResourceHandlerFactory.Handler;
import com.oracle.truffle.r.runtime.context.RContext;

/**
 * Default implementation uses the default mechanism in {@code java.lang.Class}.
 */
class LazyResourceHandlerFactory extends ResourceHandlerFactory implements Handler {

    @Override
    public URL getResource(Class<?> accessor, String name) {
        return accessor.getResource(name);
    }

    @Override
    public InputStream getResourceAsStream(RContext context, Class<?> accessor, String name) {
        return accessor.getResourceAsStream(name);
    }

    @Override
    protected Handler newHandler() {
        return this;
    }

    @Override
    public Map<String, String> getRFiles(RContext context, Class<?> accessor, String pkgName) {
        CodeSource source = accessor.getProtectionDomain().getCodeSource();
        Map<String, String> result = new HashMap<>();
        try {
            URL url = source.getLocation();
            TruffleFile sourceFile = context.getSafeTruffleFile(url.toURI().getPath());
            if (sourceFile.isDirectory()) {
                try (InputStream is = accessor.getResourceAsStream(pkgName + "/R")) {
                    if (is != null) {
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
                            String line;
                            while ((line = r.readLine()) != null) {
                                if (line.endsWith(".r") || line.endsWith(".R")) {
                                    final String rResource = pkgName + "/R/" + line.trim();
                                    result.put(rResource, Utils.getResourceAsString(context, accessor, rResource, true));
                                }
                            }
                        }
                    }
                }
            } else {
                try (JarFile fastrJar = new JarFile(sourceFile.getPath())) {
                    Enumeration<JarEntry> iter = fastrJar.entries();
                    while (iter.hasMoreElements()) {
                        JarEntry entry = iter.nextElement();
                        String name = entry.getName();
                        if (name.endsWith(".R") || name.endsWith(".r")) {
                            Path p = Paths.get(name);
                            Path entryPkgPath = p.getName(p.getNameCount() - 3).getFileName();
                            assert entryPkgPath != null;
                            String entryPkg = entryPkgPath.toString();
                            Path entryParentPath = p.getName(p.getNameCount() - 2).getFileName();
                            assert entryParentPath != null;
                            String entryParent = entryParentPath.toString();
                            if (entryParent.equals("R") && entryPkg.equals(pkgName)) {
                                int size = (int) entry.getSize();
                                byte[] buf = new byte[size];
                                InputStream is = fastrJar.getInputStream(entry);
                                int totalRead = 0;
                                int n;
                                while ((n = is.read(buf, totalRead, buf.length - totalRead)) > 0) {
                                    totalRead += n;
                                }
                                result.put(p.toString(), new String(buf));
                            }
                        }
                    }
                }
            }
            return result;
        } catch (Exception ex) {
            throw RSuicide.rSuicide(RContext.getInstance(), ex, "Could not load R files from resources. Details: " + ex.getMessage());
        }
    }
}
