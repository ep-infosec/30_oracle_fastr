/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.env.frame;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.r.runtime.RArguments;

/**
 * This frame is used for {@code namespace:base}. It delegates all operations to
 * {@link #packageBaseFrame}, however, it's enclosing frame is set to the frame for
 * {@code globalenv}.
 */
public final class NSBaseMaterializedFrame implements MaterializedFrame {

    private static final ValueProfile frameProfile = ValueProfile.createClassProfile();

    private final MaterializedFrame packageBaseFrame;
    @CompilationFinal(dimensions = 1) private final Object[] arguments;

    // this frame descriptor is only used for lookups in FrameSlotChangeMonitor
    private final FrameDescriptor markerFrameDescriptor;

    public NSBaseMaterializedFrame(MaterializedFrame packageBaseFrame, MaterializedFrame globalFrame) {
        this.packageBaseFrame = packageBaseFrame;
        this.arguments = Arrays.copyOf(packageBaseFrame.getArguments(), packageBaseFrame.getArguments().length);
        this.markerFrameDescriptor = FrameSlotChangeMonitor.createEnvironmentFrameDescriptor("namespace:base", this);
        RArguments.initializeEnclosingFrame(this, globalFrame);
    }

    private MaterializedFrame getPackageBaseFrame() {
        return frameProfile.profile(packageBaseFrame);
    }

    public void updateGlobalFrame(MaterializedFrame globalFrame) {
        RArguments.setEnclosingFrame(this, globalFrame, true);
    }

    public FrameDescriptor getMarkerFrameDescriptor() {
        return markerFrameDescriptor;
    }

    @Override
    public FrameDescriptor getFrameDescriptor() {
        return getPackageBaseFrame().getFrameDescriptor();
    }

    @Override
    public Object[] getArguments() {
        return arguments;
    }

    @Override
    public MaterializedFrame materialize() {
        return this;
    }

    /*
     * Delegates to #originalFrame
     */

    @Override
    public Object getObject(int slot) throws FrameSlotTypeException {
        return getPackageBaseFrame().getObject(slot);
    }

    @Override
    public void setObject(int slot, Object value) {
        getPackageBaseFrame().setObject(slot, value);
    }

    @Override
    public byte getByte(int slot) throws FrameSlotTypeException {
        return getPackageBaseFrame().getByte(slot);
    }

    @Override
    public void setByte(int slot, byte value) {
        getPackageBaseFrame().setByte(slot, value);
    }

    @Override
    public boolean getBoolean(int slot) throws FrameSlotTypeException {
        return getPackageBaseFrame().getBoolean(slot);
    }

    @Override
    public void setBoolean(int slot, boolean value) {
        getPackageBaseFrame().setBoolean(slot, value);
    }

    @Override
    public int getInt(int slot) throws FrameSlotTypeException {
        return getPackageBaseFrame().getInt(slot);
    }

    @Override
    public void setInt(int slot, int value) {
        getPackageBaseFrame().setInt(slot, value);
    }

    @Override
    public long getLong(int slot) throws FrameSlotTypeException {
        return getPackageBaseFrame().getLong(slot);
    }

    @Override
    public void setLong(int slot, long value) {
        getPackageBaseFrame().setLong(slot, value);
    }

    @Override
    public float getFloat(int slot) throws FrameSlotTypeException {
        return getPackageBaseFrame().getFloat(slot);
    }

    @Override
    public void setFloat(int slot, float value) {
        getPackageBaseFrame().setFloat(slot, value);
    }

    @Override
    public double getDouble(int slot) throws FrameSlotTypeException {
        return getPackageBaseFrame().getDouble(slot);
    }

    @Override
    public void setDouble(int slot, double value) {
        getPackageBaseFrame().setDouble(slot, value);
    }

    @Override
    public Object getValue(int slot) {
        return getPackageBaseFrame().getValue(slot);
    }

    @Override
    public void copy(int srcSlot, int destSlot) {
        getPackageBaseFrame().copy(srcSlot, destSlot);
    }

    @Override
    public void swap(int first, int second) {
        getPackageBaseFrame().swap(first, second);
    }

    @Override
    public byte getTag(int slot) {
        return getPackageBaseFrame().getTag(slot);
    }

    @Override
    public boolean isObject(int slot) {
        return getPackageBaseFrame().isObject(slot);
    }

    @Override
    public boolean isByte(int slot) {
        return getPackageBaseFrame().isByte(slot);
    }

    @Override
    public boolean isBoolean(int slot) {
        return getPackageBaseFrame().isBoolean(slot);
    }

    @Override
    public boolean isInt(int slot) {
        return getPackageBaseFrame().isInt(slot);
    }

    @Override
    public boolean isLong(int slot) {
        return getPackageBaseFrame().isLong(slot);
    }

    @Override
    public boolean isFloat(int slot) {
        return getPackageBaseFrame().isFloat(slot);
    }

    @Override
    public boolean isDouble(int slot) {
        return getPackageBaseFrame().isDouble(slot);
    }

    @Override
    public void clear(int slot) {
        getPackageBaseFrame().clear(slot);
    }

    @Override
    public Object getAuxiliarySlot(int slot) {
        return getPackageBaseFrame().getAuxiliarySlot(slot);
    }

    @Override
    public void setAuxiliarySlot(int slot, Object value) {
        getPackageBaseFrame().setAuxiliarySlot(slot, value);
    }
}
