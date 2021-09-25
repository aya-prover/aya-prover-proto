// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.desugar;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.function.CheckedSupplier;
import kala.tuple.Unit;
import org.aya.api.error.Reporter;
import org.aya.api.ref.PreLevelVar;
import org.aya.api.util.Arg;
import org.aya.concrete.Expr;
import org.aya.concrete.desugar.error.LevelProblem;
import org.aya.concrete.visitor.StmtFixpoint;
import org.aya.generic.Level;
import org.aya.util.Constants;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000, kiva
 */
public record Desugarer(@NotNull Reporter reporter, @NotNull BinOpSet opSet) implements StmtFixpoint<Unit> {
  @Override public @NotNull Expr visitApp(@NotNull Expr.AppExpr expr, Unit unit) {
    if (expr.function() instanceof Expr.RawUnivExpr univ) return desugarUniv(expr, univ);
    return StmtFixpoint.super.visitApp(expr, unit);
  }

  @Override public @NotNull Expr visitRawUniv(@NotNull Expr.RawUnivExpr expr, Unit unit) {
    return desugarUniv(new Expr.AppExpr(expr.sourcePos(), expr, Option.none()), expr);
  }

  @Override public @NotNull Expr visitRawUnivArgs(@NotNull Expr.RawUnivArgsExpr expr, Unit unit) {
    return catching(expr, () -> new Expr.UnivArgsExpr(expr.sourcePos(), expr.univArgs().mapChecked(this::levelVar)));
  }

  @NotNull private Expr desugarUniv(Expr.@NotNull AppExpr expr, Expr.RawUnivExpr univ) {
    var pos = univ.sourcePos();
    var arg = expr.argument();
    if (arg.isEmpty()) return new Expr.UnivExpr(pos, new Level.Polymorphic(0));
    return catching(expr, () -> new Expr.UnivExpr(pos, levelVar(arg.get().term().expr())));
  }

  private @NotNull Expr catching(@NotNull Expr expr, @NotNull CheckedSupplier<@NotNull Expr, DesugarInterruption> f) {
    try {
      return f.getChecked();
    } catch (DesugarInterruption e) {
      return new Expr.ErrorExpr(expr.sourcePos(), expr);
    }
  }

  public static class DesugarInterruption extends Exception {
  }

  private @NotNull Level<PreLevelVar> levelVar(@NotNull Expr expr) throws DesugarInterruption {
    return switch (expr) {
      case Expr.LMaxExpr uMax -> new Level.Maximum(uMax.levels().mapChecked(this::levelVar));
      case Expr.LSucExpr uSuc -> levelVar(uSuc.expr()).lift(1);
      case Expr.LitIntExpr uLit -> new Level.Constant<>(uLit.integer());
      case Expr.RefExpr ref && ref.resolvedVar() instanceof PreLevelVar lv -> new Level.Reference<>(lv);
      case Expr.HoleExpr hole -> new Level.Reference<>(new PreLevelVar(Constants.randomName(hole), hole.sourcePos()));
      default -> {
        reporter.report(new LevelProblem.BadLevelExpr(expr));
        throw new DesugarInterruption();
      }
    };
  }

  @Override public @NotNull Expr visitBinOpSeq(@NotNull Expr.BinOpSeq binOpSeq, Unit unit) {
    var seq = binOpSeq.seq();
    assert seq.isNotEmpty() : binOpSeq.sourcePos().toString();
    return new BinOpParser(opSet, seq.view())
      .build(binOpSeq.sourcePos())
      .accept(this, Unit.unit());
  }
}
