/*
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * Copyright (c) 2012-2014, Purdue University
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test;

import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Engine;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Result;

import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RSuicide;
import com.oracle.truffle.r.runtime.ResourceHandlerFactory;
import com.oracle.truffle.r.runtime.context.FastROptions;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.context.RContext.ContextKind;
import com.oracle.truffle.r.test.generate.FastRContext;
import com.oracle.truffle.r.test.generate.FastRSession;
import com.oracle.truffle.r.test.generate.GnuROneShotRSession;
import static com.oracle.truffle.r.test.generate.RSession.USE_DEFAULT_TIMEOUT;
import com.oracle.truffle.r.test.generate.TestOutputManager;
import java.util.Map;

/**
 * Base class for all unit tests. The unit tests are actually arranged as a collection of
 * micro-tests of the form {@code assertXXX(String test)}, organized into groups under one JUnit
 * test method, i.e., annotated with {@link Test}. Some of the micro-tests are generated dynamically
 * from templates.
 *
 * Given this two-level structure, it is important that a failing micro-test does not fail the
 * entire JUnit test, as this will prevent the subsequent micro-tests from running at all. Instead
 * failure is handled by setting the {@link #microTestFailed} field, and JUnit failure is indicated
 * in the {@link #afterTest()} method.
 */
public class TestBase {

    public static final boolean ProcessFailedTests = Boolean.getBoolean("ProcessFailedTests");

    static {
        if (ProcessFailedTests) {
            System.out.println("Re-trying failed unit tests (ProcessFailedTests=true)");
        }
    }

    /**
     * When {@link #ProcessFailedTests} is set to true this flag further limits the tests executed
     * to those with {@link Ignored#Unknown} flag.
     */
    public static final boolean IgnoredUnknownOnlyTests = Boolean.getBoolean("IgnoredUnknownOnlyTests");
    /**
     * When {@link #ProcessFailedTests} is set to true show the same results like for regular tests
     * (normally just statistical info would be shown).
     */
    public static final boolean ShowFailedTestsResults = Boolean.getBoolean("ShowFailedTestsResults");
    /**
     * Adds {@link Ignored#NewRVersionMigration} to failed test. This attempts to modify the Java
     * source code of the tests.
     */
    public static final boolean AddIgnoreForFailedTests = Boolean.getBoolean("AddIgnoreForFailedTests");

    /**
     * {@link FastROptions} passed from the mx {@code JUnit} invocation.
     */
    public static final Map<String, String> options = new HashMap<>();

    /**
     * See {@code TestTestBase} for examples.
     */
    public enum Output implements TestTrait {
        ImprovedErrorContext, // FastR provides a more accurate error context
        IgnoreErrorContext, // the error context is ignored (e.g., "a+b" vs. "a + b")
        IgnoreErrorMessage, // the actual error message is ignored
        IgnoreWarningContext, // the warning context is ignored
        IgnoreWarningMessage, // the warning message is ignored
        MayIgnoreErrorContext, // like IgnoreErrorContext, but no warning if the messages match
        MayIgnoreWarningContext,
        MayIgnoreWarningMessage,
        MissingWarning, // Test output is correct but a warning msg is missing in FastR output
        ContainsReferences, // replaces references in form of 0xbcdef1 for numbers
        IgnoreWhitespace, // removes all whitespace from the whole output
        IgnoreCase, // ignores upper/lower case differences
        IgnoreDebugPath, // ignores <path> in debug output like "debug at <path> #..."
        IgnoreDebugDepth, // ignores call depth printed by the debugger ("Browse[<call depth>]")
        IgnoreDebugCallString; // ignores the caller string like "debugging in:" or "Called from:"

        @Override
        public String getName() {
            return name();
        }
    }

    public enum Ignored implements TestTrait {
        Unknown("failing tests that have not been classified yet"),
        Unstable("tests that produce inconsistent results in GNUR"),
        OutputFormatting("tests that fail because of problems with output formatting"),
        ParserErrorFormatting("tests that fail because of the formatting of parser error messages"),
        WrongCaller("tests that fail because the caller source is wrong in an error or warning"),
        ParserError("tests that fail because of bugs in the parser"),
        ImplementationError("tests that fail because of bugs in other parts of the runtime"),
        ReferenceError("tests that fail because of faulty behavior in the reference implementation that we don't want to emulate"),
        SideEffects("tests that are ignored because they would interfere with other tests"),
        MissingBuiltin("tests that fail because of missing builtins"),
        NewRVersionMigration("temporarily ignored while migrating to new GNU-R version"),
        NativeGridGraphics("tests that are ignored because of the different behavior of FastrInternalGrid from native grid packages"),
        Unimplemented("tests that fail because of missing functionality");

        private final String description;

        Ignored(String description) {
            this.description = description;
        }

        @Override
        public String getName() {
            return name();
        }

        public String getDescription() {
            return description;
        }
    }

    public enum IgnoredJdk implements TestTrait {
        LaterThanJdk8("tests that fail if jdk later then java 8", System.getProperty("java.specification.version").compareTo("1.8") > 0);

        private final String description;
        private final boolean isIgnoring;

        IgnoredJdk(String description, boolean isIgnoring) {
            this.description = description;
            this.isIgnoring = isIgnoring;
        }

        @Override
        public String getName() {
            return name();
        }

        public String getDescription() {
            return description;
        }

        public static boolean containsIgnoring(TestTrait[] traits) {
            return Arrays.stream(TestTrait.collect(traits, IgnoredJdk.class)).anyMatch(t -> t.isIgnoring());
        }

        private boolean isIgnoring() {
            return isIgnoring;
        }
    }

    public enum Context implements TestTrait {
        NonShared, // Test requires a new non-shared {@link RContext}.
        NoJavaInterop; // Test requires a {@link RContext} with disabled host access.

        @Override
        public String getName() {
            return name();
        }
    }

    /**
     * Instantiated by the mx {@code JUnit} wrapper. The arguments are passed as system properties
     * <ul>
     * <li>{@code expected=dir}: path to dir containing expected output file to be
     * read/generated/updated</li>
     * <li>{@code gen-expected}: causes the expected output file to be generated/updated</li>
     * <li>{@code gen-fastr=dir}: causes the FastR output to be generated in dir</li>
     * <li>{@code gen-diff=dir}: generates a difference file between FastR and expected output in
     * dir</li>
     * <li>{@code check-expected}: checks that the expected output file is consistent with the set
     * of tests but does not update</li>
     * <li>{@code keep-trailing-whitespace}: keep trailing whitespace when generating expected
     * <li>{@code test-methods}: pattern to match test methods in test classes output></li>
     * </ul>
     */
    public static class RunListener extends org.junit.runner.notification.RunListener {

        private static File diffsOutputFile;

        private static final String PROP_BASE = "fastr.test.";
        private static final String ENV_PROP_BASE = "FASTR_TEST_";

        private enum Props {
            GEN_EXPECTED("gen.expected"),
            GEN_EXPECTED_QUIET("gen.expected.quiet"),
            CHECK_EXPECTED("check.expected"),
            TRACE_TESTS("trace.tests"),
            OPTIONS_TESTS("options"),
            KEEP_TRAILING_WHITESPACE("keep.trailing.whitespace");

            private final String propSuffix;

            Props(String propSuffix) {
                this.propSuffix = propSuffix;
            }
        }

        private static String getProperty(String baseName) {
            String propName = PROP_BASE + baseName;
            String val = System.getProperty(propName);
            if (val == null || val.trim().isEmpty()) {
                val = System.getenv(ENV_PROP_BASE + baseName.replace('.', '_'));
            }
            return val;
        }

        public static boolean getBooleanProperty(String baseName) {
            String val = getProperty(baseName);
            return val != null && (val.length() == 0 || val.equals("true"));
        }

        @Override
        public void testRunStarted(Description description) {
            try {
                File fastROutputFile = null;
                boolean checkExpected = false;
                String genExpected = getProperty(Props.GEN_EXPECTED.propSuffix);
                boolean genExpectedQuiet = getBooleanProperty(Props.GEN_EXPECTED_QUIET.propSuffix);
                checkExpected = getBooleanProperty(Props.CHECK_EXPECTED.propSuffix);
                if (genExpected != null) {
                    File expectedOutputFile = new File(new File(genExpected), TestOutputManager.TEST_EXPECTED_OUTPUT_FILE);
                    expectedOutputManager = new ExpectedTestOutputManager(expectedOutputFile, true, checkExpected, genExpectedQuiet);
                } else {
                    // read from jar
                    URL expectedTestOutputURL = ResourceHandlerFactory.getHandler().getResource(TestBase.class, TestOutputManager.TEST_EXPECTED_OUTPUT_FILE);
                    expectedOutputManager = new ExpectedTestOutputManager(expectedTestOutputURL, false, checkExpected, genExpectedQuiet);
                    System.console();
                }
                // parse test options before initializing context
                // FastRTestOutputManager -> FastrSession -> context
                fastROptions();
                fastROutputManager = new FastRTestOutputManager(fastROutputFile);

                traceTests = getBooleanProperty(Props.TRACE_TESTS.propSuffix);
                addOutputHook();
            } catch (Throwable ex) {
                throw new AssertionError("R initialization failure", ex);
            }
        }

        private static void fastROptions() {
            String optionPrefix = PROP_BASE + Props.OPTIONS_TESTS.propSuffix;
            for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
                String prop = (String) entry.getKey();
                if (prop.startsWith(optionPrefix)) {
                    String name = prop.substring(optionPrefix.length() + 1);
                    options.put(name, entry.getValue().toString());
                    System.out.println("Note: using FastR option: " + name + "=" + entry.getValue().toString());
                }
            }
        }

        @Override
        public void testRunFinished(Result result) {
            try {
                deleteDir(Paths.get(TEST_OUTPUT));
                if (expectedOutputManager.generate) {
                    boolean updated = expectedOutputManager.writeTestOutputFile();
                    if (updated) {
                        if (expectedOutputManager.checkOnly) {
                            // fail fast
                            RSuicide.rSuicideDefault("Test file:" + expectedOutputManager.outputFile + " is out of sync with unit tests");
                        }
                        System.out.println("updating " + expectedOutputManager.outputFile);
                    }
                }
                if (fastROutputManager.outputFile != null) {
                    fastROutputManager.writeTestOutputFile(null, false);
                }
                if (diffsOutputFile != null) {
                    TestOutputManager.writeDiffsTestOutputFile(diffsOutputFile, expectedOutputManager, fastROutputManager);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void testStarted(Description description) {
            testElementName = description.getClassName() + "." + description.getMethodName();
            if (traceTests) {
                System.out.println(testElementName);
            }
            failedMicroTests = new ArrayList<>();
        }
    }

    @Before
    public void beforeTest() {
        checkOutputManagersInitialized();
    }

    private static void checkOutputManagersInitialized() {
        if (expectedOutputManager == null) {
            /*
             * Assume we are running a unit test in an IDE/non-JUnit setup and therefore the
             * RunListener was not invoked. In this case we can expect the test output file to exist
             * and open it as a resource.
             */
            URL expectedTestOutputURL = ResourceHandlerFactory.getHandler().getResource(TestBase.class, TestOutputManager.TEST_EXPECTED_OUTPUT_FILE);
            if (expectedTestOutputURL == null) {
                Assert.fail("cannot find " + TestOutputManager.TEST_EXPECTED_OUTPUT_FILE + " resource");
            } else {
                try {
                    expectedOutputManager = new ExpectedTestOutputManager(new File(expectedTestOutputURL.getPath()), false, false, false);
                    fastROutputManager = new FastRTestOutputManager(null);
                    addOutputHook();
                } catch (IOException ex) {
                    Assert.fail("error reading: " + expectedTestOutputURL.getPath() + ": " + ex);
                }
            }
        }
    }

    /**
     * Method for non-JUnit implementation to emulate important behavior of {@link RunListener}.
     */
    public static void emulateRunListener() {
        checkOutputManagersInitialized();
    }

    /**
     * Method for non-JUnit implementation to set test tracing.
     */
    public static void setTraceTests() {
        traceTests = true;
    }

    /**
     * Set the test context explicitly (for non-JUnit implementation). N.B. The {@code lineno} is
     * not the micro-test line, but that of the method declaration.
     */
    public void doBeforeTest(String className, int lineno, String methodName) {
        testElementName = className + "." + methodName;
        failedMicroTests = new ArrayList<>();
        explicitTestContext = String.format("%s:%d (%s)", className, lineno, methodName);
        if (traceTests) {
            System.out.println(testElementName);
        }
    }

    private static class ExpectedTestOutputManager extends TestOutputManager {
        private final boolean generate;

        /**
         * When {@code true}, indicates that test generation is in check only mode.
         */
        private final boolean checkOnly;
        /**
         * When running in generation mode, the original content of the expected output file.
         */
        private final String oldExpectedOutputFileContent;

        private boolean haveRSession;

        protected ExpectedTestOutputManager(File outputFile, boolean generate, boolean checkOnly, boolean genExpectedQuiet) throws IOException {
            super(outputFile);
            this.checkOnly = checkOnly;
            this.generate = generate;
            if (genExpectedQuiet) {
                localDiagnosticHandler.setQuiet();
            }
            oldExpectedOutputFileContent = readTestOutputFile();
            if (generate) {
                createRSession();
            }
        }

        protected ExpectedTestOutputManager(URL urlOutput, boolean generate, boolean checkOnly, boolean genExpectedQuiet) throws IOException {
            super(urlOutput);
            this.checkOnly = checkOnly;
            this.generate = generate;
            if (genExpectedQuiet) {
                localDiagnosticHandler.setQuiet();
            }
            oldExpectedOutputFileContent = readTestOutputFile();
            if (generate) {
                createRSession();
            }
        }

        private void createRSession() {
            if (!haveRSession) {
                setRSession(new GnuROneShotRSession());
                haveRSession = true;
            }
        }

        boolean writeTestOutputFile() throws IOException {
            return writeTestOutputFile(oldExpectedOutputFileContent, checkOnly);
        }
    }

    private static class FastRTestOutputManager extends TestOutputManager {
        final FastRSession fastRSession;

        FastRTestOutputManager(File outputFile) {
            super(outputFile);
            setRSessionName("FastR");
            // no point in printing errors to file when running tests (that contain errors on
            // purpose)
            fastRSession = FastRSession.create();
        }
    }

    private static ExpectedTestOutputManager expectedOutputManager;
    private static FastRTestOutputManager fastROutputManager;

    private static class MicroTestInfo {
        /**
         * The expression currently being evaluated by FastR.
         */
        private String expression;

        /**
         * The result of FastR evaluating {@link #expression}.
         */
        private String fastROutput;

        /**
         * The expected output.
         */
        private String expectedOutput;

    }

    private static final MicroTestInfo microTestInfo = new MicroTestInfo();

    /**
     * Set to the JUnit test element name by the {@code RunListener}, i.e., {@code class.testMethod}
     * .
     */
    private static String testElementName;

    /**
     * Emptied at the start of a JUnit test, each failed micro test will be added to the list.
     */
    private static ArrayList<String> failedMicroTests = new ArrayList<>();

    private static ArrayList<String> unexpectedSuccessfulMicroTests = new ArrayList<>();

    private static SortedMap<String, Integer> exceptionCounts = new TreeMap<>();

    private static int successfulTestCount;
    private static int ignoredTestCount;
    private static int failedTestCount;
    private static int successfulInputCount;
    private static int ignoredInputCount;
    private static int failedInputCount;

    protected static String explicitTestContext;

    /**
     * A way to limit which tests are actually run. TODO requires more JUnit support for filtering
     * in the wrapper.
     *
     */
    @SuppressWarnings("unused") private static String testMethodsPattern;

    /**
     * {@code true} if expected output is not discarding trailing whitespace.
     */
    private static boolean keepTrailingWhiteSpace;

    /**
     * Trace the test methods as they are executed (debugging).
     */
    private static boolean traceTests;

    protected static final String ERROR = "Error";
    protected static final String WARNING = "Warning message";

    /**
     * If this is set to {@code true}, {@link Output#IgnoreErrorContext} will compare the full
     * output instead of truncating leading "Error" strings and such. This means it will behave like
     * {@link #assertEval}.
     */
    private static final boolean FULL_COMPARE_ERRORS = false;

    /**
     * To implement {@link Output#ContainsReferences}.
     **/
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("(?<id>(0x[0-9abcdefx]+))");

    /**
     * Test a given string with R source against expected output. This is (currently) an exact
     * match, so any warnings or errors will cause a failure until FastR matches GnuR in that
     * respect.
     */
    protected void assertEval(String... input) {
        evalAndCompare(input, USE_DEFAULT_TIMEOUT);
    }

    protected void assertEval(long timeout, String... input) {
        evalAndCompare(input, timeout);
    }

    protected void assertEval(TestTrait trait1, String... input) {
        evalAndCompare(input, USE_DEFAULT_TIMEOUT, trait1);
    }

    protected void assertEval(long timeout, TestTrait trait1, String... input) {
        evalAndCompare(input, timeout, trait1);
    }

    protected void assertEval(long timeout, TestTrait trait1, TestTrait trait2, String... input) {
        evalAndCompare(input, timeout, trait1, trait2);
    }

    protected void assertEval(long timeout, TestTrait trait1, TestTrait trait2, TestTrait trait3, String... input) {
        evalAndCompare(input, timeout, trait1, trait2, trait3);
    }

    protected void assertEval(TestTrait trait1, TestTrait trait2, String... input) {
        evalAndCompare(input, USE_DEFAULT_TIMEOUT, trait1, trait2);
    }

    protected void assertEval(TestTrait trait1, TestTrait trait2, TestTrait trait3, String... input) {
        evalAndCompare(input, USE_DEFAULT_TIMEOUT, trait1, trait2, trait3);
    }

    protected void assertEval(TestTrait trait1, TestTrait trait2, TestTrait trait3, TestTrait trait4, String... input) {
        evalAndCompare(input, USE_DEFAULT_TIMEOUT, trait1, trait2, trait3, trait4);
    }

    protected void assertEval(TestTrait trait1, TestTrait trait2, TestTrait trait3, TestTrait trait4, TestTrait trait5, String... input) {
        evalAndCompare(input, USE_DEFAULT_TIMEOUT, trait1, trait2, trait3, trait4, trait5);
    }

    protected void afterMicroTest() {
        // empty
    }

    // support testing of FastR-only functionality (equivalent GNU R output provided separately)
    protected boolean assertEvalFastR(TestTrait trait1, String input, String gnuROutput) {
        return evalAndCompare(getAssertEvalFastR(gnuROutput, input), USE_DEFAULT_TIMEOUT, trait1);
    }

    protected boolean assertEvalFastR(TestTrait trait1, String input, String gnuROutput, boolean useREPL) {
        return evalAndCompare(getAssertEvalFastR(gnuROutput, input), useREPL, USE_DEFAULT_TIMEOUT, true, trait1);
    }

    protected boolean assertEvalFastR(TestTrait trait1, TestTrait trait2, String input, String gnuROutput) {
        return evalAndCompare(getAssertEvalFastR(gnuROutput, input), USE_DEFAULT_TIMEOUT, trait1, trait2);
    }

    protected boolean assertEvalFastR(String input, String gnuROutput) {
        return evalAndCompare(getAssertEvalFastR(gnuROutput, input), USE_DEFAULT_TIMEOUT);
    }

    protected boolean assertEvalFastR(String input, String gnuROutput, boolean useREPL) {
        return evalAndCompare(getAssertEvalFastR(gnuROutput, input), useREPL, USE_DEFAULT_TIMEOUT, true);
    }

    protected void assertEvalOneShot(String input) {
        evalAndCompare(new String[]{input}, true, USE_DEFAULT_TIMEOUT, false);
    }

    protected void assertEvalOneShot(String[] inputs) {
        evalAndCompare(inputs, true, USE_DEFAULT_TIMEOUT, false);
    }

    private static String[] getAssertEvalFastR(String gnuROutput, String input) {
        return new String[]{"if (!any(R.version$engine == \"FastR\")) { " + gnuROutput + " } else { " + input + " }"};
    }

    /*
     * implementation support methods
     */

    /**
     * Check for micro-test failure and if so fail the entire test. N.B. Must do this using
     * {@code @After} and not in the {@code testFinished} listener method, because exceptions in the
     * listener prevent its subsequent invocation.
     */
    @After
    public void afterTest() {
        if (failedMicroTests != null && !failedMicroTests.isEmpty()) {
            fail(failedMicroTests.size() + " micro-test(s) failed: \n  " + new TreeSet<>(failedMicroTests));
        }
    }

    private static Path cwd;

    private static Path getCwd() {
        if (cwd == null) {
            cwd = Paths.get(System.getProperty("user.dir"));
        }
        return cwd;
    }

    protected static final String TEST_OUTPUT = "tmptest";

    /**
     * Return a path that is relative to the 'cwd/testoutput' when running tests.
     */
    public static Path relativize(Path path) {
        return getCwd().relativize(path);
    }

    /**
     * Creates a directory with suffix {@code name} in the {@code testoutput} directory and returns
     * a relative path to it.
     */
    public static Path createTestDir(String name) {
        Path dir = Paths.get(getCwd().toString(), TEST_OUTPUT, name);
        if (!dir.toFile().exists()) {
            if (!dir.toFile().mkdirs()) {
                Assert.fail("failed to create dir: " + dir.toString());
            }
        }
        return relativize(dir);
    }

    private static void microTestFailed() {
        if (!ProcessFailedTests || ShowFailedTestsResults) {
            System.err.printf("%nMicro-test failure: %s%n", getTestContext());
            System.err.printf("%16s %s%n", "Expression:", microTestInfo.expression);
            System.err.printf("%16s %s", "Expected output:", microTestInfo.expectedOutput);
            System.err.printf("%16s %s%n", "FastR output:", microTestInfo.fastROutput);

            failedMicroTests.add(getTestContext());
        }
    }

    private static String getTestContext() {
        if (explicitTestContext != null) {
            return explicitTestContext;
        }
        try {
            StackTraceElement culprit = getTestStackFrame();
            String context = String.format("%s:%d (%s)", culprit.getClassName(), culprit.getLineNumber(), culprit.getMethodName());
            return context;
        } catch (NullPointerException npe) {
            return "no test context available";
        }
    }

    private static StackTraceElement getTestStackFrame() {
        // We want the stack trace as if the JUnit test failed.
        RuntimeException ex = new RuntimeException();
        // The first method not in TestBase is the culprit
        StackTraceElement culprit = null;
        // N.B. This may not always be available (AOT).
        StackTraceElement[] stackTrace = ex.getStackTrace();
        for (int i = 0; i < stackTrace.length; i++) {
            StackTraceElement se = stackTrace[i];
            if (!se.getClassName().endsWith("TestBase")) {
                culprit = se;
                break;
            }
        }
        return culprit;
    }

    /**
     * Wraps the traits, there are some meta-traits like {@link #isIgnored}, other traits can be
     * accessed through the corresponding enum-set.
     */
    private static class TestTraitsSet {
        EnumSet<Ignored> ignored = EnumSet.noneOf(Ignored.class);
        EnumSet<Output> output = EnumSet.noneOf(Output.class);
        EnumSet<Context> context = EnumSet.noneOf(Context.class);
        boolean isIgnored;
        boolean containsError;

        TestTraitsSet(TestTrait[] traits) {
            ignored.addAll(Arrays.asList(TestTrait.collect(traits, Ignored.class)));
            output.addAll(Arrays.asList(TestTrait.collect(traits, Output.class)));
            context.addAll(Arrays.asList(TestTrait.collect(traits, Context.class)));
            containsError = (!FULL_COMPARE_ERRORS && (output.contains(Output.IgnoreErrorContext) || output.contains(Output.ImprovedErrorContext) || output.contains(Output.IgnoreErrorMessage)));
            isIgnored = (ignored.size() > 0 || IgnoreOS.containsIgnoring(traits) || IgnoredJdk.containsIgnoring(traits)) ^
                            (ProcessFailedTests && (!IgnoredUnknownOnlyTests || (IgnoredUnknownOnlyTests && ignored.contains(Ignored.Unknown))) &&
                                            !(ignored.contains(Ignored.Unstable) || ignored.contains(Ignored.SideEffects)));
            assert !output.contains(Output.IgnoreWhitespace) || output.size() == 1 : "IgnoreWhitespace trait does not work with any other Output trait";

        }

        String preprocessOutput(String out) {
            String s = out;
            if (output.contains(Output.IgnoreWhitespace)) {
                return s.replaceAll("\\s+", "");
            }
            if (output.contains(Output.IgnoreCase)) {
                return s.toLowerCase();
            }
            if (output.contains(Output.ContainsReferences)) {
                return convertReferencesInOutput(s);
            }
            if (output.contains(Output.IgnoreDebugPath)) {
                s = convertDebugOutput(s);
            }
            if (output.contains(Output.IgnoreDebugDepth)) {
                s = removeDebugCallDepth(s);
            }
            if (output.contains(Output.IgnoreDebugCallString)) {
                s = removeDebugCallString(s);
            }
            return s;
        }
    }

    private boolean evalAndCompare(String[] inputs, long timeout, TestTrait... traitsList) {
        return evalAndCompare(inputs, false, timeout, true, traitsList);
    }

    private boolean evalAndCompare(String[] inputs, boolean useREPL, long timeout, boolean hasGeneratedOutput, TestTrait... traitsList) {
        if (!hasGeneratedOutput && generatingExpected()) {
            return true;
        }
        IncludeList[] includeLists = TestTrait.collect(traitsList, IncludeList.class);
        TestTraitsSet traits = new TestTraitsSet(traitsList);
        ContextKind contextKind = traits.context.contains(Context.NonShared) ? ContextKind.SHARE_NOTHING : ContextKind.SHARE_PARENT_RW;
        int index = 1;
        boolean allOk = true;
        boolean skipFastREval = traits.isIgnored || generatingExpected();
        for (String input : inputs) {
            String expected = expectedEval(input, hasGeneratedOutput, traitsList);
            if (skipFastREval) {
                ignoredInputCount++;
            } else {
                String result = fastREval(input, contextKind, timeout, !traits.context.contains(Context.NoJavaInterop), useREPL);
                CheckResult checkResult = checkResult(includeLists, input, traits.preprocessOutput(expected), traits.preprocessOutput(result), traits);

                result = checkResult.result;
                expected = checkResult.expected;
                boolean ok = checkResult.ok;

                if (ProcessFailedTests) {
                    if (ok) {
                        unexpectedSuccessfulMicroTests.add(getTestContext() + ": " + input);
                    } else if (expected.startsWith(ERROR) && result.startsWith(ERROR)) {
                        if (checkMessageStripped(expected, result)) {
                            unexpectedSuccessfulMicroTests.add("<error> " + getTestContext() + ": " + input);
                        }
                    } else if (expected.contains(WARNING) && result.contains(WARNING)) {
                        if (getOutputWithoutWarning(expected).equals(getOutputWithoutWarning(result)) && getWarningMessage(expected).equals(getWarningMessage(result))) {
                            unexpectedSuccessfulMicroTests.add("<warning> " + getTestContext() + ": " + input);
                        }
                    }
                }
                if (ok) {
                    successfulInputCount++;
                } else {
                    failedInputCount++;
                    microTestFailed();
                    if (AddIgnoreForFailedTests) {
                        try {
                            addIgnoreForFailedTest();
                        } catch (Exception ex) {
                            System.err.println("Cannot add ignore for test. Message: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                    if (!ProcessFailedTests || ShowFailedTestsResults) {
                        // Show hint where first diff occurs - use preprocessed text
                        int expectedLen = checkResult.expected.length();
                        int testLen = checkResult.result.length();
                        int len = Math.min(expectedLen, testLen);
                        int line = 0;
                        int col = 0;
                        StringBuilder sb = new StringBuilder(128);
                        for (int i = 0; i < len; i++) {
                            char c = checkResult.expected.charAt(i);
                            if (c != checkResult.result.charAt(i)) {
                                sb.append("First different char index: ").append(i).append('[').append(line + 1).append(':').append(col + 1).append("]\n    Expected: ");
                                for (int type = 0; type <= 1; type++) {
                                    int maxLen = (type == 0) ? expectedLen : testLen;
                                    int dLen = Math.min(i + 60, maxLen);
                                    for (int j = i; j < dLen; j++) {
                                        char ch = ((type == 0) ? checkResult.expected : checkResult.result).charAt(j);
                                        switch (ch) {
                                            case '\n':
                                                sb.append("\\n");
                                                break;
                                            case '\r':
                                                sb.append("\\r");
                                                break;
                                            case '\t':
                                                sb.append("\\t");
                                                break;
                                            case '\b':
                                                sb.append("\\b");
                                                break;
                                            case '\f':
                                                sb.append("\\f");
                                                break;
                                            default:
                                                sb.append(ch);
                                        }
                                    }
                                    if (dLen < maxLen) {
                                        sb.append("...");
                                    }
                                    if (type == 0) {
                                        sb.append("\n       FastR: ");
                                    }
                                }
                                break;
                            }
                            col++;
                            if (c == '\n') {
                                line++;
                                col = 0;
                            }
                        }
                        System.err.println(sb);
                    }

                    if (inputs.length > 1) {
                        System.out.print('E');
                    }
                }
                allOk &= ok;
                afterMicroTest();
            }
            if ((index) % 100 == 0) {
                System.out.print('.');
            }
            index++;
        }
        if (traits.isIgnored) {
            ignoredTestCount++;
        } else if (allOk) {
            successfulTestCount++;
        } else {
            failedTestCount++;
        }
        return !skipFastREval;
    }

    private static void addIgnoreForFailedTest() throws IOException {
        StackTraceElement frame = getTestStackFrame();
        String fileName = frame.getFileName();
        Path testsPath = Paths.get("./com.oracle.truffle.r.test/src/com/oracle/truffle/r/test");
        Optional<Path> pathOptional = Files.walk(testsPath).filter(Files::isRegularFile).filter(f -> f.endsWith(fileName)).findFirst();
        if (!pathOptional.isPresent()) {
            System.out.println("WARNING: cannot add ignore to test (file not found) " + frame.getClassName() + ":" + frame.getLineNumber());
            return;
        }
        Path path = pathOptional.get();
        List<String> lines = Files.readAllLines(path);
        String line = lines.get(frame.getLineNumber() - 1);
        if (line.contains("Ignored.NewRVersionMigration")) {
            // already there, can happen for templated tests
            return;
        }
        if (!line.contains("assertEval(")) {
            System.out.println("WARNING: cannot add ignore to test " + frame.getClassName() + ":" + frame.getLineNumber());
            return;
        }
        line = line.replaceAll("(:?Output\\.[a-zA-Z0-9]*[ ]*,)[ ]*", "/*$1*/");
        line = line.replace("assertEval(", "assertEval(Ignored.NewRVersionMigration, ");
        lines.set(frame.getLineNumber() - 1, line);
        Files.write(path, lines);
    }

    private static class CheckResult {

        public final boolean ok;
        public final String result;
        public final String expected;

        CheckResult(boolean ok, String result, String expected) {
            this.ok = ok;
            this.result = result;
            this.expected = expected;
        }
    }

    private CheckResult checkResult(IncludeList[] includeLists, String input, String originalExpected, String originalResult, TestTraitsSet traits) {
        boolean ok;
        String result = originalResult;
        String expected = originalExpected;
        if (expected.equals(result) || searchIncludeLists(includeLists, input, expected, result, traits)) {
            ok = true;
            if (traits.containsError && !traits.output.contains(Output.IgnoreErrorMessage)) {
                System.out.println("unexpected correct error message: " + getTestContext());
            }
            if (traits.output.contains(Output.IgnoreWarningContext) || traits.output.contains(Output.IgnoreWarningMessage)) {
                System.out.println("unexpected correct warning message: " + getTestContext());
            }
        } else {
            if (traits.output.contains(Output.MissingWarning)) {
                boolean expectedContainsWarning = expected.contains(WARNING);
                if (!expectedContainsWarning) {
                    System.out.println("unexpected missing warning message:" + getTestContext());
                }
                ok = expectedContainsWarning && !result.contains(WARNING);
                expected = getOutputWithoutWarning(expected);
            } else if (traits.output.contains(Output.IgnoreWarningContext) || traits.output.contains(Output.IgnoreWarningMessage) ||
                            ((traits.output.contains(Output.MayIgnoreWarningContext) || traits.output.contains(Output.MayIgnoreWarningMessage)) && expected.contains(WARNING))) {
                String resultWarning = getWarningMessage(result);
                String expectedWarning = getWarningMessage(expected);
                ok = resultWarning.equals(expectedWarning) || traits.output.contains(Output.IgnoreWarningMessage) || traits.output.contains(Output.MayIgnoreWarningMessage);
                result = getOutputWithoutWarning(result);
                expected = getOutputWithoutWarning(expected);
            } else {
                ok = true;
            }
            if (ok) {
                if (traits.containsError || (traits.output.contains(Output.MayIgnoreErrorContext) && expected.startsWith(ERROR))) {
                    ok = result.startsWith(ERROR) && (traits.output.contains(Output.IgnoreErrorMessage) || checkMessageStripped(expected, result) || checkMessageVectorInIndex(expected, result));
                } else {
                    ok = expected.equals(result);
                }
            }
        }
        return new CheckResult(ok, result, expected);
    }

    private static String convertReferencesInOutput(String input) {
        String result = input;
        Matcher matcher = REFERENCE_PATTERN.matcher(result);
        HashMap<String, Integer> idsMap = new HashMap<>();
        int currentId = 1;
        while (matcher.find()) {
            if (idsMap.putIfAbsent(matcher.group("id"), currentId) == null) {
                currentId++;
            }
        }
        for (Entry<String, Integer> item : idsMap.entrySet()) {
            result = result.replace(item.getKey(), item.getValue().toString());
        }
        return result;
    }

    private static String convertDebugOutput(String out) {
        String prefix = "debug at ";
        return removeAllOccurrencesBetween(out, prefix, prefix.length(), "#", 0);
    }

    private static String removeDebugCallDepth(String out) {
        String prefix = "Browse[";
        return removeAllOccurrencesBetween(out, prefix, prefix.length(), "]", 0);
    }

    private static String removeDebugCallString(String out) {
        return removeLines(out, line -> line.startsWith("debugging in:") || line.startsWith("Called from:"));
    }

    private static String removeAllOccurrencesBetween(String out, String prefix, int prefixOffset, String suffix, int suffixOffset) {
        StringBuilder sb = new StringBuilder(out);

        int idxPrefix = -1;
        int idxSuffix = -1;

        while ((idxPrefix = sb.indexOf(prefix, idxPrefix + 1)) > 0 && (idxSuffix = sb.indexOf(suffix, idxPrefix)) > idxPrefix) {
            sb.replace(idxPrefix + prefixOffset, idxSuffix + suffixOffset, "");
        }

        return sb.toString();
    }

    /**
     * Removes the lines from the test output string matching the provided predicate.
     */
    private static String removeLines(String out, Predicate<String> pred) {
        StringBuilder sb = new StringBuilder();

        BufferedReader r = new BufferedReader(new StringReader(out));
        String line;
        try {
            while ((line = r.readLine()) != null) {
                if (!pred.test(line)) {
                    sb.append(line);
                    sb.append(System.lineSeparator());
                }
            }
        } catch (IOException e) {
            // won't happen
        }

        return sb.toString();
    }

    private boolean searchIncludeLists(IncludeList[] includeLists, String input, String expected, String result, TestTraitsSet testTraits) {
        if (includeLists == null) {
            return false;
        }
        for (IncludeList list : includeLists) {
            IncludeList.Results wlr = list.get(input);
            if (wlr != null) {
                // Sanity check that "expected" matches the entry in the IncludeList
                CheckResult checkedResult = checkResult(null, input, wlr.expected, expected, testTraits);
                if (!checkedResult.ok) {
                    System.out.println("expected output does not match: " + wlr.expected + " vs. " + expected);
                    return false;
                }
                // Substitute the FastR output and try to match that
                CheckResult fastRResult = checkResult(null, input, wlr.fastR, result, testTraits);
                if (fastRResult.ok) {
                    list.markUsed(input);
                    return true;
                }
            }
        }
        return false;
    }

    private static final Pattern warningPattern1 = Pattern.compile("^(?<pre>.*)Warning messages:\n1:(?<msg0>.*)\n2:(?<msg1>.*)\n3:(?<msg2>.*)\n4:(?<msg3>.*)5:(?<msg4>.*)$", Pattern.DOTALL);
    private static final Pattern warningPattern2 = Pattern.compile("^(?<pre>.*)Warning messages:\n1:(?<msg0>.*)\n2:(?<msg1>.*)\n3:(?<msg2>.*)\n4:(?<msg3>.*)$", Pattern.DOTALL);
    private static final Pattern warningPattern3 = Pattern.compile("^(?<pre>.*)Warning messages:\n1:(?<msg0>.*)\n2:(?<msg1>.*)\n3:(?<msg2>.*)$", Pattern.DOTALL);
    private static final Pattern warningPattern4 = Pattern.compile("^(?<pre>.*)Warning messages:\n1:(?<msg0>.*)\n2:(?<msg1>.*)$", Pattern.DOTALL);
    private static final Pattern warningPattern5 = Pattern.compile("^(?<pre>.*)Warning message:(?<msg0>.*)$", Pattern.DOTALL);

    private static final Pattern warningMessagePattern = Pattern.compile("^\n? ? ?(?:In .* :[ \n])?[ \n]*(?<m>[^\n]*)\n?$", Pattern.DOTALL);
    private static final Pattern hiddenMessagePattern = Pattern.compile("^(?<pre>.*)(?<msg>There were [0-9]+ warnings \\(use warnings\\(\\) to see them\\)).*$", Pattern.DOTALL);

    private static final Pattern[] warningPatterns = new Pattern[]{warningPattern1, warningPattern2, warningPattern3, warningPattern4, warningPattern5};

    private static Matcher getWarningMatcher(String output) {
        for (Pattern pattern : warningPatterns) {
            Matcher matcher = pattern.matcher(output);
            if (matcher.matches()) {
                return matcher;
            }
        }
        Matcher matcher = hiddenMessagePattern.matcher(output);
        if (matcher.matches()) {
            return matcher;
        }
        return null;
    }

    private static String getWarningMessage(String output) {
        Matcher matcher = getWarningMatcher(output);
        if (matcher == null) {
            return "";
        }
        if (matcher.pattern() == hiddenMessagePattern) {
            return matcher.group("msg");
        }

        StringBuilder str = new StringBuilder();
        for (int i = 0; i < warningPatterns.length; i++) {
            try {
                String message = matcher.group("msg" + i);
                Matcher messageMatcher = warningMessagePattern.matcher(message);
                boolean messageMatches = messageMatcher.matches();
                assert messageMatches : "unexpected format in warning message: " + message;
                str.append(messageMatcher.group("m").trim()).append('|');
            } catch (IllegalArgumentException e) {
                break;
            }
        }
        return str.toString();
    }

    private static String getOutputWithoutWarning(String output) {
        Matcher matcher = getWarningMatcher(output);
        return matcher != null ? matcher.group("pre") : output;
    }

    /**
     * Compares the actual error message, after removing any context before the ':' and after
     * removing whitespace.
     */
    private static boolean checkMessageStripped(String expected, String result) {
        String[] stripped = splitAndStripMessage(expected, result);
        if (stripped == null) {
            return false;
        }
        String expectedStripped = stripped[0];
        String resultStripped = stripped[1];
        return resultStripped.equals(expectedStripped);
    }

    private static final Pattern VECTOR_INDEX_PATTERN = Pattern.compile("(?<prefix>(attempt to select (more|less) than one element)).*");

    /**
     * Deal with R 3.3.x "selected more/less than one element in xxxIndex.
     */
    private static boolean checkMessageVectorInIndex(String expected, String result) {
        String[] stripped = splitAndStripMessage(expected, result);
        if (stripped == null) {
            return false;
        }
        String expectedStripped = stripped[0];
        String resultStripped = stripped[1];
        Matcher matcher = VECTOR_INDEX_PATTERN.matcher(expectedStripped);
        if (matcher.find()) {
            String prefix = matcher.group("prefix");
            return prefix.equals(resultStripped);
        } else {
            return false;
        }
    }

    private static String[] splitAndStripMessage(String expected, String result) {
        int cxr = result.lastIndexOf(':');
        int cxe = expected.lastIndexOf(':');
        if (cxr < 0 || cxe < 0) {
            return null;
        }
        String resultStripped = result.substring(cxr + 1).trim();
        String expectedStripped = expected.substring(cxe + 1).trim();
        return new String[]{expectedStripped, resultStripped};
    }

    protected String fastREval(String input) {
        return fastREval(input, ContextKind.SHARE_PARENT_RW);
    }

    /**
     * Evaluate {@code input} in FastR, returning all (virtual) console output that was produced. If
     * {@code nonShared} then this must evaluate in a new, non-shared, {@link RContext}.
     */
    protected String fastREval(String input, ContextKind contextKind) {
        return fastREval(input, contextKind, USE_DEFAULT_TIMEOUT, true, false);
    }

    protected String fastREval(String input, ContextKind contextKind, long timeout, boolean allowHostAccess, boolean useREPL) {
        assert contextKind != null;
        microTestInfo.expression = input;
        String result;
        try {
            beforeEval();
            if (useREPL) {
                result = fastROutputManager.fastRSession.evalInREPL(this, input, contextKind, timeout, allowHostAccess);
            } else {
                result = fastROutputManager.fastRSession.eval(this, input, contextKind, timeout, allowHostAccess);

            }
        } catch (Throwable e) {
            String clazz;
            if (e instanceof RInternalError && e.getCause() != null) {
                clazz = e.getCause().getClass().getSimpleName();
            } else {
                clazz = e.getClass().getSimpleName();
            }
            Integer count = exceptionCounts.get(clazz);
            exceptionCounts.put(clazz, count == null ? 1 : count + 1);
            result = e.toString();
            if (!ProcessFailedTests || ShowFailedTestsResults) {
                e.printStackTrace();
            }
        }
        if (fastROutputManager.outputFile != null) {
            fastROutputManager.addTestResult(testElementName, input, result, keepTrailingWhiteSpace);
        }
        microTestInfo.fastROutput = result;
        return TestOutputManager.prepareResult(result, keepTrailingWhiteSpace);
    }

    public static boolean generatingExpected() {
        return expectedOutputManager.generate;
    }

    /**
     * Evaluate expected output from {@code input}. By default the lookup is based on {@code input}
     * but can be overridden by providing a non-null {@code testIdOrNull}.
     */
    protected String expectedEval(String input, boolean hasGeneratedOutput, TestTrait... traits) {
        if (generatingExpected()) {
            // generation mode
            return genTestResult(input, hasGeneratedOutput, traits);
        } else {
            // unit test mode
            String expected = expectedOutputManager.getOutput(input);
            if (expected == null) {
                // get the expected output dynamically (but do not update the file)
                expectedOutputManager.createRSession();
                expected = genTestResult(input, hasGeneratedOutput);
                if (expected == null) {
                    expected = "<<NO EXPECTED OUTPUT>>";
                }
            }
            microTestInfo.expectedOutput = expected;
            return expected;
        }
    }

    private String genTestResult(String input, boolean hasGeneratedOutput, TestTrait... traits) {
        return expectedOutputManager.genTestResult(this, testElementName, input, localDiagnosticHandler, expectedOutputManager.checkOnly, keepTrailingWhiteSpace, hasGeneratedOutput, traits);
    }

    /**
     * Creates array with all the combinations of parameters substituted in template. Substitution
     * is done via '%NUMBER', i.e. '%0' is replaced with all the values from the first array.
     */
    protected static String[] template(String template, String[]... parameters) {
        return TestOutputManager.template(template, parameters);
    }

    protected static String[] join(String[]... arrays) {
        return TestOutputManager.join(arrays);
    }

    /**
     * Tests that require additional {@link Engine} global symbols should override this, which will
     * be called just prior to the evaluation.
     */
    public void addPolyglotSymbols(@SuppressWarnings("unused") FastRContext context) {
    }

    private static final LocalDiagnosticHandler localDiagnosticHandler = new LocalDiagnosticHandler();

    private static class LocalDiagnosticHandler implements TestOutputManager.DiagnosticHandler {
        private boolean quiet;

        @Override
        public void warning(String msg) {
            System.out.println("\nwarning: " + msg);
        }

        @Override
        public void note(String msg) {
            if (!quiet) {
                System.out.println("\nnote: " + msg);
            }
        }

        @Override
        public void error(String msg) {
            System.err.println("\nerror: " + msg);
        }

        void setQuiet() {
            quiet = true;
        }
    }

    protected static boolean deleteDir(Path dir) {
        try {
            Files.walkFileTree(dir, DELETE_VISITOR);
        } catch (Throwable e) {
            return false;
        }
        return true;
    }

    private static final class DeleteVisitor extends SimpleFileVisitor<Path> {

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            return del(file);
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return del(dir);
        }

        private static FileVisitResult del(Path p) throws IOException {
            Files.delete(p);
            return FileVisitResult.CONTINUE;
        }
    }

    private static final DeleteVisitor DELETE_VISITOR = new DeleteVisitor();

    private static void addOutputHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (!generatingExpected()) {
                    IncludeList.report();
                }
                if (!unexpectedSuccessfulMicroTests.isEmpty()) {
                    System.out.println("Unexpectedly successful tests:");
                    for (String test : new TreeSet<>(unexpectedSuccessfulMicroTests)) {
                        System.out.println(test);
                    }
                }
                if (!exceptionCounts.isEmpty()) {
                    System.out.println("Exceptions encountered during test runs:");
                    for (Entry<String, Integer> entry : exceptionCounts.entrySet()) {
                        System.out.println(entry);
                    }
                }
                System.out.println("            tests | inputs");
                System.out.printf("successful: %6d | %6d%n", successfulTestCount, successfulInputCount);
                double successfulTestPercentage = 100 * successfulTestCount / (double) (successfulTestCount + failedTestCount + ignoredTestCount);
                double successfulInputPercentage = 100 * successfulInputCount / (double) (successfulInputCount + failedInputCount + ignoredInputCount);
                System.out.printf("            %5.1f%% | %5.1f%%%n", successfulTestPercentage, successfulInputPercentage);
                System.out.printf("   ignored: %6d | %6d%n", ignoredTestCount, ignoredInputCount);
                System.out.printf("    failed: %6d | %6d%n", failedTestCount, failedInputCount);
            }
        });
    }

    /**
     * Called before an actual evaluation happens.
     */
    public void beforeEval() {
        // empty
    }
}
