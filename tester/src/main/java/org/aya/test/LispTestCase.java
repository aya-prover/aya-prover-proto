// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.test;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.core.sort.LevelEqn;
import org.aya.core.term.Term;
import org.aya.tyck.LocalCtx;
import org.aya.tyck.unify.TypedDefEq;
import org.aya.util.Ordering;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableMap;
import org.glavo.kala.control.Option;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;

public class LispTestCase {
  protected final MutableMap<String, @NotNull Var> vars = MutableMap.create();

  protected @NotNull TypedDefEq eq(MutableMap<LocalVar, Term> localCtx) {
    return new TypedDefEq(
      ThrowingReporter.INSTANCE, Ordering.Eq,
      new LocalCtx(localCtx, null), null, SourcePos.NONE,
      new LevelEqn.Set(Option.none(), Buffer.of(), ThrowingReporter.INSTANCE, Buffer.of())
    );
  }

  @AfterEach
  public void clearVars() {
    vars.clear();
  }
}
