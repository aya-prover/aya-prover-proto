// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.unify;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.Term;
import org.mzi.tyck.MetaContext;
import org.mzi.tyck.error.HoleAppWarn;
import org.mzi.util.Ordering;

/**
 * @author ice1000
 */
public class NaiveDefEq extends DefEq {
  public NaiveDefEq(@NotNull Ordering ord, @NotNull MetaContext metaContext) {
    super(ord, metaContext);
  }

  @Override
  public @NotNull Boolean visitHole(AppTerm.@NotNull HoleApp lhs, @NotNull Term preRhs, @Nullable Term type) {
    if (!lhs.argsBuf().isEmpty()) {
      report(new HoleAppWarn(lhs, expr));
      return false;
    }
    var solution = metaContext.solutions().getOption(lhs);
    if (solution.isDefined()) return compare(solution.get(), preRhs, type);
    if (preRhs instanceof AppTerm.HoleApp rhs) {
      if (!rhs.args().isEmpty()) {
        report(new HoleAppWarn(lhs, expr));
        return false;
      }
      if (lhs.var() == rhs.var()) return true;
    }
    // TODO[ice]: check for recursive solution
    metaContext.solutions().put(lhs, preRhs);
    return true;
  }
}
