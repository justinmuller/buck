/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.ocaml;

import static com.facebook.buck.ocaml.OcamlRuleBuilder.createOcamlLinkTarget;
import static com.facebook.buck.ocaml.OcamlRuleBuilder.createStaticLibraryBuildTarget;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.cli.BuckConfig;
import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.config.Config;
import com.facebook.buck.config.Configs;
import com.facebook.buck.cxx.CxxBuckConfig;
import com.facebook.buck.cxx.CxxDescriptionEnhancer;
import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.CxxPlatformUtils;
import com.facebook.buck.cxx.CxxSourceRuleFactory;
import com.facebook.buck.cxx.CxxSourceRuleFactoryHelper;
import com.facebook.buck.cxx.HeaderVisibility;
import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.DefaultCellPathResolver;
import com.facebook.buck.testutil.integration.BuckBuildLog;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TemporaryPaths;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.environment.Architecture;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OCamlIntegrationTest {

  @Rule
  public TemporaryPaths tmp = new TemporaryPaths();

  @Before
  public void checkOcamlIsConfigured() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "ocaml", tmp);
    workspace.setUp();

    ProjectFilesystem filesystem = new ProjectFilesystem(tmp.getRoot());

    Config rawConfig = Configs.createDefaultConfig(filesystem.getRootPath());

    BuckConfig buckConfig = new BuckConfig(
        rawConfig,
        filesystem,
        Architecture.detect(),
        Platform.detect(),
        ImmutableMap.copyOf(System.getenv()),
        new DefaultCellPathResolver(filesystem.getRootPath(), rawConfig));

    OcamlBuckConfig ocamlBuckConfig = new OcamlBuckConfig(
        Platform.detect(),
        buckConfig);

    assumeTrue(ocamlBuckConfig.getOcamlCompiler().isPresent());
    assumeTrue(ocamlBuckConfig.getOcamlBytecodeCompiler().isPresent());
    assumeTrue(ocamlBuckConfig.getOcamlDepTool().isPresent());
    assumeTrue(ocamlBuckConfig.getYaccCompiler().isPresent());
    assumeTrue(ocamlBuckConfig.getLexCompiler().isPresent());
  }

  @Test
  public void testHelloOcamlBuild() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "ocaml", tmp);
    workspace.setUp();

    BuildTarget target = BuildTargetFactory.newInstance(
        workspace.getDestPath(),
        "//hello_ocaml:hello_ocaml");
    BuildTarget binary = createOcamlLinkTarget(target);
    BuildTarget lib = BuildTargetFactory.newInstance(
        workspace.getDestPath(),
        "//hello_ocaml:ocamllib");
    BuildTarget staticLib = createStaticLibraryBuildTarget(lib);
    ImmutableSet<BuildTarget> targets = ImmutableSet.of(target, binary, lib, staticLib);

    workspace.runBuckCommand("build", target.toString()).assertSuccess();

    BuckBuildLog buildLog = workspace.getBuildLog();

    assertTrue(buildLog.getAllTargets().containsAll(targets));

    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetBuiltLocally(staticLib.toString());

    workspace.resetBuildLogFile();

    // Check that running a build again results in no builds since everything is up to
    // date.
    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    assertEquals(ImmutableSet.of(binary, target), buildLog.getAllTargets());
    buildLog.assertTargetHadMatchingRuleKey(binary.toString());
    buildLog.assertTargetHadMatchingRuleKey(target.toString());

    workspace.resetBuildLogFile();

    // Update the source file.
    workspace.replaceFileContents("hello_ocaml/amodule.ml", "v2", "v3");

    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    assertTrue(buildLog.getAllTargets().containsAll(targets));

    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetHadMatchingRuleKey(staticLib.toString());

    workspace.resetBuildLogFile();

    // Update the source file.
    workspace.replaceFileContents("hello_ocaml/ocamllib/m1.ml", "print me", "print Me");

    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    assertTrue(buildLog.getAllTargets().containsAll(targets));

    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetBuiltLocally(staticLib.toString());

    workspace.resetBuildLogFile();

    // Update the source file.
    workspace.replaceFileContents("hello_ocaml/BUCK", "#INSERT_POINT", "'ocamllib/dummy.ml',");

    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    assertTrue(buildLog.getAllTargets().containsAll(targets));

    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetBuiltLocally(staticLib.toString());

    workspace.resetBuildLogFile();

    BuildTarget lib1 = BuildTargetFactory.newInstance(
        workspace.getDestPath(),
        "//hello_ocaml:ocamllib1");
    BuildTarget staticLib1 = createStaticLibraryBuildTarget(lib1);
    ImmutableSet<BuildTarget> targets1 = ImmutableSet.of(target, binary, lib1, staticLib1);
    // We rebuild if lib name changes
    workspace.replaceFileContents("hello_ocaml/BUCK", "name = 'ocamllib'", "name = 'ocamllib1'");
    workspace.replaceFileContents(
        "hello_ocaml/BUCK",
        ":ocamllib",
        ":ocamllib1");

    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    assertTrue(buildLog.getAllTargets().containsAll(targets1));

    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetBuiltLocally(staticLib1.toString());
  }

  @Test
  public void testLexAndYaccBuild() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this,
        "ocaml",
        tmp);
    workspace.setUp();

    BuildTarget target = BuildTargetFactory.newInstance(workspace.getDestPath(), "//calc:calc");
    BuildTarget binary = createOcamlLinkTarget(target);

    ImmutableSet<BuildTarget> targets = ImmutableSet.of(target, binary);

    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    BuckBuildLog buildLog = workspace.getBuildLog();
    assertEquals(
        targets,
        buildLog.getAllTargets());
    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetBuiltLocally(binary.toString());

    workspace.resetBuildLogFile();

    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    assertEquals(ImmutableSet.of(binary, target), buildLog.getAllTargets());
    buildLog.assertTargetHadMatchingRuleKey(binary.toString());
    buildLog.assertTargetHadMatchingRuleKey(target.toString());

    workspace.resetBuildLogFile();

    workspace.replaceFileContents("calc/lexer.mll", "The type token", "the type token");
    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    assertEquals(
        targets,
        buildLog.getAllTargets());
    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetBuiltLocally(binary.toString());

    workspace.resetBuildLogFile();

    workspace.replaceFileContents("calc/parser.mly", "the entry point", "The entry point");
    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    assertEquals(
        targets,
        buildLog.getAllTargets());
    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetBuiltLocally(binary.toString());
  }

  @Test
  public void testCInteropBuild() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this,
        "ocaml",
        tmp);
    workspace.setUp();

    BuildTarget target = BuildTargetFactory.newInstance(workspace.getDestPath(), "//ctest:ctest");
    BuildTarget binary = createOcamlLinkTarget(target);
    ImmutableSet<BuildTarget> targets = ImmutableSet.of(target, binary);

    workspace.runBuckCommand("build", target.toString()).assertSuccess();

    BuckBuildLog buildLog = workspace.getBuildLog();

    assertTrue(buildLog.getAllTargets().containsAll(targets));

    buildLog.assertTargetBuiltLocally(target.toString());

    workspace.resetBuildLogFile();
    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    assertEquals(ImmutableSet.of(binary, target), buildLog.getAllTargets());
    buildLog.assertTargetHadMatchingRuleKey(binary.toString());
    buildLog.assertTargetHadMatchingRuleKey(target.toString());

    workspace.resetBuildLogFile();
    workspace.replaceFileContents("ctest/ctest.c", "NATIVE PLUS", "Native Plus");
    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    assertTrue(buildLog.getAllTargets().containsAll(targets));
    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetBuiltLocally(binary.toString());

    workspace.resetBuildLogFile();
    workspace.replaceFileContents("ctest/BUCK", "#INSERTION_POINT", "compiler_flags=['-noassert']");
    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    assertTrue(buildLog.getAllTargets().containsAll(targets));
    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetBuiltLocally(binary.toString());

    workspace.resetBuildLogFile();
    workspace.replaceFileContents(
        "ctest/BUCK",
        "compiler_flags=['-noassert']",
        "compiler_flags=[]");
    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    assertTrue(buildLog.getAllTargets().containsAll(targets));
    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetBuiltLocally(binary.toString());

    workspace.resetBuildLogFile();
    workspace.replaceFileContents("ctest/BUCK", "compiler_flags=[]", "compiler_flags=[]");
    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    assertEquals(ImmutableSet.of(binary, target), buildLog.getAllTargets());
    buildLog.assertTargetHadMatchingRuleKey(binary.toString());
    buildLog.assertTargetHadMatchingRuleKey(target.toString());
  }

  @Test
  public void testSimpleBuildWithLib() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this,
        "ocaml",
        tmp);
    workspace.setUp();

    BuildTarget target = BuildTargetFactory.newInstance(workspace.getDestPath(), "//:plus");
    workspace.runBuckCommand("build", target.toString()).assertSuccess();
  }

  @Test
  public void testRootBuildTarget() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this,
        "ocaml",
        tmp);
    workspace.setUp();

    BuildTarget target = BuildTargetFactory.newInstance(workspace.getDestPath(), "//:main");
    workspace.runBuckCommand("build", target.toString()).assertSuccess();
  }

  @Test
  public void testPrebuiltLibraryBytecodeOnly() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this,
        "ocaml",
        tmp);
    workspace.setUp();

    BuildTarget target = BuildTargetFactory.newInstance(
        workspace.getDestPath(),
        "//ocaml_ext_bc:ocaml_ext");
    BuildTarget binary = createOcamlLinkTarget(target);
    BuildTarget bytecode = OcamlBuildRulesGenerator.addBytecodeFlavor(binary);
    BuildTarget libplus = BuildTargetFactory.newInstance(
        workspace.getDestPath(),
        "//ocaml_ext_bc:plus");
    ImmutableSet<BuildTarget> targets = ImmutableSet.of(target, bytecode, libplus);

    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    BuckBuildLog buildLog = workspace.getBuildLog();
    assertTrue(buildLog.getAllTargets().containsAll(targets));
    assertFalse(buildLog.getAllTargets().contains(binary));
    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetBuiltLocally(bytecode.toString());
  }

  @Test
  @Ignore("Redesign test so it does not depend on compiler/platform-specific binary artifacts.")
  public void testPrebuiltLibraryMac() throws IOException {
    if (Platform.detect() == Platform.MACOS) {
      ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
          this,
          "ocaml",
          tmp);
      workspace.setUp();

      BuildTarget target = BuildTargetFactory.newInstance(
          workspace.getDestPath(),
          "//ocaml_ext_mac:ocaml_ext");
      BuildTarget binary = createOcamlLinkTarget(target);
      BuildTarget bytecode = OcamlBuildRulesGenerator.addBytecodeFlavor(binary);
      BuildTarget libplus = BuildTargetFactory.newInstance(
          workspace.getDestPath(),
          "//ocaml_ext_mac:plus");
      ImmutableSet<BuildTarget> targets = ImmutableSet.of(target, binary, bytecode, libplus);

      workspace.runBuckCommand("build", target.toString()).assertSuccess();
      BuckBuildLog buildLog = workspace.getBuildLog();
      for (BuildTarget t : targets) {
        assertTrue(
            String.format("Expected %s to be built", t.toString()),
            buildLog.getAllTargets().contains(t));
      }
      buildLog.assertTargetBuiltLocally(target.toString());
      buildLog.assertTargetBuiltLocally(binary.toString());

      workspace.resetBuildLogFile();
      workspace.runBuckCommand("build", target.toString()).assertSuccess();
      for (BuildTarget t : targets) {
        assertTrue(
            String.format("Expected %s to be built", t.toString()),
            buildLog.getAllTargets().contains(t));
      }
      buildLog.assertTargetHadMatchingRuleKey(target.toString());
      buildLog.assertTargetHadMatchingRuleKey(binary.toString());

      workspace.resetBuildLogFile();
      workspace.replaceFileContents(
          "ocaml_ext_mac/BUCK",
          "libplus_lib",
          "libplus_lib1");
      workspace.runBuckCommand("build", target.toString()).assertSuccess();
      buildLog = workspace.getBuildLog();
      assertTrue(buildLog.getAllTargets().containsAll(targets));
      buildLog.assertTargetBuiltLocally(target.toString());
      buildLog.assertTargetBuiltLocally(binary.toString());
    }
  }

  @Test
  public void testCppLibraryDependency() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this,
        "ocaml",
        tmp);
    workspace.setUp();

    BuildTarget target = BuildTargetFactory.newInstance(workspace.getDestPath(), "//clib:clib");
    BuildTarget binary = createOcamlLinkTarget(target);
    BuildTarget libplus = BuildTargetFactory.newInstance(workspace.getDestPath(), "//clib:plus");
    BuildTarget libplusStatic = createStaticLibraryBuildTarget(libplus);
    BuildTarget cclib = BuildTargetFactory.newInstance(workspace.getDestPath(), "//clib:cc");

    CxxPlatform cxxPlatform = CxxPlatformUtils.build(
        new CxxBuckConfig(FakeBuckConfig.builder().build()));
    CxxSourceRuleFactory cxxSourceRuleFactory = CxxSourceRuleFactoryHelper.of(
        workspace.getDestPath(),
        cclib,
        cxxPlatform);
    BuildTarget cclibbin =
        CxxDescriptionEnhancer.createStaticLibraryBuildTarget(
            cclib,
            cxxPlatform.getFlavor(),
            CxxSourceRuleFactory.PicType.PDC);
    String sourceName = "cc/cc.cpp";
    BuildTarget ccObj = cxxSourceRuleFactory.createCompileBuildTarget(sourceName);
    BuildTarget headerSymlinkTreeTarget =
        CxxDescriptionEnhancer.createHeaderSymlinkTreeTarget(
            cclib,
            cxxPlatform.getFlavor(),
            HeaderVisibility.PRIVATE);
    BuildTarget exportedHeaderSymlinkTreeTarget =
        CxxDescriptionEnhancer.createHeaderSymlinkTreeTarget(
            cclib,
            cxxPlatform.getFlavor(),
            HeaderVisibility.PUBLIC);

    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    BuckBuildLog buildLog = workspace.getBuildLog();
    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetBuiltLocally(binary.toString());
    buildLog.assertTargetBuiltLocally(libplus.toString());
    buildLog.assertTargetBuiltLocally(libplusStatic.toString());
    buildLog.assertTargetBuiltLocally(cclibbin.toString());
    buildLog.assertTargetBuiltLocally(ccObj.toString());
    buildLog.assertTargetBuiltLocally(headerSymlinkTreeTarget.toString());
    buildLog.assertTargetBuiltLocally(exportedHeaderSymlinkTreeTarget.toString());

    workspace.resetBuildLogFile();
    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    buildLog.assertTargetHadMatchingRuleKey(binary.toString());
    buildLog.assertTargetHadMatchingRuleKey(target.toString());

    workspace.resetBuildLogFile();
    workspace.replaceFileContents("clib/cc/cc.cpp", "Hi there", "hi there");
    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetBuiltLocally(binary.toString());
    buildLog.assertTargetBuiltLocally(libplus.toString());
    buildLog.assertTargetBuiltLocally(libplusStatic.toString());
    buildLog.assertTargetBuiltLocally(cclibbin.toString());
    buildLog.assertTargetBuiltLocally(ccObj.toString());
  }

  @Test
  public void testConfigWarningsFlags() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this,
        "config_warnings_flags",
        tmp);
    workspace.setUp();

    BuildTarget target = BuildTargetFactory.newInstance(workspace.getDestPath(),
        "//:unused_var");
    BuildTarget binary = createOcamlLinkTarget(target);

    ImmutableSet<BuildTarget> targets = ImmutableSet.of(target, binary);

    workspace.runBuckCommand("build", target.toString()).assertFailure();
    BuckBuildLog buildLog = workspace.getBuildLog();
    assertTrue(buildLog.getAllTargets().containsAll(targets));
    buildLog.assertTargetCanceled(target.toString());
    buildLog.assertTargetCanceled(binary.toString());

    workspace.resetBuildLogFile();
    workspace.replaceFileContents(".buckconfig", "warnings_flags=+a", "");
    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetBuiltLocally(binary.toString());
  }

  @Test
  public void testConfigInteropIncludes() throws IOException, InterruptedException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this,
        "config_interop_includes",
        tmp);
    workspace.setUp();

    Path ocamlc = new ExecutableFinder(Platform.detect()).getExecutable(
        Paths.get("ocamlc"),
        ImmutableMap.copyOf(System.getenv()));

    ProcessExecutor.Result result = workspace.runCommand(ocamlc.toString(), "-where");
    assertEquals(0, result.getExitCode());
    String stdlibPath = result.getStdout().get();

    BuildTarget target = BuildTargetFactory.newInstance(workspace.getDestPath(),
        "//:test");
    BuildTarget binary = createOcamlLinkTarget(target);

    ImmutableSet<BuildTarget> targets = ImmutableSet.of(target, binary);

    // Points somewhere with no stdlib in it, so fails to find Pervasives
    workspace.runBuckCommand("build", target.toString()).assertFailure();
    BuckBuildLog buildLog = workspace.getBuildLog();
    assertTrue(buildLog.getAllTargets().containsAll(targets));
    buildLog.assertTargetCanceled(target.toString());
    buildLog.assertTargetCanceled(binary.toString());

    workspace.resetBuildLogFile();

    // Point to the real stdlib (from `ocamlc -where`)
    workspace.replaceFileContents(
        ".buckconfig",
        "interop.includes=lib",
        "interop.includes=" + stdlibPath);
    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetBuiltLocally(binary.toString());

    workspace.resetBuildLogFile();

    // Remove the config, should default to a valid place
    workspace.replaceFileContents(
        ".buckconfig",
        "interop.includes=" + stdlibPath,
        "");
    workspace.runBuckCommand("build", target.toString()).assertSuccess();
    buildLog = workspace.getBuildLog();
    buildLog.assertTargetBuiltLocally(target.toString());
    buildLog.assertTargetBuiltLocally(binary.toString());
  }

  @Test
  public void testGenruleDependency() throws IOException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "ocaml", tmp);
    workspace.setUp();

    BuildTarget binary = BuildTargetFactory.newInstance(
        workspace.getDestPath(),
        "//generated:binary");
    BuildTarget generated = BuildTargetFactory.newInstance(
        workspace.getDestPath(),
        "//generated:generated");
    ImmutableSet<BuildTarget> targets = ImmutableSet.of(binary, generated);

    // Build the binary.
    workspace.runBuckCommand("build", binary.toString()).assertSuccess();

    // Make sure the generated target is built as well.
    BuckBuildLog buildLog = workspace.getBuildLog();
    assertTrue(buildLog.getAllTargets().containsAll(targets));
    buildLog.assertTargetBuiltLocally(binary.toString());
  }
}
