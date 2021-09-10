// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.ExprProblem;
import org.aya.api.error.Problem;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record UnifyError(
  @NotNull Expr expr,
  @NotNull Term expected,
  @NotNull Term actual
) implements ExprProblem, Problem {
  @Override public @NotNull Doc describe() {
    return Doc.vcat(
      Doc.english("Cannot check the expression of type"),
      Doc.nest(2, actual.toDoc(DistillerOptions.DEFAULT)),
      Doc.nest(2, Doc.parened(Doc.sep(Doc.plain("Normalized:"), actual.normalize(NormalizeMode.NF).toDoc(DistillerOptions.DEFAULT)))),
      Doc.english("against the type"),
      Doc.nest(2, expected.toDoc(DistillerOptions.DEFAULT)),
      Doc.nest(2, Doc.parened(Doc.sep(Doc.plain("Normalized:"), expected.normalize(NormalizeMode.NF).toDoc(DistillerOptions.DEFAULT))))
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
