// Generated by jextract

package org.jjazz.fluidsynthjava.jextract;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import jdk.incubator.foreign.*;
import static jdk.incubator.foreign.CLinker.*;
class constants$54 {

    static final FunctionDescriptor delete_fluid_midi_router_rule$FUNC = FunctionDescriptor.ofVoid(
        C_POINTER
    );
    static final MethodHandle delete_fluid_midi_router_rule$MH = RuntimeHelper.downcallHandle(
        fluidsynth_h.LIBRARIES, "delete_fluid_midi_router_rule",
        "(Ljdk/incubator/foreign/MemoryAddress;)V",
        constants$54.delete_fluid_midi_router_rule$FUNC, false
    );
    static final FunctionDescriptor fluid_midi_router_rule_set_chan$FUNC = FunctionDescriptor.ofVoid(
        C_POINTER,
        C_INT,
        C_INT,
        C_FLOAT,
        C_INT
    );
    static final MethodHandle fluid_midi_router_rule_set_chan$MH = RuntimeHelper.downcallHandle(
        fluidsynth_h.LIBRARIES, "fluid_midi_router_rule_set_chan",
        "(Ljdk/incubator/foreign/MemoryAddress;IIFI)V",
        constants$54.fluid_midi_router_rule_set_chan$FUNC, false
    );
    static final FunctionDescriptor fluid_midi_router_rule_set_param1$FUNC = FunctionDescriptor.ofVoid(
        C_POINTER,
        C_INT,
        C_INT,
        C_FLOAT,
        C_INT
    );
    static final MethodHandle fluid_midi_router_rule_set_param1$MH = RuntimeHelper.downcallHandle(
        fluidsynth_h.LIBRARIES, "fluid_midi_router_rule_set_param1",
        "(Ljdk/incubator/foreign/MemoryAddress;IIFI)V",
        constants$54.fluid_midi_router_rule_set_param1$FUNC, false
    );
    static final FunctionDescriptor fluid_midi_router_rule_set_param2$FUNC = FunctionDescriptor.ofVoid(
        C_POINTER,
        C_INT,
        C_INT,
        C_FLOAT,
        C_INT
    );
    static final MethodHandle fluid_midi_router_rule_set_param2$MH = RuntimeHelper.downcallHandle(
        fluidsynth_h.LIBRARIES, "fluid_midi_router_rule_set_param2",
        "(Ljdk/incubator/foreign/MemoryAddress;IIFI)V",
        constants$54.fluid_midi_router_rule_set_param2$FUNC, false
    );
    static final FunctionDescriptor fluid_midi_router_handle_midi_event$FUNC = FunctionDescriptor.of(C_INT,
        C_POINTER,
        C_POINTER
    );
    static final MethodHandle fluid_midi_router_handle_midi_event$MH = RuntimeHelper.downcallHandle(
        fluidsynth_h.LIBRARIES, "fluid_midi_router_handle_midi_event",
        "(Ljdk/incubator/foreign/MemoryAddress;Ljdk/incubator/foreign/MemoryAddress;)I",
        constants$54.fluid_midi_router_handle_midi_event$FUNC, false
    );
    static final FunctionDescriptor fluid_midi_dump_prerouter$FUNC = FunctionDescriptor.of(C_INT,
        C_POINTER,
        C_POINTER
    );
    static final MethodHandle fluid_midi_dump_prerouter$MH = RuntimeHelper.downcallHandle(
        fluidsynth_h.LIBRARIES, "fluid_midi_dump_prerouter",
        "(Ljdk/incubator/foreign/MemoryAddress;Ljdk/incubator/foreign/MemoryAddress;)I",
        constants$54.fluid_midi_dump_prerouter$FUNC, false
    );
}


