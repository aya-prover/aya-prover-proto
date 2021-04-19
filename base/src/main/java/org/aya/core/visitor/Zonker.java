// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.ExprTycker;
import org.glavo.kala.control.Option;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Instantiates holes (assuming all holes are solved).
 * Called <code>StripVisitor</code> in Arend and <code>zonk</code> in
 * GHC and Andras' setoidtt prototype. Related discussion can be found on
 * <a href="https://twitter.com/mistakenly_made/status/1382356066688651266">Twitter</a>
 * and <a href="https://stackoverflow.com/a/31890743/7083401">StackOverflow</a>.
 *
 * @author ice1000
 */
public record Zonker(@NotNull Option<String> sourceFile, @NotNull Reporter reporter) implements TermFixpoint<Unit> {
  @Contract(pure = true) @Override public @NotNull Term visitHole(@NotNull CallTerm.Hole term, Unit unit) {
    var sol = term.ref().core();
    if (sol.body == null) {
      reporter.report(new UnsolvedMeta(sourceFile, sol.sourcePos));
      throw new ExprTycker.TyckInterruptedException();
    }
    return sol.body.accept(this, Unit.unit());
  }

  public static record UnsolvedMeta(@NotNull Option<String> sourceFile,
                                    @NotNull SourcePos sourcePos) implements Problem {
    @Override public @NotNull Doc describe() {
      return Doc.plain("Unsolved meta");
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }
}
