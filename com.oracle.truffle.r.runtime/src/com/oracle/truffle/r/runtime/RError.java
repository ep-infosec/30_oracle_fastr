/*
 * Copyright (c) 1995-2015, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, a copy is available at
 * https://www.R-project.org/Licenses/
 */
package com.oracle.truffle.r.runtime;

import static com.oracle.truffle.r.launcher.REPL.ERROR_PRINTED_MEMBER_NAME;
import static com.oracle.truffle.r.runtime.RLogger.LOGGER_PERFORMANCE_WARNINGS;
import static com.oracle.truffle.r.runtime.context.FastROptions.PerformanceWarnings;

import java.io.IOException;
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.builtins.RBuiltinKind;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.env.REnvironment.PutException;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

/**
 * A facade for handling errors. This class extends {@link RuntimeException} so that it can be
 * thrown, and various static methods that should be used to actually effect the throw. It also
 * declares the {@link Message} enum that provides an abstract way to declare a particular message.
 *
 * The error messages in {@link Message} have been copied from GNU R source code.
 *
 * The details of the error handling, which is complicated by support for condition handling and the
 * ability to invoke arbitrary code, typically {@code browser}, is in {@link RErrorHandling}.
 *
 * In the event that an error is not "handled" or a warning is actually generated (they may be
 * delayed) it is necessary to construct a string that represents the "context", (using the GnuR
 * term) where the error occurred. GnuR maintains a physical {@code Context} objects to denote this,
 * but FastR does not, Instead we have information on which "builtin" reported the error/warning, by
 * way of a {@link Node} value (which may be indirectly related to the actual builtin due to AST
 * transformations) and the Truffle {@link Frame} stack. Mostly the {@link Node} value and
 * {@link Frame} are sufficient to reconstruct the context, but there are some special cases that
 * might require more information to disambiguate, one such example is {@link ShowCallerOf}.
 */
@SuppressWarnings("serial")
@ExportLibrary(InteropLibrary.class)
public final class RError extends AbstractTruffleException {

    private final String verboseStackTrace;

    /**
     * Can be used by the embedders to find out if the error message was already printed by the
     * engine. GNU-R prints the message immediately upon the error, which causes visible side
     * effects. We avoid printing the message inside the engine as long as the side effects cannot
     * be observed by the R code.
     */
    private boolean printed = false;

    /**
     * This exception should be subclassed by subsystems that need to throw subsystem-specific
     * exceptions to be caught by builtin implementations, which can then invoke
     * {@link RError#error(RBaseNode, RErrorException)}, which access the stored {@link Message}
     * object and any arguments. E.g. see {@link PutException}.
     */
    public abstract static class RErrorException extends Exception {
        private static final long serialVersionUID = 1L;

        private final RError.Message msg;
        @CompilationFinal(dimensions = 1) private final Object[] args;

        @TruffleBoundary
        protected RErrorException(Throwable cause, RError.Message msg, Object[] args) {
            super(RErrorHandling.formatMessage(msg, args), cause);
            this.msg = msg;
            this.args = args;
        }
    }

    public abstract static class ErrorContext extends RBaseNode {
        private ErrorContext() {
            // private constructor
        }

        @Override
        public RBaseNode getErrorContext() {
            return this;
        }

        @Override
        public SourceSection getEncapsulatingSourceSection() {
            return RSyntaxNode.INTERNAL;
        }

        @Override
        public SourceSection getSourceSection() {
            return RSyntaxNode.INTERNAL;
        }
    }

    private static final class ErrorContextImpl extends ErrorContext {
    }

    public static ErrorContext contextForBuiltin(RBuiltin builtin) {
        ErrorContext callObj;
        if (builtin == null) {
            callObj = RError.NO_CALLER;
        } else if (builtin.kind() == RBuiltinKind.INTERNAL) {
            callObj = RError.SHOW_CALLER;
        } else {
            callObj = null;
        }
        return callObj;
    }

    /**
     * This flags a call to {@code error} or {@code warning} where the error message should show the
     * caller's caller.
     */
    public static final ErrorContext SHOW_CALLER2 = new ErrorContextImpl();
    /**
     * This flags a call to {@code error} or {@code warning} where the error message should show the
     * caller of the current function.
     */
    public static final ErrorContext SHOW_CALLER = new ErrorContextImpl();

    /**
     * This flags a call to {@code error} or {@code warning} where the error message should show the
     * caller provided function if such is available. Otherwise the caller of the current function
     * will be shown.
     */
    public static final class ShowCallerOf extends ErrorContext {
        private final String function;

        public ShowCallerOf(String function) {
            this.function = function;
        }

        public String getCallerOf() {
            return function;
        }
    }

    /**
     * A very special case that ensures that no caller is output in the error/warning message. This
     * is needed where, even if there is a caller, GnuR does not show it.
     */
    public static final ErrorContext NO_CALLER = new ErrorContextImpl();

    /**
     * This is a workaround for a case in {@code RCallNode} where an error might be thrown while
     * executing a {@code RootNode}, which is not a subclass of {@link RBaseNode}.
     */
    public static final ErrorContext ROOTNODE = new ErrorContextImpl();

    /**
     * If the message is set (non-null), then it was not printed yet and it is left to the
     * embedder/REPL to deal with the exception and eventual printing. The message should not
     * contain the last new line that would otherwise be printed.
     */
    RError(String msg, Node location) {
        super(msg, location);
        this.verboseStackTrace = RInternalError.createVerboseStackTrace();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isException() {
        return true;
    }

    @ExportMessage
    RuntimeException throwException() {
        throw this;
    }

    @ExportMessage
    boolean hasSourceLocation() {
        return getLocation() != null;
    }

    @ExportMessage(name = "getSourceLocation")
    SourceSection getSourceSection() throws UnsupportedMessageException {
        Node location = getLocation();
        if (location == null) {
            throw UnsupportedMessageException.create();
        }
        SourceSection ss = location.getEncapsulatingSourceSection();
        if (ss == null) {
            throw UnsupportedMessageException.create();
        }
        return ss;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new String[]{ERROR_PRINTED_MEMBER_NAME};
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isMemberReadable(String name) {
        return name.equals(ERROR_PRINTED_MEMBER_NAME);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object readMember(String name) throws UnknownIdentifierException {
        if (name.equals(ERROR_PRINTED_MEMBER_NAME)) {
            return printed;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw UnknownIdentifierException.create(name);
    }

    @Override
    public String toString() {
        return getMessage();
    }

    public String getVerboseStackTrace() {
        return verboseStackTrace;
    }

    public void setPrinted(boolean bl) {
        this.printed = bl;
    }

    public static RError error(ErrorContext node, Message msg) {
        throw error(node, msg, new Object[]{});
    }

    public static RError error(ErrorContext node, Message msg, Object arg) {
        throw error(node, msg, new Object[]{arg});
    }

    @TruffleBoundary
    public static RError error(ErrorContext node, Message msg, Object... args) {
        throw error0(node, msg, args);
    }

    @TruffleBoundary
    public static RError error(RBaseNode node, Message msg, Object... args) {
        throw error0(node, msg, args);
    }

    @TruffleBoundary
    public static RError error(Node node, Message msg, Object... args) {
        throw error0(findParentRBase(node), msg, args);
    }

    @TruffleBoundary
    public static RError error(RBaseNode node, Message msg) {
        throw error0(node, msg, (Object[]) null);
    }

    @TruffleBoundary
    public static RError error(Node node, Message msg) {
        throw error0(findParentRBase(node), msg, (Object[]) null);
    }

    @TruffleBoundary
    public static RError interopError(RBaseNode node, Throwable e, TruffleObject o) {
        throw error0(node, RError.Message.GENERIC, "Foreign function failed: " + (e.getMessage() != null ? e.getMessage() : e.toString()) + " on object " + o);
    }

    @TruffleBoundary
    public static RError ioError(RBaseNode node, IOException ex) {
        throw error0(node, Message.GENERIC, ex.getMessage());
    }

    public static RBaseNode findParentRBase(Node node) {
        Node current = node;
        while (current != null) {
            if (current instanceof RBaseNode) {
                return (RBaseNode) current;
            }
            current = current.getParent();
        }
        throw RInternalError.shouldNotReachHere("Could not find RBaseNode for given Node. Is it not adopted in the AST?");
    }

    /**
     * Handles an R error with the most general argument signature. All other facade variants
     * delegate to this method.
     *
     * Note that the method never actually returns a result, instead it throws the error directly.
     * However, the signature has a return type of {@link RError} to allow callers to use the idiom
     * {@code throw error(...)} to indicate the control transfer. It is entirely possible that, due
     * to condition handlers, the error will not actually be thrown.
     *
     * @param node {@code RNode} of the code throwing the error, or {@link #SHOW_CALLER2} if not
     *            available. If {@code NO_NODE} an attempt will be made to identify the call context
     *            from the currently active frame.
     * @param msg a {@link Message} instance specifying the error
     * @param args arguments for format specifiers in the message string
     */
    @TruffleBoundary
    private static RError error0(RBaseNode node, Message msg, Object... args) {
        assert node != null;
        // thrown from a builtin specified by "node"
        RErrorHandling.signalError(node, msg, args);
        return RErrorHandling.errorcallDflt(node != NO_CALLER, node, msg, args);
    }

    /**
     * Convenience variant of {@link #error(RBaseNode, Message, Object...)} where only one argument
     * to the message is given. This avoids object array creation caller, which may be
     * Truffle-compiled.
     */
    @TruffleBoundary
    public static RError error(RBaseNode node, Message msg, Object arg) {
        throw error(node, msg, new Object[]{arg});
    }

    /**
     * Variant for the case where the original error occurs in code where it is not appropriate to
     * report the error. The error information is propagated using the {@link RErrorException}.
     */
    @TruffleBoundary
    public static RError error(RBaseNode node, RErrorException ex) {
        throw error(node, ex.msg, ex.args);
    }

    /**
     * A temporary error that indicates an unimplemented feature where terminating the VM using
     * {@link RSuicide#rSuicide(String)} would be inappropriate.
     */
    public static RError nyi(RBaseNode node, String msg) {
        CompilerDirectives.transferToInterpreter();
        throw error(node, RError.Message.NYI, msg);
    }

    @TruffleBoundary
    public static void warning(ErrorContext node, Message msg, Object... args) {
        assert node != null;
        RErrorHandling.warningcall(true, node, msg, args);
    }

    @TruffleBoundary
    public static void warning(RBaseNode node, Message msg, Object... args) {
        assert node != null;
        RErrorHandling.warningcall(true, node, msg, args);
    }

    @TruffleBoundary
    public static RError stop(boolean showCall, RBaseNode node, Message msg, Object arg) {
        assert node != null;
        RErrorHandling.signalError(node, msg, arg);
        return RErrorHandling.errorcallDflt(showCall, node, msg, arg);
    }

    public static void performanceWarning(String string) {
        checkObsoleteOption();
        TruffleLogger logger = RLogger.getLogger(LOGGER_PERFORMANCE_WARNINGS);
        if (logger.isLoggable(Level.FINE)) {
            StringBuilder sb = new StringBuilder();
            sb.append("Performance warning: ").append(string).append("\n");
            sb.append(createStackTrace(false));
            logger.fine(sb.toString());
            // warning(RError.SHOW_CALLER2, Message.PERFORMANCE, string);
        }
    }

    @TruffleBoundary
    private static void checkObsoleteOption() {
        if (RContext.getInstance().getOption(PerformanceWarnings)) {
            System.out.println("WARNING: The PerformanceWarnings option was discontinued.\n" +
                            "You can rerun FastR with --log.R.com.oracle.truffle.r.performanceWarnings.level=FINE");
        }
    }

    private static String createStackTrace(boolean full) {
        StackTraceElement[] trace = new RuntimeException().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int length = full ? trace.length : (trace.length < 8 ? trace.length : 8);
        for (int i = 1; i < length; i++) {
            StackTraceElement element = trace[i];
            sb.append("  at ").append(element.getClassName()).append(".").append(element.getMethodName()).append("(").append(element.getFileName()).append(":").append(
                            element.getLineNumber()).append(")\n");
        }
        return sb.toString();
    }

    public enum Message {
        /**
         * Eventually this will go away, used only by {@link RError#nyi}.
         */
        NYI("not yet implemented: %s"),
        /**
         * {@code GENERIC} should only be used in the rare case where a known error is not
         * available.
         */
        GENERIC("%s"),
        TOO_SHORT("'%s' is too short"),
        CONVERTED_FROM_WARNING("(converted from warning) %s"),
        INVALID_DATA_OF_TYPE_TOO_SHORT("invalid data of mode '%s' (too short)"),
        VECTOR_SIZE_TOO_LARGE("vector size specified is too large"),
        ARG_RECYCYLED("an argument will be fractionally recycled"),
        LENGTH_GT_1("the condition has length > 1 and only the first element will be used"),
        LENGTH_ZERO("argument is of length zero"),
        NA_UNEXP("missing value where TRUE/FALSE needed"),
        LENGTH_NOT_MULTI("longer object length is not a multiple of shorter object length"),
        INTEGER_OVERFLOW("NAs produced by integer overflow"),
        INTEGER_OVERFLOW_USE_NUMERIC("integer overflow in '%s'; use '%s(as.numeric(.))'"),
        NA_OR_NAN("NA/NaN argument"),
        SUBSCRIPT_BOUNDS("subscript out of bounds"),
        SUBSCRIPT_BOUNDS_SUB("[[ ]] subscript out of bounds"),
        INVALID_NEGATIVE_SUBSCRIPT("invalid negative subscript"),
        SELECT_LESS_1("attempt to select less than one element"),
        SELECT_LESS_1_IN_ONE_INDEX("attempt to select less than one element in integerOneIndex"),
        SELECT_MORE_1("attempt to select more than one element"),
        SELECT_MORE_1_IN_ONE_INDEX("attempt to select more than one element in integerOneIndex"),
        ONLY_0_MIXED("only 0's may be mixed with negative subscripts"),
        REPLACEMENT_0("replacement has length zero"),
        NOT_MULTIPLE_REPLACEMENT("number of items to replace is not a multiple of replacement length"),
        MORE_SUPPLIED_REPLACE("more elements supplied than there are to replace"),
        NA_SUBSCRIPTED("NAs are not allowed in subscripted assignments"),
        INVALID_ARG_TYPE("invalid argument type"),
        INVALID_LEN_0_ARG("invalid length 0 argument"),
        INVALID_ARG_UNARY("invalid argument to unary operator"),
        VECTOR_SIZE_NEGATIVE("vector size cannot be negative"),
        VECTOR_SIZE_NA("vector size cannot be NA"),
        VECTOR_SIZE_NA_NAN("vector size cannot be NA/NaN"),
        NO_LOOP_FOR_BREAK_NEXT("no loop for break/next, jumping to top level"),
        INVALID_FOR_SEQUENCE("invalid for() loop sequence"),
        NO_NONMISSING_MAX("no non-missing arguments to max; returning -Inf"),
        NO_NONMISSING_MIN("no non-missing arguments to min; returning Inf"),
        NO_NONMISSING_MAX_NA("no non-missing arguments, returning NA"),
        NO_NONMISSING_MIN_NA("no non-missing arguments, returning NA"),
        LENGTH_NONNEGATIVE("length must be non-negative number"),
        MUST_BE_NONNEGATIVE("'%s' must be a non-negative number"),
        MUST_BE_POSITIVE("'%s' must be positive"),
        MUST_BE_POSITIVE_INT("'%s' must be a positive integer"),
        MUST_BE_POSITIVE_SD("%s must be non-negative number"),
        MUST_BE_SQUARE("'%s' (%d x %d) must be square"),
        MUST_BE_SQUARE_COMPATIBLE("'%s' (%d x %d) must be compatible with '%s' (%d x %d)"),
        INVALID_TFB_SD("invalid (to - from)/by in seq(.)"),
        INVALID_TFB("invalid '(to - from)/by'"),
        WRONG_SIGN_IN_BY("wrong sign in 'by' argument"),
        BY_TOO_SMALL("'by' argument is much too small"),
        TOO_LONG_VECTOR("result would be too long a vector"),
        INCORRECT_SUBSCRIPTS("incorrect number of subscripts"),
        INCORRECT_SUBSCRIPTS_MATRIX("incorrect number of subscripts on matrix"),
        NEGATIVE_EXTENTS_TO_MATRIX("negative extents to matrix"),
        INVALID_SEP("invalid 'sep' specification"),
        INVALID_LENGTH("invalid '%s' length"),
        INVALID_NA_PRINT_SPEC("invalid 'na.print' specification"),
        EMPTY_WHAT("empty 'what' specified"),
        LINE_ELEMENTS("line %d did not have %d elements"),
        ITEMS_NOT_MULTIPLE("number of items read is not a multiple of the number of columns"),
        TRACEMEM_NOT_NULL("cannot trace NULL"),
        INPUT_MUST_BE_STRING("input must be a character string"),
        // mathlib errors/warnings
        ML_ERROR_RANGE("value out of range in '%s'"),
        ML_ERROR_NOCONV("convergence failed in '%s'"),
        ML_ERROR_PRECISION("full precision may not have been achieved in '%s'"),
        ML_ERROR_UNDERFLOW("underflow occurred in '%s'"),
        // below: GNU R gives also expression for the argument
        NOT_FUNCTION("'%s' is not a function, character or symbol"),
        NOT_A_FUNCTION("'%s' is not a function"),
        NON_CHARACTER("non-character argument"),
        NON_CHARACTER_OBJECTS("non-character object(s)"),
        NON_CHARACTER_NAMES("non-character names"),
        NON_NUMERIC_MATH("non-numeric argument to mathematical function"),
        NAN_PRODUCED("NaNs produced"),
        NAN_PRODUCED_IN_FUNCTION("NaNs produced in function \"%s\""),
        NUMERIC_COMPLEX_MATRIX_VECTOR("requires numeric/complex matrix/vector arguments"),
        NON_CONFORMABLE_ARGS("non-conformable arguments"),
        DATA_VECTOR("'data' must be of a vector type"),
        NON_NUMERIC_MATRIX_EXTENT("non-numeric matrix extent"),
        NON_CONFORMABLE_ARRAYS("non-conformable arrays"),
        UNKNOWN_UNNAMED_OBJECT("object not found"),
        CHOOSE_ROUNDING_WARNING("'k' (%g) must be integer, rounded to %d"),
        WILCOX_TOO_MUCH_MEMORY("running wilcox with m=%g, n=%g would allocate too much memory, returning NaN instead."),
        ONLY_MATRIX_DIAGONALS("only matrix diagonals can be replaced"),
        REPLACEMENT_DIAGONAL_LENGTH("replacement diagonal has wrong length"),
        NA_INTRODUCED_COERCION("NAs introduced by coercion"),
        NA_INTRODUCED_COERCION_INT("NAs introduced by coercion to integer range"),
        PRECISSION_LOSS_BY_CONVERSION("Possible precission loss by coercion of long %d to double %f"),
        ARGUMENT_WHICH_NOT_LOGICAL("argument to 'which' is not logical"),
        X_LONGER_THAN_Y("'%s' is longer than '%s'"),
        X_NUMERIC("'x' must be numeric"),
        X_LIST_ATOMIC("'x' must be a list or atomic vector"),
        X_ARRAY_TWO("'x' must be an array of at least two dimensions"),
        ACCURACY_MODULUS("probable complete loss of accuracy in modulus"),
        INVALID_SEPARATOR("invalid separator"),
        INCORRECT_DIMENSIONS("incorrect number of dimensions"),
        LOGICAL_SUBSCRIPT_LONG("(subscript) logical subscript too long"),
        ARGUMENT_LENGTHS_DIFFER("argument lengths differ"),
        ZERO_LENGTH_PATTERN("zero-length pattern"),
        UNSUPPORTED_MODE("unsupported mode"),
        MODE_LENGTH_ONE("'mode' must be of length at least one"),
        ALL_CONNECTIONS_IN_USE("all connections are in use"),
        CANNOT_READ_CONNECTION("cannot read from this connection"),
        CONNECTION_NOT_OPEN_READ("connection not open for reading"),
        CONNECTION_NOT_OPEN_WRITE("connection not open for writing"),
        BINARY_CONNECTION_REQUIRED("binary-mode connection required for ascii=FALSE"),
        CANNOT_WRITE_CONNECTION("cannot write to this connection"),
        CONN_XDR("cannot save XDR format to a text-mode connection"),
        ONLY_READ_BINARY_CONNECTION("can only read from a binary connection"),
        ONLY_WRITE_BINARY_CONNECTION("can only write to a binary connection"),
        ONLY_WRITE_CHAR_OBJECTS("can only write character objects"),
        NOT_A_TEXT_CONNECTION("'con' is not a textConnection"),
        NOT_AN_OUTPUT_TEXT_CONNECTION("'con' is not an output textConnection"),
        UNSEEKABLE_CONNECTION("'con' is not seekable"),
        MUST_BE_STRING_OR_CONNECTION("'%s' must be a character string or a connection"),
        MORE_CHARACTERS("writeChar: more characters requested than are in the string - will zero-pad"),
        TOO_FEW_LINES_READ_LINES("too few lines read in readLineWRITE_ONs"),
        INVALID_CONNECTION("invalid connection"),
        OUT_OF_RANGE("out-of-range values treated as 0 in coercion to raw"),
        UNIMPLEMENTED_COMPLEX("unimplemented complex operation"),
        UNIMPLEMENTED_COMPLEX_FUN("unimplemented complex function"),
        COMPARISON_COMPLEX("invalid comparison with complex values"),
        NON_NUMERIC_BINARY("non-numeric argument to binary operator"),
        RAW_SORT("raw vectors cannot be sorted"),
        ONLY_ATOMIC_CAN_BE_SORTED("only atomic vectors can be sorted"),
        INVALID_UNNAMED_ARGUMENT("invalid argument"),
        INVALID_UNNAMED_VALUE("invalid value"),
        NAMES_NONVECTOR("names() applied to a non-vector"),
        NAMES_LONGER("'names' attribute [%d] must be the same length as the vector [%d]"),
        ONLY_FIRST_VARIABLE_NAME("only the first element is used as variable name"),
        INVALID_FIRST_FILENAME("invalid first filename"),
        INVALID_SECOND_FILENAME("invalid second filename"),
        INVALID_FIRST_ARGUMENT("invalid first argument"),
        INVALID_FIRST_ARGUMENT_MUST_BE_VEC("invalid first argument, must be vector (list or atomic)"),
        INVALID_FIRST_ARGUMENT_NAME("invalid first argument '%s'"),
        INVALID_SECOND_ARGUMENT_NAME("invalid second argument '%s'"),
        NO_ENCLOSING_ENVIRONMENT("no enclosing environment"),
        ASSIGN_EMPTY("cannot assign values in the empty environment"),
        USE_NULL_ENV_DEFUNCT("use of NULL environment is defunct"),
        AS_ENV_NULL_DEFUNCT("using 'as.environment(NULL)' is defunct"),
        REPLACEMENT_NOT_ENVIRONMENT("replacement object is not an environment"),
        ARGUMENT_NOT_MATRIX("argument is not a matrix"),
        ARGUMENT_NOT_FUNCTION("argument is not a function"),
        OBJECT_NOT_MATRIX("object is not a matrix"),
        ARGUMENT_NOT_ENVIRONMENT("argument is not an environment"),
        ARGUMENT_NAME_NOT_ENVIRONMENT("'%s' is not an environment"),
        DOLLAR_ATOMIC_VECTORS("$ operator is invalid for atomic vectors"),
        COERCING_LHS_TO_LIST("Coercing LHS to a list"),
        INVALID_NULL_LHS("invalid (NULL) left side of assignment"),
        INVALID_LHS("invalid (%s) left-hand side to assignment"),
        ARGUMENT_NOT_LIST("argument not a list"),
        FIRST_ARGUMENT_NOT_NAMED_LIST("first argument must be a named list"),
        FIRST_ARGUMENT_NOT_CHARVEC("first argument must be a character vector"),
        FIRST_ARGUMENT_NOT_FILENAME("first argument must be a filename"),
        ARG_MUST_BE_FUNCTION("argument must be a function"),
        ASCII_NOT_LOGICAL("'ascii' must be logical"),
        MUST_BE_LOGICAL("argument '%s' must be logical"),
        LIST_NAMES_SAME_LENGTH("names(x) must be a character vector of the same length as x"),
        DIMS_CONTAIN_NEGATIVE_VALUES("the dims contain negative values"),
        NEGATIVE_LENGTH_VECTORS_NOT_ALLOWED("negative length vectors are not allowed"),
        LONG_VECTORS_NOT_SUPPORTED("long length vectors are not supported"),
        FIRST_ARG_MUST_BE_ARRAY("invalid first argument, must be an array"),
        IMAGINARY_PARTS_DISCARDED_IN_COERCION("imaginary parts discarded in coercion"),
        DIMS_CONTAIN_NA("the dims contain missing values"),
        LENGTH_ZERO_DIM_INVALID("length-0 dimension vector is invalid"),
        ATTRIBUTES_LIST_OR_NULL("attributes must be a list or NULL"),
        SET_ATTRIBUTES_ON_NULL("attempt to set an attribute on NULL"),
        RECALL_CALLED_OUTSIDE_CLOSURE("'Recall' called from outside a closure"),
        MATCH_CALL_CALLED_OUTSIDE_FUNCTION("match.call() was called from outside a function"),
        NOT_NUMERIC_VECTOR("argument is not a numeric vector"),
        UNSUPPORTED_PARTIAL("unsupported options for partial sorting"),
        INDEX_RETURN_REMOVE_NA("'index.return' only for 'na.last(NA'"),
        SUPPLY_X_Y_MATRIX("supply both 'x' and 'y' or a matrix-like 'x'"),
        SD_ZERO("the standard deviation is zero"),
        INVALID_UNNAMED_ARGUMENTS("invalid arguments"),
        INVALID_INPUT("invalid input"),
        INVALID_INPUT_TYPE("invalid input type"),
        NA_PRODUCED("NAs produced"),
        DETERMINANT_COMPLEX("determinant not currently defined for complex matrices"),
        NON_NUMERIC_ARGUMENT("non-numeric argument"),
        FFT_FACTORIZATION("fft factorization error"),
        COMPLEX_NOT_PERMITTED("complex matrices not permitted at present"),
        FIRST_QR("first argument must be a QR decomposition"),
        ONLY_SQUARE_INVERTED("only square matrices can be inverted"),
        NON_NUMERIC_ARGUMENT_FUNCTION("non-numeric argument to function"),
        SEED_LENGTH(".Random.seed has wrong length"),
        SAME_TYPE("'%s' and '%s' must have the same type"),
        UNIMPLEMENTED_TYPE_IN_FUNCTION("unimplemented type '%s' in '%s'"),
        // below: not exactly GNU-R message
        PROMISE_CYCLE("promise already under evaluation: recursive default argument reference or earlier problems?"),
        MISSING_ARGUMENTS("'missing' can only be used for arguments"),
        INVALID_ENVIRONMENT("invalid environment"),
        INVALID_ENVIRONMENT_SPECIFIED("invalid environment specified"),
        ARG_MUST_BE_ENV("argument must be an environment"),
        ENVIR_NOT_LENGTH_ONE("numeric 'envir' arg not of length one"),
        FMT_NOT_CHARACTER("'fmt' is not a character vector"),
        UNSUPPORTED_TYPE("unsupported type"),
        AT_MOST_ONE_ASTERISK("at most one asterisk '*' is supported in each conversion specification"),
        TOO_FEW_ARGUMENTS("too few arguments"),
        ARGUMENT_STAR_NUMBER("argument for '*' conversion specification must be a number"),
        EXACTLY_ONE_WHICH("exactly one attribute 'which' must be given"),
        ATTRIBUTES_NAMED("attributes must be named"),
        MISSING_INVALID("missing value is invalid"),
        TYPE_EXPECTED("%s argument expected"),
        CANNOT_CHANGE_DIRECTORY("cannot change working directory"),
        FIRST_ARG_MUST_BE_STRING("first argument must be a character string"),
        MUST_BE_STRING_OR_FUNCTION("'%s' must be a character string or a function"),
        ARG_MUST_BE_CHARACTER_VECTOR_LENGTH_ONE("argument must be a character vector of length 1"),
        ARG_SHOULD_BE_CHARACTER_VECTOR_LENGTH_ONE("argument should be a character vector of length 1\nall but the first element will be ignored"),
        ZERO_LENGTH_VARIABLE("attempt to use zero-length variable name"),
        ARGUMENT_NOT_INTERPRETABLE_LOGICAL("argument is not interpretable as logical"),
        OPERATIONS_NUMERIC_LOGICAL_COMPLEX("operations are possible only for numeric, logical or complex types"),
        MATCH_VECTOR_ARGS("'match' requires vector arguments"),
        DIMNAMES_NONARRAY("'dimnames' applied to non-array"),
        DIMNAMES_LIST("'dimnames' must be a list"),
        NO_ARRAY_DIMNAMES("no 'dimnames' attribute for array"),
        MISSING_SUBSCRIPT("[[ ]] with missing subscript"),
        IMPROPER_SUBSCRIPT("[[ ]] improper number of subscripts"),
        ROWNAMES_STRING_OR_INT("row names must be 'character' or 'integer', not '%s'"),
        ONLY_FIRST_USED("numerical expression has %d elements: only the first used"),
        NO_SUCH_INDEX("no such index at level %d"),
        LIST_COERCION("'list' object cannot be coerced to type '%s'"),
        CAT_ARGUMENT_OF_TYPE("argument %d (type '%s') cannot be handled by 'cat'"),
        DATA_NOT_MULTIPLE_ROWS("data length [%d] is not a sub-multiple or multiple of the number of rows [%d]"),
        ARGUMENT_NOT_MATCH("supplied argument name '%s' does not match '%s'"),
        ARGUMENT_MISSING("argument \"%s\" is missing, with no default"),
        UNKNOWN_FUNCTION("could not find function \"%s\""),
        NON_FUNCTION("found non-function '%s'"),
        UNKNOWN_FUNCTION_USE_METHOD("no applicable method for '%s' applied to an object of class '%s'"),
        UNKNOWN_OBJECT("object '%s' not found"),
        INVALID_ARGUMENT("invalid '%s' argument"),
        INVALID_ARGUMENT_OF_TYPE("invalid '%s' argument of type '%s'"),
        INVALID_VALUE("invalid '%s' value"),
        INVALID_ARGUMENTS_NO_QUOTE("invalid %s arguments"),
        INVALID_SUBSCRIPT("invalid subscript"),
        INVALID_SUBSCRIPT_TYPE("invalid subscript type '%s'"),
        ARGUMENT_NOT_VECTOR("argument %d is not a vector"),
        CANNOT_COERCE("cannot coerce type '%s' to vector of type '%s'"),
        CANNOT_COERCE_RFFI("(%s) object cannot be coerced to type '%s'"),
        CANNOT_COERCE_QUOTED("'%s' object cannot be coerced to type '%s'"),
        ARGUMENT_ONLY_FIRST("argument '%s' has length > 1 and only the first element will be used"),
        ARGUMENT_ONLY_FIRST_1("only the first element of '%s' argument used"),
        ARGUMENT_WRONG_LENGTH("wrong length for argument"),
        ARGUMENT_WRONG_TYPE("wrong type for argument"),
        CANNOT_OPEN_FILE("cannot open file '%s': %s"),
        NOT_CONNECTION("'%s' is not a connection"),
        UNUSED_TEXTCONN("closing unused text connection %d (%s)"),
        INCOMPLETE_FINAL_LINE("incomplete final line found on '%s'"),
        CANNOT_OPEN_PIPE("cannot open pipe() cmd '%s': %s"),
        INVALID_TYPE_ARGUMENT("invalid 'type' (%s) of argument"),
        ATTRIBUTE_VECTOR_SAME_LENGTH("'%s' attribute [%d] must be the same length as the vector [%d]"),
        SCAN_UNEXPECTED("scan() expected '%s', got '%s'"),
        MUST_BE_ENVIRON("'%s' must be an environment"),
        MUST_BE_ENVIRON2("%s must be an environment"),
        MUST_BE_INTEGER("'%s' must be an integer"),
        UNUSED_ARGUMENT("unused argument (%s)"),
        UNUSED_ARGUMENTS("unused arguments (%s)"),
        INFINITE_MISSING_VALUES("infinite or missing values in '%s'"),
        CALLOC_COULD_NOT_ALLOCATE("'Calloc' could not allocate memory (%s of %d bytes)"),
        NON_SQUARE_MATRIX("non-square matrix in '%s'"),
        LAPACK_ERROR("error code %d from Lapack routine '%s'"),
        VALUE_OUT_OF_RANGE("value out of range in '%s'"),
        MUST_BE_STRING("'%s' must be a character string"),
        ARGUMENT_MUST_BE_STRING("argument '%s' must be a character string"),
        ARGUMENT_MUST_BE_RAW_VECTOR("argument '%s' must be a raw vector"),
        MUST_BE_NONNULL_STRING("'%s' must be non-null character string"),
        IS_OF_WRONG_LENGTH("'%s' is of wrong length %d (!= %d)"),
        IS_OF_WRONG_ARITY("%d argument passed to '%s' which requires %d"),
        OBJECT_NOT_SUBSETTABLE("object of type '%s' is not subsettable"),
        WRONG_ARGS_SUBSET_ENV("wrong arguments for subsetting an environment"),
        DIMS_DONT_MATCH_LENGTH("dims [product %d] do not match the length of object [%d]"),
        DIMNAMES_DONT_MATCH_DIMS("length of 'dimnames' [%d] must match that of 'dims' [%d]"),
        DIMNAMES_DONT_MATCH_EXTENT("length of 'dimnames' [%d] not equal to array extent"),
        MUST_BE_ATOMIC("'%s' must be atomic"),
        MUST_BE_NULL_OR_STRING("'%s' must be NULL or a character vector"),
        IS_NULL("'%s' is NULL"),
        MUST_BE_SCALAR("'%s' must be of length 1"),
        ROWS_MUST_MATCH("number of rows of matrices must match (see arg %d)"),
        COLS_MUST_MATCH("number of columns of matrices must match (see arg %d)"),
        ROWS_NOT_MULTIPLE("number of rows of result is not a multiple of vector length (arg %d)"),
        ARG_ONE_OF("'%s' should be one of %s"),
        MUST_BE_SQUARE_MATRIX("'%s' must be a square matrix"),
        MUST_BE_SQUARE_MATRIX_SPEC("'%s' (%d x %d) must be square"),
        NON_MATRIX("non-matrix argument to '%s'"),
        NON_NUMERIC_ARGUMENT_TO("non-numeric argument to '%s'"),
        DIMS_GT_ZERO("'%s' must have dims > 0"),
        NOT_POSITIVE_DEFINITE("the leading minor of order %d is not positive definite"),
        LAPACK_INVALID_VALUE("argument %d of Lapack routine %s had invalid value"),
        LAPACK_ZERO_INVERSE("element (%d, %d) is zero, so the inverse cannot be computed"),
        LAPACK_EXACTLY_SINGULAR("Lapack routine %s: system is exactly singular: U[%d,%d] = 0"),
        LAPACK_CHOL_NOT_POSITIVE_DEFINITE("the leading minor of order %d is not positive definite"),
        LAPACK_CHOL_RANK_DEF_OR_INDEF("the matrix is either rank-deficient or indefinite"),
        SYSTEM_COMP_SINGULAR("system is computationally singular: reciprocal condition number = %g"),
        RHS_SHOULD_HAVE_ROWS("right-hand side should have %d not %d rows"),
        SAME_NUMBER_ROWS("'%s' and '%s' must have the same number of rows"),
        EXACT_SINGULARITY("exact singularity in '%s'"),
        SINGULAR_SOLVE("singular matrix '%s' in solve"),
        SEED_TYPE("'.Random.seed' is not an integer vector but of type '%s', so ignored"),
        INVALID_NORMAL_TYPE_IN_RGNKIND("invalid Normal type in 'RNGkind'"),
        INVALID_SAMPLE_TYPE_IN_RGNKIND("invalid sample type in 'RNGkind'"),
        INVALID_USE("invalid use of '%s'"),
        FORMAL_MATCHED_MULTIPLE("formal argument \"%s\" matched by multiple actual arguments"),
        ARGUMENT_MATCHES_MULTIPLE("argument %d matches multiple formal arguments"),
        ARGUMENT_EMPTY("argument %d is empty"),
        REPEATED_FORMAL("repeated formal argument '%s'"),
        NOT_A_MATRIX_UPDATE_CLASS("invalid to set the class to matrix unless the dimension attribute is of length 2 (was %d)"),
        NOT_ARRAY_UPDATE_CLASS("cannot set class to \"array\" unless the dimension attribute has length > 0"),
        SET_INVALID_ATTR("attempt to set invalid '%s' attribute"),
        NOT_LEN_ONE_LOGICAL_VECTOR("'%s' must be a length 1 logical vector"),
        TOO_LONG_CLASS_NAME("class name too long in '%s'"),
        NON_STRING_GENERIC("'generic' argument must be a character string"),
        OBJECT_NOT_SPECIFIED("object not specified"),
        NO_METHOD_FOUND("no method to invoke"),
        GEN_FUNCTION_NOT_SPECIFIED("generic function not specified"),
        DUPLICATE_SWITCH_DEFAULT("duplicate 'switch' defaults: '%s' and '%s'"),
        NO_ALTERNATIVE_IN_SWITCH("empty alternative in numeric switch"),
        NO_ALTERNATIVES_IN_SWITCH("'switch' with no alternatives"),
        EXPR_NOT_LENGTH_ONE("EXPR must be a length 1 vector"),
        EXPR_MISSING("'EXPR' is missing"),
        INVALID_STORAGE_MODE_UPDATE("invalid to change the storage mode of a factor"),
        USE_DEFUNCT("use of '%s' is defunct: use %s instead"),
        NCOL_ZERO("nc(0 for non-null data"),
        NROW_ZERO("nr(0 for non-null data"),
        CANNOT_EXCEED_X("'%s' cannot exceed %s(x) = %d"),
        SAMPLE_LARGER_THAN_POPULATION("cannot take a sample larger than the population when 'replace(FALSE'\n"),
        ERROR_IN_SAMPLE("Error in sample.int(x, size, replace, prob) :  "),
        INCORRECT_NUM_PROB("incorrect number of probabilities"),
        NA_IN_PROB_VECTOR("NA in probability vector"),
        NEGATIVE_PROBABILITY("negative probability"),
        NO_POSITIVE_PROBABILITIES("no positive probabilities"),
        QBETA_ACURACY_WARNING("qbeta(a, *) =: x0 with |pbeta(x0,*%s) - alpha| = %.5g is not accurate"),
        PCHISQ_NOT_CONVERGED_WARNING("pnchisq(x=%g, ..): not converged in %d iter."),
        NON_POSITIVE_FILL("non-positive 'fill' argument will be ignored"),
        MUST_BE_ONE_BYTE("invalid %s: must be one byte"),
        INVALID_DECIMAL_SEP("invalid decimal separator"),
        INVALID_QUOTE_SYMBOL("invalid quote symbol set"),
        INVALID_TIES_FOR_RANK("invalid ties.method for rank() [should never happen]"),
        UNIMPLEMENTED_TYPE_IN_GREATER("unimplemented type '%s' in greater"),
        RANK_LARGE_N("parameter 'n' is greater than length(x), GnuR output is non-deterministic, FastR will use n=length(x)"),
        ALGORITHM_FOR_SIZE_N_DIV_2("This algorithm is for size <= n/2"),
        // below: not exactly GNU-R message
        TOO_FEW_POSITIVE_PROBABILITY("too few positive probabilities"),
        DOTS_BOUNDS("The ... list does not contain %s elements"),
        REFERENCE_NONEXISTENT("reference to non-existent argument %d"),
        UNRECOGNIZED_FORMAT("unrecognized format specification '%s'"),
        INVALID_FORMAT_LOGICAL("invalid format '%s'; use format %%d or %%i for logical objects"),
        INVALID_FORMAT_INTEGER("invalid format '%s'; use format %%d, %%i, %%o, %%x or %%X for integer objects"),
        POS_NOT_ALLOWED_WITH_NUMERIC("pos argument not allowed with a numeric value"),
        OBJ_CANNOT_BE_ATTRIBUTED("polyglot value cannot be attributed"),
        CANNOT_COERCE_EXTERNAL_OBJECT_TO_VECTOR("no method for coercing this polyglot value to a %s"),
        NO_METHOD_ASSIGNING_SUBSET_S4("no method for assigning subsets of this S4 class"),
        CANNOT_COERCE_S4_TO_VECTOR("no method for coercing this S4 class to a vector"),
        // the following list is incomplete (but like GNU-R)
        INVALID_FORMAT_DOUBLE("invalid format '%s'; use format %%f, %%e, %%g or %%a for numeric objects"),
        INVALID_LOGICAL("'%s' must be TRUE or FALSE"),
        INVALID_FORMAT_STRING("invalid format '%s'; use format %%s for character objects"),
        MUST_BE_CHARACTER("'%s' must be of mode character"),
        FIRST_ARGUMENT_MUST_BE_CHARACTER("the first argument must be of mode character"),
        ALL_ATTRIBUTES_NAMES("all attributes must have names [%d does not]"),
        INVALID_REGEXP("invalid regular expression '%s'"),
        PCRE_FULLINFO_RETURNED("'pcre_fullinfo' returned '%s'"),
        INVALID_REGEXP_REASON("invalid regular expression '%s': %s"),
        COERCING_ARGUMENT("coercing argument of type '%s' to %s"),
        MUST_BE_TRUE_FALSE_ENVIRONMENT("'%s' must be TRUE, FALSE or an environment"),
        UNKNOWN_OBJECT_MODE("object '%s' of mode '%s' was not found"),
        WRONG_LENGTH_ARG("wrong length for '%s' argument"),
        INVALID_TYPE_IN("invalid '%s' type in 'x %s y'"),
        DOT_DOT_MISSING("'..%d' is missing"),
        DOT_DOT_INDEX_ZERO_OR_LESS("indexing '...' with non-positive index %d"),
        DOT_DOT_INVALID_INDEX("indexing '...' with an invalid index"),
        DOT_DOT_SHORT("the ... list contains fewer than %d elements"),
        DOT_DOT_NONE("the ... list contains fewer than 1 element"),
        NO_DOT_DOT("..%d used in an incorrect context, no ... to look in"),
        NO_DOT_DOT_CNTXT("incorrect context: the current call has no '...' to look in"),
        NO_DOT_DOT_DOT("'...' used in an incorrect context"),
        NO_LIST_FOR_CDR("'nthcdr' needs a list to CDR down"),
        INVALID_TYPE_LENGTH("invalid type/length (%s/%d) in vector allocation"),
        SUBASSIGN_TYPE_FIX("incompatible types (from %s to %s) in subassignment type fix"),
        SUBSCRIPT_TYPES("incompatible types (from %s to %s) in [[ assignment"),
        INCOMPATIBLE_METHODS("incompatible methods (\"%s\", \"%s\") for \"%s\""),
        RECURSIVE_INDEXING_FAILED("recursive indexing failed at level %d"),
        ARGUMENT_PASSED("%d argument passed to %s which requires %d"),
        ARGUMENTS_PASSED("%d arguments passed to %s which requires %d"),
        ARGUMENT_IGNORED("argument '%s' will be ignored"),
        NOT_CHARACTER_VECTOR("'%s' must be a character vector"),
        WRONG_WINSLASH("'winslash' must be '/' or '\\\\\\\\'"),
        CANNOT_MAKE_VECTOR_OF_MODE("vector: cannot make a vector of mode '%s'"),
        SET_ROWNAMES_NO_DIMS("attempt to set 'rownames' on an object with no dimensions"),
        COLUMNS_NOT_MULTIPLE("number of columns of result is not a multiple of vector length (arg %d)"),
        DATA_FRAMES_SUBSET_ACCESS("data frames subset access not supported"),
        CANNOT_ASSIGN_IN_EMPTY_ENV("cannot assign values in the empty environment"),
        CANNOT_OPEN_CONNECTION("cannot open the connection"),
        ERROR_READING_CONNECTION("error reading connection: %s"),
        ERROR_WRITING_CONNECTION("error writing connection: %s"),
        ERROR_FLUSHING_CONNECTION("error flushing connection: %s"),
        ALREADY_OPEN_CONNECTION("connection is already open"),
        NO_ITEM_NAMED("no item called \"%s\" on the search list"),
        INVALID_OBJECT("invalid object for 'as.environment'"),
        EMPTY_NO_PARENT("the empty environment has no parent"),
        ARG_NOT_AN_ENVIRONMENT("argument to '%s' is not an environment"),
        NOT_AN_ENVIRONMENT("not an environment"),
        NOT_A_SYMBOL("not a symbol"),
        CANNOT_SET_PARENT("cannot set the parent of the empty environment"),
        INVALID_OR_UNIMPLEMENTED_ARGUMENTS("invalid or unimplemented arguments"),
        INVALID_LIST_FOR_SUBSTITUTION("invalid list for substitution"),
        NOTHING_TO_LINK("nothing to link"),
        FROM_TO_DIFFERENT("'from' and 'to' are of different lengths"),
        NA_IN_FOREIGN_FUNCTION_CALL("NAs in foreign function call (arg %d)"),
        NA_NAN_INF_IN_FOREIGN_FUNCTION_CALL("NA/NaN/Inf in foreign function call (arg %d)"),
        INCORRECT_ARG("incorrect arguments to %s"),
        UNIMPLEMENTED_ARG_TYPE("unimplemented argument type (arg %d)"),
        C_SYMBOL_NOT_IN_TABLE("C symbol name \"%s\" not in load table"),
        FORTRAN_SYMBOL_NOT_IN_TABLE("Fortran symbol name \"%s\" not in load table"),
        SYMBOL_NOT_IN_TABLE("\"%s\" not available for .%s() for package \"%s\""),
        NOT_THAT_MANY_FRAMES("not that many frames on the stack"),
        UNIMPLEMENTED_ARGUMENT_TYPE("unimplemented argument type"),
        MUST_BE_SQUARE_NUMERIC("'%s' must be a square numeric matrix"),
        MUST_BE_NUMERIC_MATRIX("'%s' must be a numeric matrix"),
        PARSE_ERROR("parse error"),
        SEED_NOT_VALID_INT("supplied seed is not a valid integer"),
        POSITIVE_CONTEXTS("number of contexts must be positive"),
        NORMALIZE_PATH_NOSUCH("path[%d]=\"%s\": No such file or directory"),
        ARGS_MUST_BE_NAMED("all arguments must be named"),
        INVALID_INTERNAL("invalid .Internal() argument"),
        NO_SUCH_INTERNAL("there is no .Internal function '%s'"),
        NO_SUCH_PRIMITIVE("no such primitive function"),
        INVALID_VALUE_FOR("invalid value for '%s'"),
        OPTION_CANNOT_BE_DELETED("option '%s' cannot be deleted"),
        IMP_EXP_NAMES_MATCH("length of import and export names must match"),
        ENV_ADD_BINDINGS("cannot add bindings to a locked environment"),
        ENV_REMOVE_BINDINGS("cannot remove bindings from a locked environment"),
        ENV_REMOVE_VARIABLES("cannot remove variables from the %s environment"),
        ENV_CHANGE_BINDING("cannot change value of locked binding for '%s'"),
        ENV_ASSIGN_EMPTY("cannot assign values in the empty environment"),
        ENV_DETACH_BASE("detaching \"package:base\" is not allowed"),
        ENV_SUBSCRIPT("subscript out of range"),
        DLL_LOAD_ERROR("unable to load shared object '%s'\n  %s"),
        DLL_NOT_LOADED("shared object '%s' was not loaded"),
        DLL_RINIT_ERROR("package 'init' method failed"),
        RNG_BAD_KIND("RNG kind %s is not available"),
        RNG_NOT_IMPL_KIND("unimplemented RNG kind %d"),
        RNG_READ_SEEDS("cannot read seeds unless 'user_unif_nseed' is supplied"),
        RNG_SYMBOL("%s not found in user rng library"),
        CUMMAX_UNDEFINED_FOR_COMPLEX("'cummax' not defined for complex numbers"),
        CUMMIN_UNDEFINED_FOR_COMPLEX("'cummin' not defined for complex numbers"),
        OP_NOT_DEFINED_FOR_S4_CLASS("%s operator not defined for this S4 class"),
        NMAX_LESS_THAN_ONE("'nmax' must be positive"),
        CHAR_VEC_ARGUMENT("a character vector argument expected"),
        QUOTE_G_ONE("only the first character of 'quote' will be used"),
        UNEXPECTED("unexpected '%s' in \"%s\""),
        UNEXPECTED_LINE("unexpected '%s' in \"%s\" (line %d)"),
        FIRST_ELEMENT_USED("first element used of '%s' argument"),
        MUST_BE_COERCIBLE_INTEGER("argument must be coercible to non-negative integer"),
        DEFAULT_METHOD_NOT_IMPLEMENTED_FOR_TYPE("default method not implemented for type '%s'"),
        ARG_MUST_BE_CLOSURE("argument must be a closure"),
        NOT_DEBUGGED("argument is not being debugged"),
        ADDING_INVALID_CLASS("adding class \"%s\" to an invalid object"),
        IS_NA_TO_NON_VECTOR("is.na() applied to non-(list or vector) of type '%s'"),
        NOT_MEANINGFUL_FOR_FACTORS("\u2018%s\u2019 not meaningful for factors"),
        INPUTS_DIFFERENT_LENGTHS("inputs of different lengths"),
        MATRIX_LIKE_REQUIRED("a matrix-like object is required as argument to '%s'"),
        NOT_MEANINGFUL_FOR_ORDERED_FACTORS("'%s' is not meaningful for ordered factors"),
        UNSUPPORTED_URL_SCHEME("unsupported URL scheme"),
        CANNOT_CLOSE_STANDARD_CONNECTIONS("cannot close standard connections"),
        FULL_PRECISION("full precision may not have been achieved in '%s'"),
        ATTACH_BAD_TYPE("'attach' only works for lists, data frames and environments"),
        STRING_ARGUMENT_REQUIRED("string argument required"),
        FILE_APPEND_TO("nothing to append to"),
        FILE_OPEN_TMP("file(\"\") only supports open = \"w+\" and open = \"w+b\": using the former"),
        FILE_APPEND_WRITE("write error during file append"),
        REQUIRES_CHAR_VECTOR("'%s' requires a character vector"),
        ARGUMENT_NOT_CHAR_VECTOR("argument is not a character vector"),
        NOT_VALID_NAMES("not a valid named list"),
        CHAR_ARGUMENT("character argument expected"),
        MUST_BE_FINITE("'%s' must be a finite number"),
        UNKNOWN_VALUE("unknown '%s' value"),
        MUST_BE_VECTOR("'%s' must be a vector"),
        NO_SUCH_CONNECTION("there is no connection %d"),
        REQUIRES_DLLINFO("R_getRegisteredRoutines() expects a DllInfo reference"),
        NULL_DLLINFO("NULL value passed for DllInfo"),
        REQUIRES_NAME_DLLINFO("must pass package name or DllInfo reference"),
        APPLY_NON_FUNCTION("attempt to apply non-function"),
        MINIMIZE_NON_FUNCTION("attempt to minimize non-function"),
        NO_INDEX("no index specified"),
        INVALID_ARG_NUMBER("%s: invalid number of arguments"),
        BAD_HANDLER_DATA("bad handler data"),
        DEPARSE_INVALID_CUTOFF("invalid 'cutoff' value for 'deparse', using default"),
        FILE_CANNOT_CREATE("cannot create file '%s'"),
        FILE_CANNOT_LINK("  cannot link '%s' to '%s', reason %s"),
        FILE_CANNOT_COPY("  cannot copy '%s' to '%s', reason %s"),
        FILE_CANNOT_REMOVE("  cannot remove file '%s'"),
        FILE_CANNOT_RENAME("  cannot rename file '%s' to '%s'"),
        FILE_COPY_RECURSIVE_IGNORED("'recursive' will be ignored as 'to' is not a single existing directory"),
        FILE_OPEN_ERROR("unable to open file"),
        ALREADY_EXISTS("'%s' already exists"),
        DIR_CANNOT_CREATE("cannot create dir '%s'"),
        DIR_CANNOT_CREATE_NO_SUCH("cannot create dir '%s', reason 'No such file or directory'"),
        IMPOSSIBLE_SUBSTITUTE("substitute result cannot be represented"),
        PACKAGE_AVAILABLE("'%s' may not be available when loading"),
        BAD_RESTART("bad restart"),
        RESTART_NOT_ON_STACK("restart not on stack"),
        PERFORMANCE("performance problem: %s"),
        MUST_BE_SMALL_INT("argument '%s' must be a small integer"),
        NO_INTEROP("'%s' is not an object that supports interoperability (class %s)"),
        NO_IMPORT_OBJECT("'%s' is not an exported object"),
        NO_FUNCTION_RETURN("no function to return from, jumping to top level"),
        REG_FINALIZER_FIRST("first argument must be environment or external pointer"),
        REG_FINALIZER_SECOND("second argument must be a function"),
        REG_FINALIZER_THIRD("third argument must be 'TRUE' or 'FALSE'"),
        LAZY_LOAD_DB_CORRUPT("lazy-load database '%s' is corrupt or unreadable"),
        MAGIC_EMPTY("restore file may be empty -- no data loaded"),
        MAGIC_TOONEW("restore file may be from a newer version of R -- no data loaded"),
        MAGIC_CORRUPT("bad restore file magic number (file may be corrupted) -- no data loaded"),
        SLOT_BASIC_CLASS("trying to get slot \"%s\" from an object of a basic class (\"%s\") with no slots"),
        SLOT_NON_S4("trying to get slot \"%s\" from an object (class \"%s\") that is not an S4 object "),
        SLOT_CANNOT_GET("cannot get a slot (\"%s\") from an object of type \"%s\""),
        SLOT_NONE("no slot of name \"%s\" for this object of class \"%s\""),
        SLOT_INVALID_TYPE_OR_LEN("invalid type or length for slot name"),
        S4OBJECT_NX_ENVIRONMENT("S4 object does not extend class \"environment\""),
        NOT_A_SLOT("'%s' is not a slot in class ”%s”"),
        NS_ALREADY_REG("namespace already registered"),
        NS_NOTREG("namespace not registered"),
        SLOT_INVALID_TYPE("invalid type '%s' for slot name"),
        OBJECT_FROM_VIRTUAL("trying to generate an object from a virtual class (\"%s\")"),
        CLASS_INVALID_S3("object of class \"%s\" does not correspond to a valid S3 object"),
        STD_GENERIC_WRONG_CALL("call to standardGeneric(\"%s\") apparently not from the body of that generic function"),
        EXPECTED_GENERIC("expected a generic function or a primitive for dispatch, got an object of class \"%s\""),
        NOT_ALL_SAME_LENGTH("not all arguments have the same length"),
        NO_INPUT_NUMBER_OF_CASES("no input has determined the number of cases"),
        SINGLE_STRING_TOO_LONG("'%s' must be a single string (got a character vector of length %d)"),
        NON_EMPTY_STRING("'%s' must be a non-empty string; got an empty string"),
        SINGLE_STRING_WRONG_TYPE("'%s' must be a single string (got an object of class \"%s\")"),
        NO_GENERIC_FUN("no generic function definition found for '%s'"),
        NO_GENERIC_FUN_IN_ENV("no generic function definition found for '%s' in the supplied environment"),
        INVALID_PRIM_METHOD_CODE("invalid primitive methods code (\"%s\"): should be \"clear\", \"reset\", \"set\", or \"suppress\""),
        PRIM_GENERIC_NOT_FUNCTION("the formal definition of a primitive generic must be a function object (got type '%s')"),
        NON_INTEGER_VALUE("non-integer value %s qualified with L; using numeric value"),
        NON_INTEGER_N("non-integer %s = %f"),
        INTEGER_VALUE_DECIMAL("integer literal %s contains decimal; using numeric value"),
        INTEGER_VALUE_UNNECESARY_DECIMAL("integer literal %s contains unnecessary decimal point"),
        NON_LANG_ASSIGNMENT_TARGET("target of assignment expands to non-language object"),
        INVALID_LARGE_NA_VALUE("invalid '%s' value (too large or NA)"),
        INVALID_NEGATIVE_VALUE("invalid '%s' value (< 0)"),
        POSITIVE_LENGTH("'%s' must have positive length"),
        BROWSER_QUIT("cannot quit from browser"),
        QUIT_ASK("one of \"yes\", \"no\", \"ask\" or \"default\" expected."),
        QUIT_SAVE("unrecognized value of 'save'"),
        QUIT_ASK_INTERACTIVE("save=\"ask\" in non-interactive use: command-line default will be used"),
        QUIT_INVALID_STATUS("invalid 'status', 0 assumed"),
        QUIT_INVALID_RUNLAST("invalid 'runLast', FALSE assumed"),
        ENVIRONMENTS_COERCE("environments cannot be coerced to other types"),
        ROWSUM_NAMES_NOT_CHAR("row names are not character"),
        ROWSUM_NON_NUMERIC("non-numeric matrix in rowsum(): this should not happen"),
        ARGUMENTS_REQUIRED_COUNT("%d arguments to '%s' which requires %d"),
        ARG_IS_NOT_OF_MODE("argument is not of mode %s"),
        ARGUMENT_LENGTH_0("argument of length 0"),
        MUST_BE_VECTOR_BUT_WAS("'%s' must be of a vector type, was '%s'"),
        SYSTEM_CHAR_ARG("non-empty character argument expected"),
        SYSTEM_INTERN_NOT_NA("'intern' must be logical and not NA"),
        NO_SUCH_FILE("cannot open file '%s': No such file or directory"),
        NON_STRING_ARG_TO_INTERNAL_PASTE("non-string argument to .Internal(paste)"),
        INVALID_STRING_IN_STOP(" [invalid string in stop(.)]"),
        INVALID_STRING_IN_WARNING(" [invalid string in warning(.)]"),
        ERR_MSG_MUST_BE_STRING("error message must be a character string"),
        ERR_MSG_BAD("bad error message"),
        BAD_ENVIRONMENT("bad %s environment argument"),
        CANNOT_BE_LENGTH("'%s' cannot be of length %d"),
        SECOND_ARGUMENT_LIST("second argument must be a list"),
        DOES_NOT_HAVE_DIMNAMES("'%s' does not have named dimnames"),
        ATTEMPT_TO_REPLICATE("attempt to replicate an object of type '%s'"),
        ATTEMPT_TO_REPLICATE_NO_VECTOR("attempt to replicate non-vector"),
        INCORRECT_ARG_TYPE("incorrect type for %s argument"),
        INVALID_ARG_OF_LENGTH("invalid %s argument of length %d"),
        INVALID_ARG("invalid %s argument"),
        INVALID_FILENAME_PATTERN("invalid filename pattern"),
        INVALID_FILENAME_SPECIFICATION("invalid filename specification"),
        INVALID_FILE_EXT("invalid file extension"),
        NO("no '%s'"),
        APPLIES_TO_VECTORS("%s applies only to vectors"),
        NOT_A_VECTOR("argument %d is not a vector"),
        RADIX_SORT_DEC_MATCH("length(decreasing) must match the number of order arguments"),
        RADIX_SORT_DEC_NOT_LOGICAL("'decreasing' elements must be TRUE or FALSE"),
        COERCE_NON_FACTOR("attempting to coerce non-factor"),
        MALFORMED_FACTOR("malformed factor"),
        GAP_MUST_BE_NON_NEGATIVE("'gap' must be non-negative integer"),
        WRONG_PCRE_INFO("'pcre_fullinfo' returned '%d' "),
        BAD_FUNCTION_EXPR("badly formed function expression"),
        FIRST_ELEMENT_ONLY("only first element of '%s' argument used"),
        MUST_BE_GE_ONE("'%s' must be of length >= 1"),
        MORE_THAN_ONE_MATCH("there is more than one match in '%s'"),
        ARG_MUST_BE_CHARACTER("argument '%s' must be character"),
        INCORRECT_NOF_ARGS("Incorrect number of arguments (%d), expecting %d for '%s'"),
        MACRO_CAN_BE_APPLIED_TO("%s can only be applied to a '%s', not a '%s'"),
        LOSS_OF_ACCURACY_MOD("probable complete loss of accuracy in modulus"),
        LENGTH_MISAPPLIED("LENGTH or similar applied to %s object"),
        TOO_MANY_ARGS("too many arguments"),
        UNIMPLEMENTED_TYPE_IN_R("type \"%s\" unimplemented in R"),
        NOT_AN_OUTPUT_RAW_CONNECTION("'con' is not an output rawConnection"),
        NOT_A_RAW_CONNECTION("'con' is not a rawConnection"),
        SEEK_OUTSITE_RAW_CONNECTION("attempt to seek outside the range of the raw connection"),
        VECTOR_IS_TOO_LARGE("vector is too large"),
        SEEK_NOT_RELEVANT_FOR_TEXT_CON("seek is not relevant for text connection"),
        NOT_ENABLED_FOR_THIS_CONN("'%s' not enabled for this connection"),
        CANNOT_OPEN_FIFO("cannot open fifo '%s'"),
        UNSUPPORTED_ENCODING_CONVERSION("unsupported conversion from '%s' to '%s'"),
        UNABLE_TO_RESOLVE("unable to resolve '%s'"),
        LINE_CONTAINS_EMBEDDED_NULLS("line %d appears to contain an embedded nul"),
        UNSUPPORTED_URL_METHOD("method = \"%s\" is not supported"),
        CANNOT_REPLICATE_NULL("cannot replicate NULL to a non-zero length"),
        TRUNCATE_ONLY_WRITE_CONNECTION("can only truncate connections open for writing"),
        TRUNCATE_ONLY_OPEN_CONN("can only truncate an open connection"),
        TRUNCATE_NOT_ENABLED("truncation not enabled for this connection"),
        TRUNCATE_UNSUPPORTED_FOR_CONN("cannot truncate connection: %s"),
        INCOMPLETE_STRING_AT_EOF_DISCARDED("incomplete string at end of file has been discarded"),
        INVALID_CHANNEL_OBJECT("invalid channel object (ByteChannel expected)"),
        INVALID_TAG("invalid tag"),
        INVALID_VARIABLE_NAMES("invalid variable names"),
        INVALID_EXPRESSION("invalid expression in '%s'"),
        INVALID_EXPRESSION_TYPE("expression must not be type '%s'"),
        NOT_IN_DERIVATIVE_TABLE("Function '%s' is not in the derivatives table"),
        CANNOT_ADD_BINDINGS("cannot add bindings to a locked environment"),
        SYMBOL_HAS_REGULAR_BINDING("symbol already has a regular binding"),
        CANNOT_CHANGE_LOCKED_ACTIVE_BINDING("cannot change active binding if binding is locked"),
        NO_BINDING_FOR("no binding for \"%s\""),
        INVALID_SUBSTRING_ARGS("invalid substring arguments"),
        OBJECT_SIZE_ESTIMATE("The object size is only estimated."),
        REPLACING_IN_NON_CHAR_OBJ("replacing substrings in a non-character object"),
        FILE_NOT_FOUND_IN_ZIP("requested file not found in the zip file"),
        LIST_NO_VALID_NAMES("list argument has no valid names"),
        VALUES_MUST_BE_LENGTH("values must be length %s,\n but FUN(X[[%d]]) result is length %s"),
        OS_REQUEST_LOCALE("OS reports request to set locale to \"%s\" cannot be honored"),
        INVALID_TYPE("invalid type (%s) for '%s' (must be a %s)"),
        NOT_A_LIST_OF_SOCKETS("not a list of sockets"),
        NOT_A_SOCKET_CONNECTION("not a socket connection"),
        UNEXPECTED_OBJ_IN_SIZE("Unexpected object type %s while calculating estimated object size."),
        BAD_CONSTANT_COUNT("bad constant count"),
        MUST_BE_MULTIPLE("argument '%s' must be a multiple of %d long"),
        MUSTNOT_CONTAIN_NAS("argument '%s' must not contain NAs"),
        VERSION_N_NOT_SUPPORTED("version %d not supported"),
        ATOMIC_VECTOR_ARGUMENTS_ONLY("atomic vector arguments only"),
        MUST_BE_COMPLEX_MATRIX("'%s' must be a complex matrix"),
        INVALID_FORMAL_ARG_LIST("invalid formal argument list for \"%s\""),
        SINGULAR_BACKSOLVE("singular matrix in 'backsolve'. First zero in diagonal [%d]"),
        EOF_AFTER_BACKSLASH("\\ followed by EOF"),
        DERIV_OVER_N_MAX("deriv = %d > %d (= n_max)"),
        BESSEL_ARG_RANGE("bessel_%s(%g): ncalc (=%d) != nb (=%d); alpha=%g. Arg. out of range?"),
        BESSEL_PRECISION_LOST("bessel_%s(%g,nu=%g): precision lost in result"),
        BESSEL_NU_TOO_LARGE("bessel%s(x, nu): nu=%g too large for bessel_%s() algorithm"),
        NOT_LESS_THAN("'%s' not less than '%s'"),
        NA_NOT_ALLOWED("NA value for '%s' is not allowed"),
        NA_REPLACED("NA replaced by maximum positive value"),
        NA_INF_REPLACED("-Inf replaced by maximally negative value"),
        MINUS_INF_REPLACED("NA/Inf replaced by maximum positive value"),
        INVALID_FUNCTION_VALUE("invalid function value in '%s'"),
        LINE_MALFORMED("Line starting '%s ...' is malformed!"),
        IS_NOT_GRAPHICAL_PAR("\"%s\" is not a graphical parameter"),
        GRAPHICAL_PAR_CANNOT_BE_SET("graphical parameter \"%s\" cannot be set"),
        COMMAND_TIMED_OUT("command '%s' timed out after %ds"),
        NO_TAG_IN_SET_ATTRIB(
                        "SET_ATTRIB: tag in the attributes pairlist must be a symbol. %s given. It is possible that the code intends to set the TAG after the call to SET_ATTRIB, but this is not supported in FastR."),
        WRONG_ARGS_COMBINATION("Wrong arguments combination, please refer to ?%s for more details."),
        COULD_NOT_FIND_LANGUAGE("Could not find language corresponding to extension '%s', you can specify the language id explicitly, please refer to ?%s for more details."),
        LANGUAGE_NOT_AVAILABLE("Language with id '%s' is not available. Did you start R with --polyglot or use allowPolyglotAccess when building the context?"),
        POLYGLOT_BINDING_NOT_AVAILABLE("Polyglot bindings are not accessible for this language. Use --polyglot or allowPolyglotAccess when building the context."),
        NO_LANGUAGE_PROVIDED("No language id provided, please refer to ?%s for more details."),
        NO_CODE_OR_PATH_PROVIDED("No code or path provided, please refer to ?%s for more details."),
        LENGTH_OF_NULL_UNCHANGED("length of NULL cannot be changed"),
        CANNOT_SET_LENGTH("cannot set length of non-(vector or list)"),
        LONG_VECTOR_NOT_SUPPORTED("long vector '%s' is not supported"),
        CANNOT_SET_ATTR_ON("cannot set attribute on a %s"),
        TSP_NUMERIC_LENGTH3("'tsp' attribute must be numeric of length three"),
        INVALID_TSP("invalid time series parameters specified"),
        CANNOT_ASSIGN_EMPTY_VECTOR("cannot assign '%s' to zero-length vector"),
        DATE_TIME_CONVERSION_SPEC_NOT_IMPLEMENTED("Date time conversion format '%s' is not implemented in FastR yet. Please submit an issue at https://github.com/oracle/fastr."),
        CANNOT_ALLOCATE_VECTOR_GB("cannot allocate vector of size %.1f Gb"),
        INVALID_POLYNOMIAL_COEFFICIENT("invalid polynomial coefficient"),
        ROOT_FINDING_FAILED("root finding code failed"),
        IS_GZCON("this is already a 'gzcon' connection"),
        USING_TEXT_MODE_NOT_WORK_CORRECTLY("using a text-mode '%s' connection may not work correctly"),
        CAN_USE_ONLY_R_OR_W_CONNECTIONS("can only use read- or write- binary connections"),
        CANNOT_CREATE_GZCON("cannot create a '%s' connection from a writable %s; maybe use %s");

        public final String message;
        final boolean hasArgs;

        Message(String message) {
            this.message = message;
            hasArgs = message.indexOf('%') >= 0;
        }
    }
}
