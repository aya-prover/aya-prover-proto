// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import org.aya.api.concrete.ConcreteExpr;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.concrete.desugar.Desugarer;
import org.aya.concrete.pretty.ExprPrettier;
import org.aya.concrete.priority.BinOpParser;
import org.aya.concrete.priority.BinOpSet;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.visitor.ExprResolver;
import org.aya.generic.ParamLike;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Either;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * @author re-xyr
 */
@Debug.Renderer(text = "toDoc().debugRender()")
public sealed interface Expr extends ConcreteExpr {
  <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

  default <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    visitor.traceEntrance(this, p);
    var ret = doAccept(visitor, p);
    visitor.traceExit(ret, this, p);
    return ret;
  }

  default @NotNull Expr resolve(@NotNull Context context) {
    return accept(ExprResolver.INSTANCE, context);
  }

  default @NotNull Expr resolve(Reporter reporter) {
    return resolve(new EmptyContext(reporter));
  }

  default @NotNull Expr desugar(@NotNull BinOpSet opSet) {
    return accept(Desugarer.INSTANCE, opSet);
  }

  @Override default @NotNull Doc toDoc() {
    return accept(ExprPrettier.INSTANCE, false);
  }

  interface Visitor<P, R> {
    default void traceEntrance(@NotNull Expr expr, P p) {
    }
    default void traceExit(R r, @NotNull Expr expr, P p) {
    }
    R visitRef(@NotNull RefExpr expr, P p);
    R visitUnresolved(@NotNull UnresolvedExpr expr, P p);
    R visitLam(@NotNull LamExpr expr, P p);
    R visitPi(@NotNull PiExpr expr, P p);
    R visitSigma(@NotNull SigmaExpr expr, P p);
    R visitUniv(@NotNull UnivExpr expr, P p);
    R visitApp(@NotNull AppExpr expr, P p);
    R visitHole(@NotNull HoleExpr expr, P p);
    R visitTup(@NotNull TupExpr expr, P p);
    R visitProj(@NotNull ProjExpr expr, P p);
    R visitNew(@NotNull NewExpr expr, P p);
    R visitLitInt(@NotNull LitIntExpr expr, P p);
    R visitLitString(@NotNull LitStringExpr expr, P p);
    R visitBinOpSeq(@NotNull BinOpSeq binOpSeq, P p);
  }

  interface BaseVisitor<P, R> extends Visitor<P, R> {
    R catchUnhandled(@NotNull Expr expr, P p);
    @Override default R visitRef(@NotNull RefExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitUnresolved(@NotNull UnresolvedExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitLam(@NotNull LamExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitPi(@NotNull PiExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitSigma(@NotNull Expr.SigmaExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitUniv(@NotNull UnivExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitApp(@NotNull AppExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitHole(@NotNull HoleExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitTup(@NotNull TupExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitProj(@NotNull ProjExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitNew(@NotNull NewExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitLitInt(@NotNull LitIntExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitLitString(@NotNull LitStringExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitBinOpSeq(@NotNull BinOpSeq expr, P p) {
      return catchUnhandled(expr, p);
    }
  }

  /**
   * @author re-xyr
   */
  record UnresolvedExpr(
    @NotNull SourcePos sourcePos,
    @NotNull QualifiedID name
  ) implements Expr {
    public UnresolvedExpr(@NotNull SourcePos sourcePos, @NotNull String name) {
      this(sourcePos, new QualifiedID(sourcePos, name));
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitUnresolved(this, p);
    }
  }

  /**
   * @author ice1000
   */
  record HoleExpr(
    @NotNull SourcePos sourcePos,
    boolean explicit,
    @Nullable Expr filling
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitHole(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record AppExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr function,
    @NotNull ImmutableSeq<@NotNull Arg<Expr>> arguments
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitApp(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record PiExpr(
    @NotNull SourcePos sourcePos,
    boolean co,
    @NotNull Expr.Param param,
    @NotNull Expr last
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitPi(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record LamExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr.Param param,
    @NotNull Expr body
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLam(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record SigmaExpr(
    @NotNull SourcePos sourcePos,
    boolean co,
    @NotNull ImmutableSeq<@NotNull Param> params
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitSigma(this, p);
    }
  }

  /**
   * @author ice1000
   */
  record RefExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Var resolvedVar,
    @NotNull String resolvedFrom
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitRef(this, p);
    }
  }

  /**
   * @param hLevel specified hLevel, -1 means unspecified, -2 means infinity
   * @param uLevel specified uLevel, -1 means unspecified
   * @author re-xyr, ice1000
   */
  record UnivExpr(
    @NotNull SourcePos sourcePos,
    int uLevel,
    int hLevel
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitUniv(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record TupExpr(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<@NotNull Expr> items
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitTup(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record ProjExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr tup,
    @NotNull Either<Integer, String> ix
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitProj(this, p);
    }
  }

  record NewExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr struct,
    @NotNull ImmutableSeq<Field> fields
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitNew(this, p);
    }
  }

  record Field(
    @NotNull String name,
    @NotNull ImmutableSeq<Tuple2<SourcePos, LocalVar>> bindings,
    @NotNull Expr body
  ) {
  }

  /**
   * @author kiva
   */
  record LitIntExpr(
    @NotNull SourcePos sourcePos,
    int integer
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLitInt(this, p);
    }
  }

  record LitStringExpr(
    @NotNull SourcePos sourcePos,
    @NotNull String string
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLitString(this, p);
    }
  }

  /**
   * @author kiva
   */
  record BinOpSeq(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<BinOpParser.Elem> seq
  ) implements Expr {
    @Override
    public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitBinOpSeq(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record Param(
    @NotNull SourcePos sourcePos,
    @NotNull LocalVar ref,
    @Nullable Expr type,
    boolean explicit
  ) implements ParamLike<Expr> {
    public Param(@NotNull SourcePos sourcePos, @NotNull LocalVar var, boolean explicit) {
      this(sourcePos, var, null, explicit);
    }

    public @NotNull Expr.Param mapExpr(@NotNull Function<@NotNull Expr, @Nullable Expr> mapper) {
      return new Param(sourcePos, ref, type != null ? mapper.apply(type) : null, explicit);
    }
  }
}
