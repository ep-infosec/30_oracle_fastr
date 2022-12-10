/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include <rffiutils.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <sys/stat.h>
#include <glob.h>
#include <sys/utsname.h>
#include <errno.h>

#define PCRE2_CODE_UNIT_WIDTH 8
#include <pcre2.h>

int call_base_initEventLoop(char *fifoInPath, char *fifoOutPath) {
    return initEventLoop(fifoInPath, fifoOutPath);
}

int call_base_getpid() {
    return getpid();
}

int call_base_getcwd(char *buf, int len) {
    char *r = getcwd(buf, len);
    if (r == NULL) {
        return 0;
    } else {
        return 1;
    }
}

int call_base_chdir(char *dir) {
    return chdir(dir);
}

int call_base_mkdtemp(char *template) {
    char *r = mkdtemp(template);
    if (r == NULL) {
        return 0;
    } else {
        return 1;
    }
}

void call_base_uname(void (*call_uname_setfields)(char *sysname, char *release, char *version, char *machine, char *nodename)) {
	struct utsname name;

	uname(&name);
	call_uname_setfields(ensure_string(name.sysname), ensure_string(name.release), ensure_string(name.version),
			ensure_string(name.machine), ensure_string(name.nodename));
}

int errfunc(const char* path, int error) {
	return 0;
}

void call_base_glob(void *closure, char *pattern) {
	void (*call_addpath)(void *path) = closure;

	glob_t globstruct;
	int rc = glob(pattern, 0, errfunc, &globstruct);
	if (rc == 0) {
		int i;
		for (i = 0; i < globstruct.gl_pathc; i++) {
			char *path = globstruct.gl_pathv[i];
			call_addpath(ensure_string(path));
		}
	}
}

void call_base_readlink(void (*call_setresult)(void *link, int cerrno), char *path) {
	char *link = NULL;
	int cerrno = 0;
    char buf[4096];
    int len = readlink(path, buf, 4096);
    if (len == -1) {
    	cerrno = errno;
    } else {
    	buf[len] = 0;
    	link = buf;
    }
	call_setresult(ensure_string(link), cerrno);
}

void call_base_strtol(void (*call_setresult)(long result, int cerrno), char *s, int base) {
    long rc = strtol(s, NULL, base);
	call_setresult(rc, errno);
}

extern const char * zlibVersion();

static void pcre2_version(char *buffer, int buff_len)
{
    int min_buff_len = pcre2_config(PCRE2_CONFIG_VERSION, NULL);
    if (buff_len < min_buff_len) {
        printf("Fatal error: pcre_version: buff_len < min_buff_len\n");
        exit(1);
    }
    int ret = pcre2_config(PCRE2_CONFIG_VERSION, buffer);
    if (ret < 0) {
        printf("Fatal error: returned %d from pcre2_config\n", ret);
        exit(1);
    }
}

void call_base_eSoftVersion(void (*call_eSoftVersion_setfields)(char *zlibVersion, char *pcre2Version)) {
    char sZlibVersion[256];
    char sPcre2Version[256];
    snprintf(sZlibVersion, 256, "%s", zlibVersion());
    pcre2_version(sPcre2Version, 256);
    call_eSoftVersion_setfields(sZlibVersion, sPcre2Version);
}

int call_base_umask(int mode) {
	return umask(mode);
}

extern int R_cpolyroot(double *opr, double *opi, int *degree, double *zeror, double *zeroi, Rboolean *fail);

int call_base_cpolyroot(double *opr, double *opi, int degree, double *zeror, double *zeroi) {
    Rboolean fail = FALSE;
    R_cpolyroot(opr, opi, &degree, zeror, zeroi, &fail);
    return (fail == TRUE) ? 1 : 0;
}

