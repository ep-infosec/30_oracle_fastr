#
# Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 3 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 3 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 3 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#


# N.B. -g -O2 (which is the FFLAGS default from platform.mk) is currently suppressed
# due to sulong limitations

ifneq ($(MAKECMDGOALS),clean)
include $(TOPDIR)/platform.mk
endif

.PHONY: all clean

include ../include/gnurheaders.mk

# location of LLVM specific object files
OBJ = ../../lib/llvm

C_HDRS := $(wildcard *.h)

LOCAL_C_SOURCES := $(wildcard *.c) 

TRUFFLE_COMMON_C_SOURCES := $(wildcard ../truffle_common/*.c)
TRUFFLE_COMMON_H_SOURCES := $(wildcard ../truffle_common/*.h)

JAVA_GD_C_SOURCES := $(wildcard ../JavaGD/*.c)
JAVA_GD_H_SOURCES := $(wildcard ../JavaGD/*.h)

C_SOURCES := $(LOCAL_C_SOURCES) $(TRUFFLE_COMMON_C_SOURCES) $(JAVA_GD_C_SOURCES)

LOCAL_C_OBJECTS := $(addprefix $(OBJ)/, $(LOCAL_C_SOURCES:.c=.o))
TRUFFLE_COMMON_C_OBJECTS := $(addprefix $(OBJ)/, $(notdir $(TRUFFLE_COMMON_C_SOURCES:.c=.o)))
JAVA_GD_C_OBJECTS := $(addprefix $(OBJ)/, $(notdir $(JAVA_GD_C_SOURCES:.c=.o)))
C_OBJECTS := $(LOCAL_C_OBJECTS) $(TRUFFLE_COMMON_C_OBJECTS) $(JAVA_GD_C_OBJECTS)

FFI_INCLUDES = -I$(TOPDIR)/include -I$(TOPDIR)/include/R_ext
LOCAL_INCLUDES = -I . -I $(abspath ../include) -I $(abspath ../common) 

# Note: LLVM_INCLUDES is set in mx
INCLUDES := $(LOCAL_INCLUDES) $(FFI_INCLUDES) $(LLVM_INCLUDES)
$(info INCLUDES in LLVM are $(INCLUDES))

FFLAGS :=

# uncomment to see exactly where headers are being read from
#CFLAGS := $(CFLAGS) -H

CFLAGS := $(CFLAGS) -DFASTR_LLVM

all: Makefile $(C_OBJECTS)

$(C_OBJECTS): | $(OBJ)

$(OBJ):
	mkdir -p $(OBJ)

$(OBJ)/%.o: %.c $(FASTR_NATIVE_DIR)/include/Rinternals.h rffiutils.h $(TRUFFLE_COMMON_H_SOURCES) ../common/rffi_upcallsindex.h
	$(CC) $(CFLAGS) $(INCLUDES) $(GNUR_HEADER_DEFS) $(SUPPRESS_WARNINGS) -c $< -o $@

$(OBJ)/%.o: ../truffle_common/%.c 
	$(CC) $(CFLAGS) $(INCLUDES) $(SUPPRESS_WARNINGS) -c $< -o $@

$(OBJ)/%.o: ../JavaGD/%.c $(TOPDIR)/include/Rinternals.h $(TRUFFLE_COMMON_H_SOURCES) $(JAVA_GD_H_SOURCES) ../common/rffi_upcallsindex.h $(C_HDRS)
	$(CC) $(CFLAGS) $(INCLUDES) -c $< -o $@

clean:
	rm -rf $(OBJ)
