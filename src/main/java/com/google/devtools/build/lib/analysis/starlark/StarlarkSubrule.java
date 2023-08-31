// Copyright 2023 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.analysis.starlark;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.docgen.annot.DocCategory;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.ArtifactRoot;
import com.google.devtools.build.lib.analysis.BazelRuleAnalysisThreadContext;
import com.google.devtools.build.lib.analysis.FilesToRunProvider;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.starlark.StarlarkActionFactory.StarlarkActionContext;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.starlarkbuildapi.StarlarkActionFactoryApi;
import com.google.devtools.build.lib.starlarkbuildapi.StarlarkSubruleApi;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkFunction;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.Tuple;

/** Represents a subrule which can be invoked in a Starlark rule's implementation function. */
public class StarlarkSubrule implements StarlarkCallable, StarlarkSubruleApi {
  // TODO(hvd) this class is a WIP, will be implemented over many commits

  private final StarlarkFunction implementation;

  public StarlarkSubrule(StarlarkFunction implementation) {
    this.implementation = implementation;
  }

  @Override
  public String getName() {
    return String.format("subrule(%s)", implementation.getName());
  }

  @Override
  public Object call(StarlarkThread thread, Tuple args, Dict<String, Object> kwargs)
      throws EvalException, InterruptedException {
    StarlarkRuleContext ruleContext =
        BazelRuleAnalysisThreadContext.fromOrFail(thread, getName())
            .getRuleContext()
            .getStarlarkRuleContext();
    ImmutableSet<? extends StarlarkSubruleApi> declaredSubrules = ruleContext.getSubrules();
    if (!declaredSubrules.contains(this)) {
      throw getUndeclaredSubruleError(ruleContext);
    }
    SubruleContext subruleContext = new SubruleContext(ruleContext);
    ImmutableList<Object> positionals =
        ImmutableList.builder().add(subruleContext).addAll(args).build();
    return Starlark.call(thread, implementation, positionals, kwargs);
  }

  private EvalException getUndeclaredSubruleError(StarlarkRuleContext starlarkRuleContext) {
    if (starlarkRuleContext.isForAspect()) {
      return Starlark.errorf(
          "aspect '%s' must declare '%s' in 'subrules'",
          starlarkRuleContext.getRuleContext().getMainAspect().getAspectClass().getName(),
          this.getName());
    } else {
      return Starlark.errorf(
          "rule '%s' must declare '%s' in 'subrules'",
          starlarkRuleContext.getRuleContext().getRule().getRuleClass(), this.getName());
    }
  }

  /**
   * The context object passed to the implementation function of a subrule.
   *
   * <p>This class exists to reduce the API surface visible to subrules and avoid leaking deprecated
   * or legacy APIs. It wraps the underlying rule's {@link StarlarkRuleContext} and either simply
   * delegates the operation to the latter, or has very similar behavior to it. Cases where behavior
   * differs is documented on the respective methods.
   */
  @StarlarkBuiltin(
      name = "subrule_ctx",
      category = DocCategory.BUILTIN,
      doc = "A context object passed to the implementation function of a subrule.")
  private static class SubruleContext implements StarlarkActionContext {
    private final StarlarkRuleContext ruleContext;
    private final StarlarkActionFactory actions;

    private SubruleContext(StarlarkRuleContext ruleContext) {
      this.ruleContext = ruleContext;
      this.actions = new StarlarkActionFactory(this);
    }

    @StarlarkMethod(
        name = "label",
        doc = "The label of the target currently being analyzed",
        structField = true)
    public Label getLabel() throws EvalException {
      return ruleContext.getLabel();
    }

    // This is identical to the StarlarkActionFactory used by StarlarkRuleContext, and subrule
    // specific behaviour is triggered by the methods inherited from StarlarkActionContext
    @StarlarkMethod(
        name = "actions",
        doc = "Contains methods for declaring output files and the actions that produce them",
        structField = true)
    public StarlarkActionFactoryApi actions() throws EvalException {
      return actions;
    }

    @Override
    public ArtifactRoot newFileRoot() {
      return ruleContext.getRuleContext().getBinDirectory();
    }

    @Override
    public void checkMutable(String attrName) throws EvalException {
      // TODO b/293304174 - implement this check
    }

    @Override
    public FilesToRunProvider getExecutableRunfiles(Artifact executable) {
      // TODO: b/293304174 - get from attributes
      return null;
    }

    @Override
    public boolean areRunfilesFromDeps(FilesToRunProvider executable) {
      // TODO: b/293304174 - get from attributes
      return false;
    }

    @Override
    public RuleContext getRuleContext() {
      return ruleContext.getRuleContext();
    }

    @Override
    public StarlarkSemantics getStarlarkSemantics() {
      return ruleContext.getStarlarkSemantics();
    }

    @Override
    public Object maybeOverrideExecGroup(Object execGroupUnchecked) throws EvalException {
      if (execGroupUnchecked != Starlark.NONE) {
        throw Starlark.errorf("'exec_group' may not be specified in subrules");
      }
      // TODO: b/293304174 - return the correct exec group
      return execGroupUnchecked;
    }

    @Override
    public Object maybeOverrideToolchain(Object toolchainUnchecked) throws EvalException {
      if (toolchainUnchecked != Starlark.UNBOUND) {
        throw Starlark.errorf("'toolchain' may not be specified in subrules");
      }
      // TODO: b/293304174 - return the correct toolchain
      return toolchainUnchecked;
    }
  }
}
