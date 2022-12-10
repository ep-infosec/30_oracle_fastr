# FastR Testing

Testing of FastR has two aspects: unit tests and package tests. Unit tests are small snippets of R code intended to test a specific aspects of R, typically one of the builtin functions. These are managed using the Java JUnit framework and all tests must pass before a change is pushed to external repository. Package testing uses the standard GNU R package test machinery and, currently, no packages
are tested before a change is pushed to external repository. Note, however, that the unit tests do test the installation of the "recommended" packages that are distributed with GNU R, plus some internal packages, e.g. `testrffi` for testing the R FFI interface.

The unit testing works by executing the R test and the comparing the output with that from GNU R, which is cached in the file `ExpectedTestOutput.test` in the `com.oracle.truffle.r.test` project. The tests are typically organized as "micro-tests" within a single JUnit test method. Exact matching of the context of error message and warnings is not required (but must be declared in a `TestTrait` argument to the micro-test).

## Unit Tests

The unit tests reside mainly in the `com.oracle.truffle.r.test` project, with a smaller number in the `com.oracle.truffle.r.nodes.test` project. The execution of the tests uses the `unittest` command that is built into `mx` and
used by all the Truffle languages. See `mx unittest --help` for a complete description of the options. Certain system properties are used to control the test environment, a;; of which begin with `fastr.test`.

1. `fastr.test.trace.tests`: this causes the specific test method being executed to be output to the standard output. A sometimes useful debugging tool.
2. `fastr.test.check.expected`: this can be used in combination with `fastr.test.generate` to checked whether `ExpectedTestOutput.test` is in sync with the current set of tests.
3. `fastr.test.generate`: Used internally by `mx rtestgen`, see below.
4. `fastr.test.generate.quiet`; Used internally by `mx rtestgen`, see below.

For convenience and backwards compatibility FastR provides some wrapper commands that invoke `unittest` with specific arguments:

1. `mx rutgen`: small set of the most basic tests
2. `mx rutsimple`: larger set of the most basic tests and Tuffle TCK
3. `mx rutgate`: all the tests that run in the gate


For example to debug a unit test under an IDE, it is important to disable the internal timeout mechanism that detects looping tests, vis:

    mx -d unittest -Dfastr.test.timeout sometestclass

Note that no value for `fastr.test.timeout` is treated as in infinite timeout. Any other value is expected to be an integer value, interpreted as seconds.

### Regenerating ExpectedTestOutput.test

After adding, removing or altering units tests (including the `TestTrait` argument), it is necessary to regenerate the `ExpectedTestOutput.test` file, vis:

    mx rtestgen

## Package Tests

### Cheat sheet

Test single package and visually compare the outputs using `meld` (look only at `*.preprocessed` files):

    mx pkgtest --verbose --dump-preprocessed --cache-pkgs dir=/path/to/cache --pkg-pattern ^MatrixModels$
    meld test.fastr test.gnur

Run RFFI tests:

    mx pkgtest --repos FASTR --run-tests testrffi

Test all gated packages:

    mx pkgtest --cache-pkgs dir=/path/to/cache --pkg-filelist com.oracle.truffle.r.test.packages/gated

Build package cache beforehand:

    mx r-pkgcache --vm fastr --cache-dir /path/to/cache --pkg-pattern <pkg-pattern>

Run `testthat` tests manually:

    library(testthat); library(mypackage); test_package('mypackage')

Take a look at `library/mypackage/tests/testthat` -- all files matching `test-*.R` are tests, 
you can run individual tests by: 

    test_package('mypackage', filter='sometestname')

### Introduction to packages

The R ecosystem is heavily based on packages contributed by the user community, and the standard CRAN repository contains over 6000 such packages. Naturally, many of these serve rather obscure areas but there is a small subset that are extremely popular and widely used, for example the "top 100" most popular packages cited here.

Using a package in R is actually a two step process. First a package must be "installed" and then it can be "loaded" into an R session. The installation step takes the package as a gzipped tar file from the repository, unpacks it and then does some processing before installing the result in a "library" directory. The processing may involve C or Fortran compilation if the package uses the R foreign function interface, as many do.The default installation library location is the system library, which is where the packages included with GnuR are stored, e.g. `base`, `stats`. However, additional directories can be specified through the `R_LIBS_USER` or `R_LIBS` environment variables.

### Installation of a package

A package can be installed in three ways:

    using the command line tool ./bin/R CMD INSTALL  package.tar.gz
    using the command line tool ./bin/R CMD INSTALL  path/to/decompressed/package
    using utils::install.packages(pkgname) from within an R session
    using the lower level tools:::.install_packages(args) from within an R session

A final step in both these approaches is to test that the package can be loaded (see below). The virtue of the second approach is that it automatically handles the download of the package from the repository. The third approach works when you have access to package tar file, vis:

    tools:::.install_packages(c("-d", "digest_0.6.9.tar.gz"))

The `-d` outputs additional tracing of the package installation process. The argument values are the same as for `R CMD INSTALL`.

### Loading a package

A package is loaded into an R session using `library(pkgname)` or `require(pkgname)`, which adds it to the search path. `pkgname` can be a quoted string or just the package name, e.g. `library(digest)`. The unquoted form takes advantage of R's lazy evaluation mechanism and the `substitute` builtin.

### Testing a package

Package developers can provide tests in several ways. To enable the full set of tests a package must be installed with the `--install-tests` option, for example:

    using command line tool ./bin/R CMD INSTALL --install-tests /path/to/directory
    using utils::install.packages('packageName', INSTALL_opts='--install-tests')

Once installed a package can be manually tested with the `tools::testInstalledPackage` function.

The `mx pkgtest` command described below handles the installation with tests and running the tests.

### Package Installation and Testing with mx

Package installation and testing is partly handled by a R script `r/install.packages.R` in the `com.oracle.truffle.r.test.packages` project and partly by an `mx` script. There are two relevant `mx` commands, `installpkgs` and `pkgtest`. The former is simply a wrapper to `install.packages.R`, whereas `pkgtest` contains additional code to gather and compare test outputs.

#### The install.packages.R script

While normally run with FastR using the `mx installpkgs` wrapper, this script can be used standalone using `Rscript`, thereby allowing to be used by GNU R also.
The command has a rather daunting set of options but, for normal use, most of these do not need to be set.

##### Usage

    mx installpkgs [--repos list]
                   [--verbose | -v] [-V]
                   [--dryrun]
                   [--no-install | -n]
                   [--install-dependents-first]
                   [--run-mode mode]
                   [--pkg-filelist file]
                   [--testdir dir]
                   [--pkg-list-installed]
                   [--print-ok-installs]
                   [--list-versions]
                   [--use-installed-pkgs]
                   [--invert-pkgset]
                   [--alpha-daily]
                   [–count-daily count]
                   [--random count]
                   [–pkg-pattern regexp]
                   [--run-tests]
                   [pattern]

A single unkeyworded argument, i.e. `pattern` is interpreted as if it were `-pkg-pattern pattern`.

Key concepts are discussed below.

##### CRAN Mirror
Packages are downloaded and installed from the repos given by the `repos` argument, a comma-separated list of `name[=value]` pairs, 
that defaults to `CRAN`. CRAN packages are downloaded from a CRAN mirror. When the standard `utils::install_packages` function is run interactively, 
the user is prompted for a mirror. To avoid such interaction, `install.packages` has two ways for specifying a mirror. 
The default CRAN mirror is specified in `com.oracle.truffle.r.native/Makefile` but this can be changed either with `CRAN=url` 
or the environment variable `CRAN_MIRROR`.  The `FASTR` repo is internal to the source base and contains FastR-specific test packages. 
The BioConductor repo can be added by setting `--repos BIOC`. User defined repos can be specified by `USERNAME=url`. N.B. For file system paths this must be a `file:` URL.

##### Installation Directory
The directory in which to install the package can be specified either by setting the `R_LIBS_USER` environment variable or with the `--lib` command line argument. The former is recommended and indeed required for running tests after installation (the testing system does not honor the `--lib` argument).

##### Specifying packages to Install
If the `--pkg-filelist` argument is provided then the associated file should contain a list of packages to install, one per line. Otherwise if a package pattern argument is given, then all packages matching the (R) regular expression are candidates for installation, otherwise all available packages are candidates, computed by invoking the `available.packages()` function. The candidate set can be adjusted with additional options.  The `--use-installed-pkgs` option will cause `install.packages` to analyze the package installation directory for existing successfully installed packages and remove those from the candidate set for installation. This option is implied by `--no-install`. Some convenience options implicitly set `--pkg-filelist`, namely:

    --ok-only: sets it to the file `com.oracle.truffle.r.test.packages/ok.packages`. This file is a list of packages that are known to install.

N.B. This file is updated only occasionally. Regressions, bug fixes, can render it inaccurate.

Two options are designed to be used for a daily package testing run. These are based on the day of the year and install/test a rolling set of packages:

    --alpha-daily: set package list to those starting with the letter computed as yday %% 26. E.g., yday 0 is ^[Aa], yday 1 is ^[Bb]
    --count-daily count: Install "count" packages starting at an index computed from yday. The set of packages repeats every N days where N is the total number of packages divided by count.

Finally, the `--invert-pkgset` option starts with the set from `available.packages()` and then subtracts the candidate set computed as described above and sets the candidate set to the result.

N.B.: `--pkg-filelist` and `--pkg-pattern` are mutually exclusive.

##### Installing Dependent Packages:
`install.packages` installs the list of requested packages one by one. By default `utils::install.packages` always installs dependent packages, even if the dependent package has already been installed. This can be particularly wasteful if the package fails to install. Setting `--install-dependents-first` causes `install.packages` to analyse the dependents and install them one by one first, aborting the installation of the depending package if any fail.

##### Run Mode
GNU R uses R/Rscript sub-processes in the internals of package installation and testing, but multiple package installations (e.g. using `--pkg-filelist`) would normally be initiated from a single top-level R process. This assumes that the package installation process itself is robust. This mode is defined as the `internal` mode variant of the `--run-mode` option. Since FastR is still under development, in `internal` mode a failure of FastR during a single package installation would abort the entire `install.packages` execution. Therefore by default `install.packages` runs each installation in  a separate FastR sub-process, referred to as `system` mode (because the R `system` function is used to launch the sub-process).

When running `install.packages` under GNU R, it makes sense to set `--run-mode internal`.

##### Use with GNU R

Basic usage is:

    $ Rscript $FASTR_HOME/fastr/com.oracle.truffle.r.test.packages/r/install.packages.R --run-mode internal [options]

where `FASTR_HOME` is the location of the FastR source.

##### Testing
Testing packages requires that they are first installed, so all of the above is relevant. Testing is enabled by the `--run-tests` option and all successfully installed packages are tested.

##### Additional Options

    --verbose | -v: output tracing on basic steps
    -V: more verbose tracing
    --dry-run: output what would be installed but don't actually install
    --no-install | -n: suppress installation phase (useful for --run-tests)
    --random count: install count packages randomly chosen from the candidate set
    --testdir dir: store test output in dir (defaults to "test").
    --list-versions: for the candidate set of packages to install list the name and version in format: name,version,
    --run-tests: run packages tests on the successfully installed packages (not including dependents)
    --dump-preprocessed: dump the preprocessed output (see below)

##### Debbuging the script
To debug the script, the following snippet should be useful:
```R
executable <- "/home/pmarek/dev/R-4.0.3/bin/R"
pkgpattern <- "testrffi"
test_dir <- "/home/pmarek/tmp/test.fastr"
test_executable <- "/home/pmarek/dev/fastr/bin/R"
install_lib <- "/home/pmarek/fastr_libraries/R-4.0.3"
Sys.setenv("R_LIBS_USER" = install_lib)
commandArgs <- function(...) {
  c(
    executable, 
    "--repos", "FASTR=file://.../com.oracle.truffle.r.test.native/packages/repo,CRAN=file://.../minicran/2021-02-01/",
    "--no-install",
    "--very-verbose",
    "--dry-run",
    "--run-tests",
    "--cache-pkgs", "dir=/home/pmarek/fastr_pkgcache,sync=TRUE,vm=fastr",
    "--test-mode", "system",
    "--test-executable", test_executable,
    "--testdir", test_dir,
    "--pkg-pattern", pkgpattern
  )
}
source("install.packages.R")
debugonce(run)
run()
```

#### Examples

    $ export R_LIBS_USER=`pwd`/lib.install.packages

    $ mx installpkgs --pkg-pattern '^A3$'

Install the `A3` package (and its dependents) in `$R_LIBS_USER`. The dependents (`xtable`, `pbapply`) will be installed implicitly by the  underlying R install.packages function

    $ mx installpkgs --repos CRAN=file://path-to-local-cran-mirror --pkg-pattern '^A3$'

Similar to above but uses a local CRAN mirror stored in `path-to-local-cran-mirror`.

    $ mx installpkgs --install-dependents-first--pkg-pattern '^A3$'

Similar to the above but the dependents of A3 are explicitly installed first. This is equivalent to using `--pkg-filelist` file, where file would contain xtable, pbapply and A3 in that order.

    $ mx installpkgs --pkg-filelist specific

Install exactly those packages (and their dependents) specified, one per line, in the file `specific`.

    $ mx installpkgs --ok-only --invert-pkgset --random-count 100

Install 100 randomly chosen packages that are not in the file `com.oracle.truffle.r.test.packages/ok.packages`.

    $ mx installpkgs '^Rcpp$'

Attempt to install the `Rcpp` package. N.B. The regular expression prevents the installation of other packages beginning with `Rcpp`.

#### The mx pkgtest command

The `mx pkgtest` command is a wrapper on `mx installpkgs` that forces the `--run-tests` option and also executes the same tests under GnuR and compares the results. The packages are installed into `lib.install.packages.fastr` and `lib.install.packages.gnur`, respectively and the test results are stored in `test.fastr` and `test.gnur`, respectively. The differences between the results, computed using `diff -r`,  are stored per package in the `test.diffs` directory. All these directories are cleaned and re-created at the start of the run.

By default the local build of FastR and the internal GNU R that is built as part of the FastR build are used to run the tests. However, when `GRAALVM_HOME` is set to the location of a `GraalVM` binary installation, that is used for FastR and the `gnur` suite must be installed and built as a sibling to `fastr`.

#### Running/Debugging Tests Locally

To debug why a test fails requires first that the package is installed locally plus some understanding about how the test process operates. The R code that performs installation and testing makes use of R sub-processes, so simply running the main process under the Java debugger will not work. To demonstrate this we will use the `digest` package as an example.The following command will install the `digest` package in the directory specified by the `R_LIBS_USER` environment variable:

    $ FASTR_LOG_SYSTEM=1 mx installpkgs '^digest$'

First, note that, by default,  the `installpkgs` command itself introduces an extra level on sub-process in order to avoid a failure from aborting the entire install command when installing/testing multiple packages. You can see this by setting the environment variable `FASTR_LOG_SYSTEM` to any value. The first sub-process logged will be running the command `com.oracle.truffle.r.test.packages/r/install.package.R` and the second will be the one running `R CMD INSTALL --install-tests` of the digest package. For ease of debugging you can set the `--run-mode` option to `internal`, which executes the first phase of the install in the process running `installpkgs`. Similar considerations apply to the testing phase. By default a sub-process is used to run the `com.oracle.truffle.r.test.packages/r/test.package.R script`, which then runs the actual test using a sub-process to invoke `R CMD BATCH`. Again the first sub-process can be avoided using `--run-mode internal`. N.B. If you run the tests for `digest` you will see that there are four separate sub-processes used to run different tests. The latter three are the specific tests for digest that were made available by installing with `--install-tests`. Not all packages have such additional tests. Note that there is no way to avoid the tests being run in sub-processes so setting the `-d` option to the `installpkgs` command will have no effect on those. Instead set the environment variable `MX_R_GLOBAL_ARGS=-d` which will cause the sub-processes to run under the debugger. Note that you will not (initially) see the `Listening for transport dt_socket at address: 8000` message on the console, but activating the debug launch from the IDE will connect to the sub-process.

### INSTALLED PACKAGES CACHE

#### Description

Avoids re-installing of packages for every test. Packages are cached for a specific native API version, i.e., checksum of the native header files.

Directory structure:

    - pkg-cache-dir
    --+- version.table
    --+- libraryVERSION0
    ----+- packageArchive0.gz
    ----+- packageArchive1.gz
    ----+- ...
    --+- libraryVERSION1
    ----+- packageArchive0.gz
    ----+- packageArchive1.gz
    ----+- ...
    --+- ...

The API checksum must be provided because we do not want to rely on some R package to compute it.

#### Usage

Run `mx pkgtest --cache-pkgs dir=<pkg-cache-dir>,size=<cache-size>`, e.g.
```
mx pkgtest --cache-pkgs dir=/tmp/cache_dir --pkg-pattern ^MatrixModels$
```

The `pkg-cache-dir` key specifies the directory of the cache (mandatory, no default).
The `size` key specifies the number of different API versions for which to cache packages (optional, default=`2L`).

#### Details

The version must be provided externally such that the R script does not rely on any package.
The version must reflect the native API in the sense that if two R runtimes have the same native API version, then the packages can be used for both runtimes.

### Environment variables

Package-specific environment variables can be specified through the PKG_TEST_ENV_<pkgname> environment variable. 
The individual environment variable pairs are delimited by a comma, e.g. export PKG_TEST_ENV_miniUI="LANGUAGE=en,LC_ALL=C" 
specifies the environment variables LANGUAGE and LC_ALL for the miniUI package test.

### Output preprocessing

The test output can be preprocessed by sed like script in `com.oracle.truffle.r.test.packages/test.output.filter`. 
Run pkgtest with `--dump-preprocessed` to get the preprocessed output dumped next to the real output
Moreover, footer and header of the output file (copyright, test time, etc) are ignored by default.

### Ignoring some suggest packages

Edit variable `ignore.suggests` in `com.oracle.truffle.r.test.packages/r/install.packages.R` 
to ignore installation of suggested packages that are not necessary for the tests to run.

### testrfffi

FastR has tests for the native R API (what is in FastR implemented by `JavaUpCallsImpl`) in `com.oracle.truffle.r.test.native/packages/testrffi/testrffi`.
To install the `testrffi` package and run the tests: `mx pkgtest --repos FASTR --run-tests testrffi` or 
just install it to the default library using command line:

     rm -f com.oracle.truffle.r.test.native/packages/testrffi/testrffi/src/*.o
     rm -f com.oracle.truffle.r.test.native/packages/testrffi/testrffi/src/*.so
     ./bin/R CMD INSTALL com.oracle.truffle.r.test.native/packages/testrffi/testrffi

Note that you can also install this package on GNU-R using the same commands.

Once installed via `mx pkgtest`, you can load it in GNUR/FastR session by:

     .libPaths(c( .libPaths(), "./lib.install.packages.gnur")); library(testrffi);
     .libPaths(c( .libPaths(), "./lib.install.packages.fastr")); library(testrffi);
     
You can leave out the `.libPaths` call if you installed the package to the default library via `./bin/R CMD INSTALL ...`.      
     
All the native R API functions that take and return R objects (e.g. not raw C types) have R wrappers in testrffi, 
e.g. native function `SETCAR` can be invoked via `api.SETCAR` if you suspect that some native R API function behaves 
differently in FastR vs GNUR, you can simply verify, e.g. run `api.SETCAR(NULL, NULL)` in both GNU R and FastR to 
find out what it should return and if FastR is compatible. 

You can add your findings as tests into `com.oracle.truffle.r.test.native/packages/testrffi/testrffi/tests/somefile.R`.
