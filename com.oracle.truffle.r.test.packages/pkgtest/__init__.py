#
# Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

'''
The pkgtest command operates in two modes:
1. In development mode it uses the FastR 'Rscript' command and the internal GNU R for test comparison
2. In production mode it uses the _opts.graalvm 'Rscript' command and a GNU R loaded as a sibling suite. This is indicated
by the environment variable 'FASTR__opts.graalvm' being set. (_opts.graalvm_FASTR is also accepted for backwards cmpatibility)

Evidently in case 2, there is the potential for a version mismatch between FastR and GNU R, and this is checked.

In either case all the output is placed in the fastr suite dir. Separate directories are used for FastR and GNU R package installs
and tests, namely 'lib.install.packages.{fastr,gnur}' and 'test.{fastr,gnur}' (sh syntax).
'''
from os.path import relpath
import shutil, os, re

from .subproc import pkgtest_run
from .output_filter import select_filters_for_package
from .fuzzy_compare import fuzzy_compare
from .util import *
from typing import List, Optional, Any, Dict, Tuple, Union


class RVM(object):
    def __init__(self, rvm_id: str, name: str):
        self.id = rvm_id
        self.name = name

    def __str__(self) -> str:
        return self.name

    def __repr__(self) -> str:
        return "RVM(id=%s, name=%s)" % (self.id, self.name)
    
    def __eq__(self, other: Any) -> bool:
        if isinstance(other, RVM):
            return self.id == other.id
        return False

    @staticmethod
    def create_dir(testdir: str) -> str:
        shutil.rmtree(testdir, ignore_errors=True)
        # Note: The existence check still makes sense because 'shutil.rmtree' won't do anything if 'testdir' is a symlink.
        if not os.path.exists(testdir):
            os.mkdir(testdir)
        return testdir

    @staticmethod
    def get_default_testdir(suffix: str) -> str:
        return join(get_fastr_repo_dir(), 'test.' + suffix)

    def get_testdir(self) -> str:
        '''Determines the path to the test output directory depending on the 
        provided R runtime and considering the arguments.
        '''
        opts = get_opts()
        attr_name = self.id + "_testdir"
        if hasattr(opts, attr_name):
            return getattr(opts, attr_name)

        # default:
        return RVM.get_default_testdir(self.id)

    def create_testdir(self) -> str:
        ''' Actually creates the testdir on the file system.'''
        return RVM.create_dir(self.get_testdir())


RVM_FASTR = RVM('fastr', 'FastR')
RVM_GNUR = RVM('gnur', 'GnuR')


def _create_tmpdir(rvm: RVM) -> str:
    if not isinstance(rvm, RVM):
        raise TypeError("Expected object of type 'RVM' but got '%s'" % str(type(rvm)))
    tmp_dir = os.environ.pop("TMPDIR", None)
    if tmp_dir:
        install_tmp = join(tmp_dir, "install.tmp." + rvm.id)
    else:
        install_tmp = join(get_fastr_repo_dir(), "install.tmp." + rvm.id)
    shutil.rmtree(install_tmp, ignore_errors=True)
    os.makedirs(install_tmp)
    return install_tmp


def _create_libinstall(rvm: RVM, no_install: bool) -> Tuple[str, str]:
    '''
    Create lib.install.packages.<rvm>/install.tmp.<rvm>/test.<rvm> for <rvm>: fastr or gnur
    If no_install is True, assume lib.install exists and is populated (development)
    '''
    if not isinstance(rvm, RVM):
        raise TypeError("Expected object of type 'RVM' but got '%s'" % str(type(rvm)))
    libinstall = join(get_fastr_repo_dir(), "lib.install.packages." + rvm.id)
    if not no_install:
        # make sure its empty
        shutil.rmtree(libinstall, ignore_errors=True)
        if os.path.exists(libinstall):
            logging.warning("could not clean temporary library dir %s" % libinstall)
        else:
            os.mkdir(libinstall)
    else:
        if not os.path.exists(libinstall) or len(os.listdir(libinstall)) == 0:
            logging.warning(f"{libinstall} directory does not exist or it does not contain any installed packages")
    install_tmp = _create_tmpdir(rvm)
    rvm.create_testdir()
    return libinstall, install_tmp


def _find_subdir(root: str, name, fatalIfMissing=True) -> str:
    for dirpath, dnames, _ in os.walk(root):
        for f in dnames:
            if f == name:
                return os.path.join(dirpath, f)
    if fatalIfMissing:
        raise Exception(name)


def _packages_test_project() -> str:
    return 'com.oracle.truffle.r.test.packages'


def _packages_test_project_dir() -> str:
    return _find_subdir(get_fastr_repo_dir(), _packages_test_project())


def _ensure_R_on_PATH(env: Dict[str, str], bindir: str) -> None:
    '''
    Some packages (e.g. stringi) require that 'R' is actually on the PATH
    '''
    env['PATH'] = join(bindir) + os.pathsep + os.environ['PATH']


def _installpkgs_script() -> str:
    packages_test = _packages_test_project_dir()
    return join(packages_test, 'r', 'install.packages.R')


def _get_ignore_suggests_file() -> str:
    return join(_packages_test_project_dir(), 'ignore.suggests')


def commit_fastr_builtins() -> int:
    '''
    There are some FastR builtins which we also want to use in GnuR (i.e. 'install.fastr.packages').
    This function deparses these functions and writes them into a file which is then loaded by GnuR.
    '''
    dest_file = join(_packages_test_project_dir(), 'r', 'fastr.functions.rdx')
    cmd_line = [get_fastr_rscript(), "--silent", "-e", '{ fastrRepoPath <- NULL; save(fastrRepoPath, install.fastr.packages, file="%s") }' % dest_file]
    logging.debug("Generating fastr.functions.R: " + str(cmd_line))
    return pkgtest_run(cmd_line)


def _run_install_packages_script(rscript_path: str, args: List[str], **kwargs) -> int:
    """
    Runs 'install.packages.R' script with the provided 'Rscript' binary.
    """
    if 'env' in kwargs:
        env = kwargs['env']
    else:
        env = os.environ.copy()
        kwargs['env'] = env

    out = kwargs.get('out', None)
    err = kwargs.get('err', None)

    _ensure_R_on_PATH(env, os.path.dirname(rscript_path))
    verbose_flag = ['--verbose'] if get_opts().verbose >= 1 else []
    cmd_line = [rscript_path] + verbose_flag + [_installpkgs_script()] + args
    logging.debug("Running {!s} with cmd line: {!s}".format(rscript_path, cmd_line))
    return pkgtest_run(cmd_line, nonZeroIsFatal=kwargs.get("nonZeroIsFatal", True), out=out, err=err, env=env)


def _fastr_installpkgs(args: List[str], **kwargs) -> int:
    """
    Runs 'install.packages.R' script with fastr.
    """
    if "FASTR_WORKING_DIR" in os.environ:
        kwargs["env"]["TMPDIR"] = os.environ["FASTR_WORKING_DIR"]
    return _run_install_packages_script(get_fastr_rscript(), args, **kwargs)


def _gnur_installpkgs(args: List[str], **kwargs) -> int:
    """
    Runs 'install.packages.R' script with GnuR.
    """
    return _run_install_packages_script(get_gnur_rscript(), args, **kwargs)


def prepare_r_install_arguments(args: List[str]) -> List[str]:
    """ Called just from pkgtest """
    # also propagate verbosity flag
    verbosity_level = get_opts().verbose
    if verbosity_level == 1:
        args += ["--verbose"]
    elif verbosity_level > 1:
        args += ["--very-verbose"]

    # Set pkg cache from env var if possible
    if "FASTR_PKGS_CACHE_OPT" in os.environ:
        if "--cache-pkgs" in args:
            warn(f"Both 'FASTR_PKGS_CACHE_OPT' env var and --cache-pkgs option are set, env var precedes the option")
            arg_idx = args.index("--cache-pkgs")
            # Drop the option with its value
            args.pop(arg_idx)
            args.pop(arg_idx)
        args += ["--cache-pkgs", os.environ["FASTR_PKGS_CACHE_OPT"]]
    elif "--cache-pkgs" not in args:
        warn("If you want to use R packages cache, export environment variable FASTR_PKGS_CACHE_OPT or use "
             "--cache-pkgs option. See option '--cache-pkgs' of 'mx pkgtest' for the syntax.")

    # install and test the packages, unless just listing versions
    if not '--list-versions' in args:
        args += ['--run-tests']
        args += ['--test-mode', 'system']
        args += ['--test-executable', get_fastr_rscript()]
        args += ['--testdir', get_opts().fastr_testdir]
        if not '--print-install-status' in args:
            args += ['--print-install-status']

    # get default CRAN mirror from our FastR home
    default_cran_mirror_url = "CRAN=" + get_default_cran_mirror()

    # We intercept '--repos SNAPSHOT' since in GraalVM mode, we do not necessarily have a 'etc/DEFAULT_CRAN_MIRROR' for
    # GnuR in an accessible location.
    if '--repos' not in args and get_opts().repos:
        # repos were set by the FASTR_REPOS environment variable.
        args += ['--repos', get_opts().repos]
    if '--repos' in args:
        repos_idx = args.index('--repos')
        if repos_idx + 1 < len(args):
            if 'SNAPSHOT' in args[repos_idx + 1]:
                logging.info("Overwriting '--repos SNAPSHOT,...' with '--repos %s,...'" % default_cran_mirror_url)
                args[repos_idx + 1] = args[repos_idx + 1].replace("SNAPSHOT", default_cran_mirror_url)
    else:
        logging.info("No '--repos' specified, using default CRAN mirror: " + default_cran_mirror_url)
        args += [ "--repos", default_cran_mirror_url]

    # add path to file containing suggests packages that are safe to ignore
    args += ["--ignore-suggests", _get_ignore_suggests_file()]

    if "--pkg-pattern" not in args and get_opts().pattern:
        args += ["--pkg-pattern", get_opts().pattern]
    if "--pkg-filelist" not in args and get_opts().filelist:
        args += ["--pkg-filelist", get_opts().filelist]

    return args


def prepare_r_install_and_test_arguments(args: List[str]) -> List[str]:
    """ Called from pkgtest """
    args = prepare_r_install_arguments(args)
    # install and test the packages, unless just listing versions
    if not '--list-versions' in args:
        args += ['--run-tests']
        args += ['--testdir', get_opts().fastr_testdir]
        if not '--print-install-status' in args:
            args += ['--print-install-status']
    return args


def pkgtest(args: List[str]) -> int:
    '''
    Package installation/testing.

    Options:
        --cache-pkgs dir=DIR     Use package cache in directory DIR (will be created if not existing).
                                 Optional parameters:
                                     size=N             Maximum number of different API versions in the cache.
                                     sync=[true|false]  Synchronize the cache
                                     vm=[fastr|gnur]
                                 Example: '--cache-pkgs dir=DIR,size=N,sync=true,vm=fastr'
                                 Can be set by FASTR_PKGS_CACHE_OPT environment variable.
        --no-install             Do not install any packages (can only test installed packages).
        --list-versions          List packages to be installed/tested without installing/testing them.
        --print-install-status   Prints status of the installed packages.

    Return codes:
        0: success
        1: install fail
        2: test fail
        3: install & test fail
    '''
    if '-h' in args or '--help' in args:
        print('==============================================')
        print('Common arguments for both r-pkgtest and r-pkgcache:')
        print('==============================================')
        common_arg_parser = get_common_arg_parser()
        common_arg_parser.prog = "mx r-pkgtest"
        common_arg_parser.print_help()
        print('\n==================================')
        print(f'Additional help for r-pkgtest:')
        print('==================================')
        print(pkgtest.__doc__)
        return 0
    unknown_args = parse_arguments(args)
    # common_install_args are shared between GnuR and FastR
    common_install_args = prepare_r_install_and_test_arguments(unknown_args)

    no_install = '--no-install' in common_install_args
    fastr_libinstall, fastr_install_tmp = _create_libinstall(RVM_FASTR, no_install)
    gnur_libinstall, gnur_install_tmp = _create_libinstall(RVM_GNUR, no_install)

    env = os.environ.copy()
    env["TMPDIR"] = fastr_install_tmp
    env['R_LIBS_USER'] = fastr_libinstall
    env['FASTR_OPTION_PrintErrorStacktracesToFile'] = 'false'
    env['FASTR_OPTION_PrintErrorStacktraces'] = 'true'

    # transfer required FastR functions to GnuR
    commit_fastr_builtins()

    # If '--cache-pkgs' is set, then also set the native API version value
    fastr_args = _set_pkg_cache_api_version(common_install_args, get_fastr_include_path())

    log_step('BEGIN', 'install/test', 'FastR')
    # Currently installpkgs does not set a return code (in install.packages.R)
    out = OutputCapture()
    rc = _fastr_installpkgs(fastr_args, nonZeroIsFatal=False, env=env, out=out, err=out)
    if rc != 0:
        # fatal error in FastR
        logging.info("FastR finished with non-zero exit code: " + str(rc))
        abort(status=rc)

    rc = 0
    for status in out.install_status.values():
        if not status:
            rc = 1
    log_step('END', 'install/test', 'FastR')

    single_pkg = len(out.install_status) == 1
    install_failure = single_pkg and rc == 1
    if '--run-tests' in common_install_args and not install_failure:
        # in order to compare the test output with GnuR we have to install/test the same
        # set of packages with GnuR
        ok_pkgs = [k for k, v in out.install_status.items() if v]
        gnur_args = _args_to_forward_to_gnur(common_install_args)

        # If '--cache-pkgs' is set, then also set the native API version value
        gnur_args = _set_pkg_cache_api_version(gnur_args, get_gnur_include_path())

        _gnur_install_test(gnur_args, ok_pkgs, gnur_libinstall, gnur_install_tmp)
        _set_test_status(out.test_info)
        logging.info('Test Status')
        for pkg, test_status in out.test_info.items():
            if test_status.status != "OK":
                rc = rc | 2
            logging.info('{0}: {1}'.format(pkg, test_status.status))

        diffdir = RVM.create_dir(RVM.get_default_testdir('diffs'))
        for pkg, _ in out.test_info.items():
            diff_file = join(diffdir, pkg)
            subprocess.call(['diff', '-r', _pkg_testdir(RVM_FASTR, pkg), _pkg_testdir(RVM_GNUR, pkg)],
                            stdout=open(diff_file, 'w'))

    shutil.rmtree(fastr_install_tmp, ignore_errors=True)
    return rc


def _set_pkg_cache_api_version(arg_list: List[str], include_dir: str) -> List[str]:
    '''
    Looks for argument '--cache-pkgs' and appends the native API version to the value list of this argument.
    '''
    if "--cache-pkgs" in arg_list:
        pkg_cache_values_idx = arg_list.index("--cache-pkgs") + 1
        if pkg_cache_values_idx < len(arg_list):
            arg_list_copy = arg_list[:]
            assert arg_list_copy is not arg_list
            if 'version=' in arg_list_copy[pkg_cache_values_idx]:
                logging.warning("Ignoring specified API version and using automatically computed one.")
                raise RuntimeError
            arg_list_copy[pkg_cache_values_idx] = arg_list_copy[pkg_cache_values_idx] + ",version={0}".format(computeApiChecksum(include_dir))
            return arg_list_copy
    return arg_list



class OutputCapture:
    def __init__(self):
        self.install_data: Dict[str, str] = dict()
        self.pkg: Optional[str] = None
        self.mode: Optional[str] = None
        self.start_install_pattern = re.compile(r"^BEGIN processing:\s*(?P<package>[a-zA-Z0-9.-]+)")
        self.test_pattern = re.compile(r"^(?P<status>BEGIN|END) testing:\s*(?P<package>[a-zA-Z0-9.-]+)")
        self.time_pattern = re.compile(r"^TEST_TIME:\s*(?P<package>[a-zA-Z0-9.-]+) (?P<time>[0-9.-]+)")
        self.status_pattern = re.compile(r"^(?P<package>[a-zA-Z0-9.-]+):\s*(?P<status>OK|FAILED)")
        self.install_status: Dict[str, bool] = dict()
        self.test_info: Dict[str, "PkgTestStatus"] = dict()

    def __call__(self, data: str) -> None:
        # The logger is always appending a newline at the end but we want to avoid double newlines.
        logging.info('subprocess output: ' + data[:-1] if data.endswith('\n') else data)
        if data == "BEGIN package installation\n":
            self.mode = "install"
            return
        elif data == "BEGIN install status\n":
            self.mode = "install_status"
            return
        elif data == "BEGIN package tests\n":
            self.mode = "test"
            return

        if self.mode == "install":
            start_install = re.match(self.start_install_pattern, data)
            if start_install:
                pkg_name = start_install.group(1)
                self.pkg = pkg_name
                self.install_data[self.pkg] = ""
            if self.pkg:
                self.install_data[self.pkg] += data
        elif self.mode == "install_status":
            if data == "END install status\n":
                self.mode = None
                return
            status = re.match(self.status_pattern, data)
            if status:
                pkg_name = status.group(1)
                self.install_status[pkg_name] = status.group(2) == "OK"
        elif self.mode == "test":
            test_match = re.match(self.test_pattern, data)
            if test_match:
                begin_end = test_match.group(1)
                pkg_name = test_match.group(2)
                if begin_end == "END":
                    _get_test_outputs(RVM_FASTR, pkg_name, self.test_info)
            else:
                time_match = re.match(self.time_pattern, data)
                if time_match:
                    pkg_name = time_match.group(1)
                    test_time = time_match.group(2)
                    get_pkg_test_status(self.test_info, pkg_name).test_time = test_time


class TestStatus(object):
    '''
    The base class for any test status.
    '''
    def __init__(self):
        self.status = "UNKNOWN"
        self.test_time = 0.0

    def set_status_indeterminate(self) -> None:
        self.status = "INDETERMINATE"
        self.test_time = -1.0

    def is_status_indeterminate(self) -> bool:
        if self.status == "INDETERMINATE":
            assert self.test_time == -1.0
            return True
        return False

    def set_status_code(self, new_status: str) -> None:
        if new_status == "INDETERMINATE":
            assert self.status in ["OK", "FAILED", "INDETERMINATE", "UNKNOWN"]
            self.set_status_indeterminate()
        elif new_status == "FAILED" and (self.status in ["OK", "FAILED", "UNKNOWN"]):
            self.status = "FAILED"
        elif new_status == "OK" and (self.status in ["OK", "UNKNOWN"]):
            self.status = "OK"
        else:
            # any other transition is not possible
            raise Exception(f"Transition from {self.status} to {new_status} should not be possible")


class TestFileStatus(TestStatus):
    '''
    Records the status of a test file. status is either "OK", "FAILED", or "INDETERMINATE".
    "FAILED" means that the file had a .fail extension.
    "INDETERMINATE" (only applicable for a FastR test output file) means that the corresponding GnuR test output file
    had a .fail extension.
    '''

    def __init__(self, test_status: "PkgTestStatus", status: str, abspath: str):
        super(TestFileStatus, self).__init__()

        self.test_status = test_status
        self.status = status
        self.abspath = abspath
        # ok, skipped, failed
        self.report: Tuple[int, int, int] = (-1, -1, -1)
        if status == "OK":
            # At this point, status == "OK" means that we had no '.fail' output file and we will investigate the single
            # test cases. So, initially we claim the test was skipped because if GnuR failed on the test, we state that
            # we skipped it.
            self.report = 0, 1, 0
        elif status == "FAILED":
            self.report = 0, 0, 1
        else:
            raise ValueError('Invalid initial test file status: %s (allowed: "OK", "FAILED")' % status)

    def set_status_code(self, new_status: str) -> None:
        super(TestFileStatus, self).set_status_code(new_status)
        self.test_status.set_status_code(new_status)

    def set_report(self, ok: int, skipped: int, failed: int) -> None:
        self.report = ok, skipped, failed

    def get_report(self) -> Tuple[int, int, int]:
        if self.is_status_indeterminate():
            ok, skipped, failed = self.report
            return ok, 0, skipped + failed
        else:
            return self.report

    def __str__(self) -> str:
        return f"Test Status:\n{self.status} (time: {str(self.test_time)} s)"


class PkgTestStatus(TestStatus):
    '''Records the test status of a package. status ends up as either "OK", or "FAILED",
    unless GnuR also failed in which case it stays as "INDETERMINATE".
    The testfile_outputs dict is keyed by the relative path of the output file to
    the 'test/pkgname' directory. The value is an instance of TestFileStatus.
    '''

    def __init__(self):
        super(PkgTestStatus, self).__init__()
        self.testfile_outputs: Dict[str, TestFileStatus] = dict()

    def __str__(self) -> str:
        return f"Overall package test status:\n{self.status} (time: {str(self.test_time)} s)"


def _pkg_testdir(rvm: RVM, pkg_name: str) -> str:
    '''
    Returns the path to the package-specific test directory like "test.fastr/pkg_name"
    '''
    if not isinstance(rvm, RVM):
        raise TypeError("Expected object of type 'RVM' but got '%s'" % str(type(rvm)))
    return join(rvm.get_testdir(), pkg_name)


def get_pkg_test_status(test_info: Dict[str, PkgTestStatus], pkg_name: str) -> PkgTestStatus:
    '''
    Get the test status (class TestStatus) for a given package.
    It is created on demand if it does not exist yet.
    '''
    test_status = test_info.get(pkg_name)
    if not test_status:
        test_status = PkgTestStatus()
        test_info[pkg_name] = test_status
    return test_status


def _get_test_outputs(rvm: RVM, pkg_name: str, test_info: Dict[str, PkgTestStatus]) -> None:
    pkg_testdir = _pkg_testdir(rvm, pkg_name)
    test_status: Optional[PkgTestStatus] = None
    for root, _, files in os.walk(pkg_testdir):
        if not test_status:
            test_status = get_pkg_test_status(test_info, pkg_name)
        for f in files:
            ext = os.path.splitext(f)[1]
            # suppress .pdf's for now (we can't compare them)
            # ignore = ['.R', '.Rin', '.prev', '.bug', '.pdf', '.save']
            # if f == 'test_time' or ext in ignore:
            #     continue
            included = ['.Rout', '.fail']
            if f == 'test_time' or not ext in included:
                continue
            status = "OK"
            if ext == '.fail':
                # some fatal error during the test
                status = "FAILED"
                f = os.path.splitext(f)[0]

            absfile = join(root, f)
            relfile = relpath(absfile, pkg_testdir)
            test_status.testfile_outputs[relfile] = TestFileStatus(test_status, status, absfile)


def _args_to_forward_to_gnur(args: List[str]) -> List[str]:
    forwarded_args_with_value = ('--repos', '--run-mode', '--cache-pkgs', '--test-mode', '--ignore-suggests', '--pkg-pattern', '--pkg-filelist')
    forwarded_args_without_value = ('--verbose', '--very-verbose')
    result = []
    i = 0
    while i < len(args):
        arg = args[i]
        if arg in forwarded_args_with_value:
            result.append(arg)
            i = i + 1
            result.append(args[i])
        elif arg in forwarded_args_without_value:
            result.append(arg)
        i = i + 1
    return result


def _remove_arg_with_value(argname: str, args: List[str]) -> List[str]:
    for i in range(len(args)):
        if args[i] == argname:
            assert i < len(args), f"There should be some value for the argument {argname}"
            return args[:i] + args[i+2:]
    return args


def _gnur_install_test(forwarded_args: List[str], pkgs: List[str], gnur_libinstall: str, gnur_install_tmp: str) -> None:
    '''
    Install/test with GNU R  exactly those packages that installed correctly with FastR.
    N.B. That means that regardless of how the packages were specified to pkgtest
    we always use a '--pkg-filelist' arg to GNU R
    '''
    if len(pkgs) == 0:
        logging.info('No packages to install/test on GNU-R (install/test failed for all packages on FastR?)')
        return

    gnur_packages = join(get_fastr_repo_dir(), 'gnur.packages')
    logging.debug("Going to test packages (on GNU-R): " + str(pkgs))
    with open(gnur_packages, 'w') as f:
        for pkg in pkgs:
            f.write(pkg)
            f.write('\n')
    env = os.environ.copy()
    env["TMPDIR"] = gnur_install_tmp
    env['R_LIBS_USER'] = gnur_libinstall
    env["TZDIR"] = "/usr/share/zoneinfo/"

    # forward any explicit args to pkgtest
    args = forwarded_args
    # Remove both --pkg-filelist and --pkg-pattern from args as they are mutually exclusive
    args = _remove_arg_with_value('--pkg-filelist', args)
    args = _remove_arg_with_value('--pkg-pattern', args)
    args += ['--pkg-filelist', gnur_packages]
    args += ['--run-tests']
    args += ['--test-executable', get_gnur_rscript()]
    args += ['--testdir', get_opts().gnur_testdir]
    log_step('BEGIN', 'install/test', 'GnuR')

    _gnur_installpkgs(args, env=env)

    log_step('END', 'install/test', 'GnuR')


def get_contents(files: List[str]) -> str:
    def robust_readlines(file: str) -> List[str]:
        try:
            with open(file) as f:
                return f.readlines()
        except:
            return ["Could not read the contents of " + file]

    # print contents of all the files
    return '\n'.join([format_contents(x, robust_readlines(x)) for x in files])


def format_contents(filename: str, contents: List[str]) -> str:
    return '\n' + filename + "\n########\n{0}########".format('\n'.join(contents))


def _set_test_status(fastr_test_info: Dict[str, PkgTestStatus]) -> None:
    def _failed_outputs(outputs: Dict[str, TestFileStatus]) -> Union[bool, List[str]]:
        '''
        return False iff outputs has no .fail files
        '''
        for _, testfile_status in outputs.items():
            if testfile_status.status == "FAILED":
                return [testfile_status.abspath]
        return False

    gnur_test_info: Dict[str, PkgTestStatus] = dict()
    for pkg, _ in fastr_test_info.items():
        _get_test_outputs(RVM_GNUR, pkg, gnur_test_info)

    # gnur is definitive so drive off that
    for pkg in list(gnur_test_info.keys()):
        logging.info('BEGIN checking ' + pkg)
        gnur_test_status = gnur_test_info[pkg]
        fastr_test_status = fastr_test_info[pkg]
        gnur_outputs = gnur_test_status.testfile_outputs
        fastr_outputs = fastr_test_status.testfile_outputs

        gnur_failed_outputs = _failed_outputs(gnur_outputs)
        if gnur_failed_outputs:
            # What this likely means is that some native package is not
            # installed on the system so GNUR can't run the tests.
            # Ideally this never happens.
            failed_outputs = [s + '.fail' for s in gnur_failed_outputs]
            logging.info(f"{pkg}: GnuR test had .fail outputs: {str(failed_outputs)}\n{get_contents(failed_outputs)}")

        fastr_failed_outputs = _failed_outputs(fastr_outputs)
        if fastr_failed_outputs:
            # In addition to the similar comment for GNU R, this can happen
            # if, say, the JVM crashes (possible with native code packages)
            logging.info(f"{pkg}: FastR test had .fail outputs: {str(fastr_failed_outputs)}\n{get_contents(fastr_failed_outputs)}")
            fastr_test_status.set_status_code("FAILED")

        # Now for each successful GNU R output we compare content (assuming FastR didn't fail)
        for gnur_test_output_relpath, gnur_testfile_status in gnur_outputs.items():

            # If FastR does not have a corresponding test output file ...
            if not gnur_test_output_relpath in fastr_outputs:
                # FastR crashed on this test
                fastr_test_status.set_status_code("FAILED")
                logging.info(f"{pkg}: FastR is missing output file: {gnur_test_output_relpath}")
                continue

            # Get corresponding FastR test output file
            fastr_testfile_status = fastr_outputs[gnur_test_output_relpath]

            # Can't compare if either GNUR or FastR failed
            if gnur_testfile_status.status == "FAILED":
                fastr_testfile_status.set_status_code("INDETERMINATE")
                continue

            # If the test output file's status is "FAILED" at this point, we know that there was a ".fail" output
            # file. So, don't do fuzzy-compare.
            if fastr_testfile_status.status == "FAILED":
                # It may only be fuzzy-compare because if we would have a test framework, the status would not be
                # "FAILED" since a test framework cannot produce ".fail" output files.
                continue

            with open(gnur_testfile_status.abspath) as f:
                gnur_content = f.readlines()
            with open(fastr_testfile_status.abspath) as f:
                fastr_content = f.readlines()

            # parse custom filters from file
            filters = select_filters_for_package(os.path.join(_packages_test_project_dir(), "test.output.filter"), pkg)

            # first, parse file and see if a known test framework has been used
            detected, ok, skipped, failed = handle_output_file(fastr_testfile_status.abspath, fastr_content)
            log_files = False
            if detected:
                # If a test framework is used, also parse the summary generated by GnuR to compare numbers.
                detected, gnur_ok, gnur_skipped, gnur_failed = handle_output_file(gnur_testfile_status.abspath,
                                                                                  gnur_content)
                fastr_invalid_numbers = ok is None or skipped is None and failed is None
                gnur_invalid_numbers = gnur_ok is None or gnur_skipped is None and gnur_failed is None
                total_fastr = ok + skipped + failed if not fastr_invalid_numbers else -1
                total_gnur = gnur_ok + gnur_skipped + gnur_failed if not gnur_invalid_numbers else -1

                if not fastr_invalid_numbers and total_fastr != total_gnur:
                    logging.info(
                        "Different number of tests executed. FastR = {} vs. GnuR = {}\n".format(total_fastr, total_gnur))
                    log_files = True
                elif fastr_invalid_numbers:
                    logging.info("FastR reported invalid numbers of executed tests.")
                    log_files = True

                if fastr_invalid_numbers or total_fastr > total_gnur:
                    # If FastR's numbers are invalid or GnuR ran fewer tests than FastR, we cannot trust the FastR numbers
                    fastr_testfile_status.set_report(0, gnur_skipped, gnur_ok + gnur_failed)
                    fastr_test_status.set_status_code("FAILED")
                    fastr_testfile_status.status = "FAILED"
                elif total_fastr < total_gnur:
                    # If FastR ran fewer tests than GnuR, we complement the missing ones as failing
                    fastr_testfile_status.set_report(ok, skipped, failed + (total_gnur - total_fastr))
                    fastr_test_status.set_status_code("FAILED")
                    fastr_testfile_status.status = "FAILED"
                else:
                    # The total numbers are equal, so we are fine.
                    fastr_testfile_status.status = "OK"
                    fastr_testfile_status.set_report(ok, skipped, failed)
            else:
                result, n_tests_passed, n_tests_failed = fuzzy_compare(gnur_content, fastr_content,
                                                                       gnur_testfile_status.abspath,
                                                                       fastr_testfile_status.abspath,
                                                                       custom_filters=filters,
                                                                       dump_preprocessed=get_opts().dump_preprocessed)
                if result == -1:
                    logging.info("{0}: content malformed: {1}".format(pkg, gnur_test_output_relpath))
                    fastr_test_status.set_status_code("INDETERMINATE")
                    # we don't know how many tests are in there, so consider the whole file to be one big skipped test
                    fastr_testfile_status.set_report(0, 1, 0)
                    log_files = True
                elif result != 0:
                    fastr_test_status.set_status_code("FAILED")
                    fastr_testfile_status.status = "FAILED"
                    fastr_testfile_status.set_report(n_tests_passed, 0, n_tests_failed)
                    logging.info("{0}: FastR output mismatch: {1}".format(pkg, gnur_test_output_relpath))
                    logging.info("    output mismatch file: {0}".format(join(_pkg_testdir(RVM_FASTR, pkg), gnur_test_output_relpath)))
                    logging.info("    output mismatch file: {0}".format(join(_pkg_testdir(RVM_GNUR, pkg), gnur_test_output_relpath)))
                    log_files = True
                else:
                    fastr_testfile_status.status = "OK"
                    fastr_testfile_status.set_report(n_tests_passed, 0, n_tests_failed)

            # print out the full output files if the test failed
            if log_files:
                logging.info(format_contents(gnur_testfile_status.abspath, gnur_content) + format_contents(
                    fastr_testfile_status.abspath, fastr_content))

        # we started out as UNKNOWN
        if not (fastr_test_status.status == "INDETERMINATE" or fastr_test_status.status == "FAILED"):
            fastr_test_status.set_status_code("OK")

        # write out a file with the test status for each output (that exists)
        with open(join(_pkg_testdir(RVM_FASTR, pkg), 'testfile_status'), 'w') as f:
            f.write('# <file path> <tests passed> <tests skipped> <tests failed>\n')
            for fastr_relpath, fastr_testfile_status in fastr_outputs.items():
                logging.info("generating testfile_status for {0}".format(fastr_relpath))
                relpath = fastr_relpath
                test_output_file = join(_pkg_testdir(RVM_FASTR, pkg), relpath)

                if os.path.exists(test_output_file):
                    ok, skipped, failed = fastr_testfile_status.get_report()
                    f.write("{0} {1} {2} {3} {4}\n".format(relpath, ok, skipped, failed, fastr_testfile_status.test_time))
                elif fastr_testfile_status.status == "FAILED":
                    # In case of status == "FAILED", also try suffix ".fail" because we just do not know if the test
                    # failed and finished or just never finished.
                    relpath_fail = fastr_relpath + ".fail"
                    test_output_file_fail = join(_pkg_testdir(RVM_FASTR, pkg), relpath_fail)
                    if os.path.exists(test_output_file_fail):
                        ok, skipped, failed = fastr_testfile_status.get_report()
                        f.write("{0} {1} {2} {3} {4}\n".format(relpath_fail, ok, skipped, failed, fastr_testfile_status.test_time))
                    else:
                        logging.info("File {0} or {1} does not exist".format(test_output_file, test_output_file_fail))
                else:
                    logging.info("File {0} does not exist".format(test_output_file))

        with open(join(_pkg_testdir(RVM_FASTR, pkg), 'test_time'), 'w') as f:
            f.write(str(fastr_test_status.test_time))

        logging.info('END checking ' + pkg)


def handle_output_file(test_output_file: str, test_output_file_lines: List[str])\
        -> Tuple[bool, Optional[int], Optional[int], Optional[int]]:
    """
    R package tests are usually distributed over several files. Each file can be interpreted as a test suite.
    This function parses the output file of all test suites and tries to detect if it used the testthat or RUnit.
    In this case, it parses the summary (number of passed, skipped, failed tests) of these test frameworks.
    If none of the frameworks is used, it performs an output diff and tries to determine, how many statements
    produces different output, i.e., every statement is considered to be a unit test.
    Returns a 4-tuple: (<framework detected>, <#passed>, <#skipped>, <#failed>).
    """
    logging.debug("Detecting output type of {!s}".format(test_output_file))
    detected = False
    ok, skipped, failed = None, None, None
    try:
        if _is_testthat_result(test_output_file):
            # if "testthat results" in test_output_file_contents[i]:
            logging.info("Detected testthat summary in {!s}".format(test_output_file))
            detected = True
            ok, skipped, failed = _parse_testthat_result(test_output_file_lines)
        elif _is_runit_result(test_output_file_lines):
            logging.info("Detected RUNIT test protocol in {!s}".format(test_output_file))
            detected = True
            ok, skipped, failed = _parse_runit_result(test_output_file_lines)
    except TestFrameworkResultException as e:
        logging.info("Error parsing test framework summary: " + str(e))
    # if this test did not use one of the known test frameworks, take the report from the fuzzy compare
    return (detected, ok, skipped, failed)


def _is_testthat_result(test_output_file: str) -> bool:
    return os.path.basename(test_output_file) == "testthat.Rout"


def _is_runit_result(lines: List[str]) -> bool:
    return any("RUNIT TEST PROTOCOL" in l for l in lines)


def _parse_testthat_result(lines: List[str]) -> Tuple[int, int, int]:
    '''
    Tries to parse results of testthat package of some known versions.
    Returns tuple of (ok_count, skipped_count, failed_count)
    '''
    if any((True for line in lines if 'testthat results' in line)):
        return _parse_old_testthat_result(lines)
    testthat_pattern = re.compile(r".*\[\s+FAIL\s+(?P<fail_count>\d+)\s+\|\s+WARN\s+(?P<warn_count>\d+)\s+\|\s+SKIP\s+(?P<skip_count>\d+)\s+\|\s+PASS\s+(?P<pass_count>\d+)\s+\]")
    for line in lines:
        match = testthat_pattern.match(line)
        if match:
            return (int(match.group("pass_count")), int(match.group("skip_count")), int(match.group("fail_count")))
    raise TestFrameworkResultException("Wrong format of testthat (version >= 3.0.1) results")


def _parse_old_testthat_result(lines: List[str]) -> Tuple[int, int, int]:
    '''
    Parses result of testthat version lower than 3.0.1.
    Returns tuple of (ok_count, skipped_count, failed_count)
    '''

    def _testthat_parse_part(part: str) -> int:
        '''
        parses a part like "OK: 2"
        '''
        parts = [x.strip() for x in part.split(":")]
        if len(parts) == 2:
            assert parts[0] == "OK" or parts[0] == "SKIPPED" or parts[0] == "WARNINGS" or parts[0] == "FAILED"
            return int(parts[1])
        raise Exception("could not parse testthat status part {0}".format(part))

    # find index of line which contains 'testthat results'
    try:
        i = next(iter([x for x in enumerate(lines) if 'testthat results' in x[1]]))[0]
        if i + 1 < len(lines):
            if lines[i + 1].startswith("OK"):
                # old-style testthat report: "OK: 123 SKIPPED: 456 FAILED: 789"
                result_line = lines[i + 1]
                idx_ok = 0
                idx_skipped = result_line.find("SKIPPED")
                idx_failed = result_line.find("FAILED")
                if idx_ok != -1 and idx_skipped != -1 and idx_failed != -1:
                    ok_part = result_line[idx_ok:idx_skipped]
                    skipped_part = result_line[idx_skipped:idx_failed]
                    failed_part = result_line[idx_failed:]
            elif lines[i + 1].startswith("["):
                # newer-style testthat report: "[ OK: 1 | SKIPPED: 2 | WARNINGS: 3 | FAILED: 4 ]"
                line = lines[i + 1].strip()[1:-1]
                ok_part, skipped_part, warnings_part, failed_part = line.split("|")
            return (
                _testthat_parse_part(ok_part), _testthat_parse_part(skipped_part),
                _testthat_parse_part(failed_part))
            raise TestFrameworkResultException("Could not parse testthat status line {0}".format(result_line))
        else:
            raise TestFrameworkResultException("Could not parse testthat summary at line {}".format(i + 1))
    except StopIteration:
        raise TestFrameworkResultException("Could not parse testthat summary: Line 'testthat results' not contained.")


def _parse_runit_result(lines: List[str]) -> Tuple[int, int, int]:
    '''
    RUNIT TEST PROTOCOL -- Thu Feb 08 10:54:42 2018
    ***********************************************
    Number of test functions: 20
    Number of errors: 0
    Number of failures: 0
    '''
    try:
        line_idx = next(iter([x for x in enumerate(lines) if 'RUNIT TEST PROTOCOL' in x[1]]))[0]
        tests_total = 0
        tests_failed = 0
        for i in range(line_idx, len(lines)):
            split_line = lines[i].split(":")
            if len(split_line) >= 2:
                if "Number of test functions" in split_line[0]:
                    tests_total = int(split_line[1])
                elif "Number of errors" in split_line[0] or "Number of failures" in split_line[0]:
                    tests_failed = tests_failed + int(split_line[1])
        return (tests_total - tests_failed, 0, tests_failed)
    except StopIteration:
        # That should really not happen since RUnit is detected by a line containing 'RUNIT TEST PROTOCOL'
        raise TestFrameworkResultException(
            "Could not parse testthat summary: Line 'RUNIT TEST PROTOCOL' not contained.")


def installpkgs(args: List[str], **kwargs):
    rargs = util.parse_arguments(args)
    return _fastr_installpkgs(rargs)


def pkgtest_check(args: List[str]) -> int:
    '''
    This function allows to do only the checking part on an existing test output
    (i.e. 'test.fastr' and 'test.gnur' directories).
    It will try to re-create
    :return:
    '''
    parser = argparse.ArgumentParser(prog="pkgtest", description='FastR package testing.')
    parser.add_argument('--fastr-home', metavar='FASTR_HOME', dest="fastr_home", type=str, default=None,
                        required=True, help='The FastR standalone repo home directory (required).')
    parser.add_argument('-v', '--verbose', dest="verbose", action="store_const", const=1, default=0,
                        help='Do verbose logging.')
    parser.add_argument('-V', '--very-verbose', dest="verbose", action="store_const", const=2,
                        help='Do verbose logging.')
    parser.add_argument('--dump-preprocessed', dest="dump_preprocessed", action="store_true",
                        help='Dump processed output files where replacement filters have been applied.')
    parser.add_argument('pkg_name', metavar="PKG_NAME",
                        help='Package name for checking.')

    from . import util
    _opts = parser.parse_args(args=args, namespace=util.get_opts())

    log_format = '%(message)s'
    if _opts.verbose == 1:
        log_level = logging.DEBUG
    elif _opts.verbose == 2:
        log_level = VERY_VERBOSE
    else:
        log_level = logging.INFO
    logging.basicConfig(level=log_level, format=log_format)

    # also log to console
    console_handler = logging.StreamHandler(stream=sys.stdout)
    console_handler.setLevel(log_level)
    console_handler.setFormatter(logging.Formatter(log_format))
    logging.getLogger("").addHandler(console_handler)

    # if not :
    #     print("Missing required argument 'pkg_name'")
    #     return 1

    pkg_name = _opts.pkg_name
    fastr_testdir = _pkg_testdir(RVM_FASTR, pkg_name)
    if not os.path.isdir(fastr_testdir):
        print(f"test directory '{fastr_testdir}' does not exist")
        return 1

    gnur_testdir = _pkg_testdir(RVM_GNUR, pkg_name)
    if not os.path.isdir(gnur_testdir):
        print(f"test directory '{gnur_testdir}' does not exist")
        return 1

    fastr_test_info: Dict[str, PkgTestStatus] = dict()
    _get_test_outputs(RVM_FASTR, pkg_name, fastr_test_info)
    _set_test_status(fastr_test_info)
    return 0


def pkgtest_cmp(args: List[str]) -> Tuple[int, int, int]:
    gnur_filename = args[0]
    fastr_filename = args[1]
    if len(args) >= 4:
        test_output_filters = args[2]
        pkg_name = args[3]
    else:
        test_output_filters = None
        pkg_name = None
    dump_preprocessed = args[4] if len(args) >= 5 else False

    filters = select_filters_for_package(args[2], pkg_name) if len(args) >= 3 else ()

    with open(gnur_filename) as f:
        gnur_content = f.readlines()
    with open(fastr_filename) as f:
        fastr_content = f.readlines()
    from .fuzzy_compare import fuzzy_compare
    return fuzzy_compare(gnur_content, fastr_content, gnur_filename, fastr_filename, filters, dump_preprocessed)


def find_top100(args: List[str]) -> None:
    find_top(args + ["100"])


def find_top(args: List[str]) -> None:
    rargs = util.parse_arguments(['--use-installed-pkgs', '--find-top'] + args)
    n = args[-1]
    libinstall = join(get_fastr_repo_dir(), "top%s.tmp" % n)
    if not os.path.exists(libinstall):
        os.mkdir(libinstall)
    os.environ['R_LIBS_USER'] = libinstall
    _fastr_installpkgs(rargs)


def pkgcache(args: List[str]) -> int:
    '''
    Explicitly install and cache packages without running tests.

    Options:
        --cache-dir DIR                     Use package cache in directory DIR (will be created if not existing).
                                            Can be set via FASTR_PKGS_CACHE_OPT env var.
        --library [fastr=DIR][[,]gnur=DIR]  The library folders to install to. If you don't want to create
                                            any new temporary library, point the library to the existing
                                            library dirs, e.g. $FASTR_HOME/library.
                                            Defaults to "lib.install.packages".
        --vm [fastr|gnur]                   Whether to install the packages on fastr or on gnur.
                                            Defaults to both.
        --sync                              Synchronize access to the package cache.
                                            Can be set via FASTR_PKGS_CACHE_OPT env var.
        --print-api-checksum                Compute and print the API checksum for the specified VMs and exit.
        --install-opts                      R specific install options

    Return codes:
        0: success
        1: fail
    '''
    if '-h' in args or '--help' in args:
        print('==============================================')
        print('Common arguments for both r-pkgtest and r-pkgcache:')
        print('==============================================')
        common_arg_parser = get_common_arg_parser()
        common_arg_parser.prog = "mx pkgcache"
        common_arg_parser.print_help()
        print('\n==================================')
        print(f'Additional help for r-pkgcache:')
        print('==================================')
        print(pkgcache.__doc__)
        return 0
    unknown_args = parse_arguments(args, r_version_check=False)

    parser = argparse.ArgumentParser(prog="mx r-pkgcache")
    parser.add_argument('--vm', help='fastr|gnur', default=['fastr', 'gnur'])
    parser.add_argument('--print-api-checksum', action="store_true", dest="print_api_checksum",
                        help='Compute and print the API checksum for the specified VMs.')
    parser.add_argument('--cache-dir', metavar='DIR', dest="cache_dir", type=str, default=None,
                        help='The package cache directory. Mutually exclusive with FASTR_PKGS_CACHE_OPT env var.')
    parser.add_argument('--library', metavar='SPEC', type=str, default="",
                        help='The library folders to install to (must be specified for each used VM in form "<vm_name>=<dir>").')
    parser.add_argument('--sync', action="store_true", help='Synchronize access to the package cache. Mutually exclusive with FASTR_PKGS_CACHE_OPT env var')
    parser.add_argument('--install-opts', metavar="INSTALL_OPTS", dest="install_opts", help='R install options', default=None)

    from . import util
    _opts = parser.parse_args(args=unknown_args, namespace=util.get_opts())

    if _opts.print_api_checksum:
        if 'fastr' in _opts.vm:
            print("fastr: " + computeApiChecksum(get_fastr_include_path()))
        if 'gnur' in _opts.vm:
            print("gnur: " + computeApiChecksum(get_gnur_include_path()))
        return 0

    # now do the version check
    util.check_r_versions()

    install_args = []

    if "FASTR_PKGS_CACHE_OPT" in os.environ:
        if _opts.sync or _opts.cache_dir:
            logging.warning(f"Both 'FASTR_PKGS_CACHE_OPT' env var and --sync or --cache-dir options are set, env var precedes options")
        logging.info("Taking package cache settins from 'FASTR_PKGS_CACHE_OPT' env var")
        install_args += ["--cache-pkgs", os.environ["FASTR_PKGS_CACHE_OPT"]]
    else:
        pkgcache_options = ["dir={}".format(_opts.cache_dir), "ignore=base"]
        if _opts.sync:
            pkgcache_options += ["sync=TRUE"]
        install_args += ["--cache-pkgs", ",".join(pkgcache_options)]

    if _opts.install_opts:
        install_args += ["--install-opts", _opts.install_opts]
    if _opts.filelist:
        install_args += ["--pkg-filelist", _opts.filelist]
    if _opts.pattern:
        install_args += ["--pkg-pattern", _opts.pattern]

    # also propagate verbosity flag
    verbosity_level = get_opts().verbose
    if verbosity_level == 1:
        install_args += ["--verbose"]
    elif verbosity_level > 1:
        install_args += ["--very-verbose"]

    # get default CRAN mirror from our FastR home
    default_cran_mirror_url = "CRAN=" + get_default_cran_mirror()

    # We intercept '--repos SNAPSHOT' since in GraalVM mode, we do not necessarily have a 'etc/DEFAULT_CRAN_MIRROR' for
    # GnuR in an accessible location.
    if _opts.repos == 'SNAPSHOT':
        logging.info("Overwriting '--repos SNAPSHOT' with '--repos %s'" % default_cran_mirror_url)
        install_args += ["--repos", default_cran_mirror_url]
    elif _opts.repos == 'FASTR':
        logging.info("Overwriting '--repos FASTR' with '--repos FASTR,%s'" % default_cran_mirror_url)
        install_args += ["--repos", "FASTR," + default_cran_mirror_url]
    elif _opts.repos:
        install_args += ["--repos", _opts.repos]
    else:
        logging.info("No '--repos' specified, using default CRAN mirror: " + default_cran_mirror_url)
        install_args += ["--repos", default_cran_mirror_url]

    library_spec = {}
    if _opts.library:
        for part in _opts.library.split(","):
            vm, lib = part.split("=")
            library_spec[vm] = lib
    print(repr(library_spec))

    gnur_rc = 0
    fastr_rc = 0
    if 'fastr' in _opts.vm:
        if 'fastr' in library_spec:
            fastr_libinstall = ensure_dir(library_spec['fastr'])
            fastr_install_tmp = _create_tmpdir(RVM_FASTR)
        else:
            fastr_libinstall, fastr_install_tmp = _create_libinstall(RVM_FASTR, False)

        env = os.environ.copy()
        env["TMPDIR"] = fastr_install_tmp
        env['R_LIBS_USER'] = fastr_libinstall
        env['FASTR_OPTION_PrintErrorStacktracesToFile'] = 'false'
        env['FASTR_OPTION_PrintErrorStacktraces'] = 'true'

        # transfer required FastR functions to GnuR
        commit_fastr_builtins()

        fastr_args = list(install_args)

        # If '--cache-pkgs' is set, then also set the native API version value
        fastr_args = _set_pkg_cache_api_version(fastr_args, get_fastr_include_path())

        log_step('BEGIN', 'install/cache', 'FastR')
        # Currently installpkgs does not set a return code (in install.packages.R)
        fastr_rc = _fastr_installpkgs(fastr_args, nonZeroIsFatal=False, env=env)
        log_step('END', 'install/cache', 'FastR')

        shutil.rmtree(fastr_install_tmp, ignore_errors=True)

    if 'gnur' in _opts.vm:
        if 'gnur' in library_spec:
            gnur_libinstall = ensure_dir(library_spec['gnur'])
            gnur_install_tmp = _create_tmpdir(RVM_GNUR)
        else:
            gnur_libinstall, gnur_install_tmp = _create_libinstall(RVM_GNUR, False)

        env = os.environ.copy()
        env["TMPDIR"] = gnur_install_tmp
        env['R_LIBS_USER'] = gnur_libinstall
        env["TZDIR"] = "/usr/share/zoneinfo/"

        gnur_args = list(install_args)

        # If '--cache-pkgs' is set, then also set the native API version value
        gnur_args = _set_pkg_cache_api_version(gnur_args, get_gnur_include_path())

        log_step('BEGIN', 'install/cache', 'GnuR')
        gnur_rc = _gnur_installpkgs(gnur_args, nonZeroIsFatal=False, env=env)
        log_step('END', 'install/cache', 'GnuR')

    return max(fastr_rc, gnur_rc)


class TestFrameworkResultException(BaseException):
    pass
