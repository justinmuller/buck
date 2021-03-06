/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.lua;

import static org.junit.Assert.assertThat;

import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.TargetGraph;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;

import org.hamcrest.Matchers;
import org.junit.Test;

public class LuaLibraryDescriptionTest {

  @Test
  public void unnamedSource() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    LuaLibrary library =
        (LuaLibrary) new LuaLibraryBuilder(BuildTargetFactory.newInstance("//some:rule"))
            .setSrcs(ImmutableSortedSet.of(new FakeSourcePath("some/foo.lua")))
            .build(resolver);
    assertThat(
        library.getLuaPackageComponents().getModules(),
        Matchers.equalTo(
            ImmutableSortedMap.<String, SourcePath>of(
                "some/foo.lua",
                new FakeSourcePath("some/foo.lua"))));
  }

  @Test
  public void namedSource() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    LuaLibrary library =
        (LuaLibrary) new LuaLibraryBuilder(BuildTargetFactory.newInstance("//some:rule"))
            .setSrcs(
                ImmutableSortedMap.of("bar.lua", new FakeSourcePath("foo.lua")))
            .build(resolver);
    assertThat(
        library.getLuaPackageComponents().getModules(),
        Matchers.equalTo(
            ImmutableSortedMap.<String, SourcePath>of(
                "some/bar.lua",
                new FakeSourcePath("foo.lua"))));
  }

  @Test
  public void baseModuleSource() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    LuaLibrary library =
        (LuaLibrary) new LuaLibraryBuilder(BuildTargetFactory.newInstance("//some:rule"))
            .setSrcs(ImmutableSortedSet.of(new FakeSourcePath("some/foo.lua")))
            .setBaseModule("blah")
            .build(resolver);
    assertThat(
        library.getLuaPackageComponents().getModules(),
        Matchers.equalTo(
            ImmutableSortedMap.<String, SourcePath>of(
                "blah/foo.lua",
                new FakeSourcePath("some/foo.lua"))));
  }

}
