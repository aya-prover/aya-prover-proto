// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import kala.collection.Map;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.tuple.Tuple3;
import kala.tuple.Unit;
import org.aya.api.core.CoreTerm;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Bind;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Expr;
import org.aya.core.ops.TermToPat;
import org.aya.core.pat.Pat;
import org.aya.core.sort.LevelSubst;
import org.aya.core.sort.Sort;
import org.aya.core.visitor.*;
import org.aya.distill.CoreDistiller;
import org.aya.generic.ParamLike;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.LittleTyper;
import org.aya.tyck.unify.level.LevelEqnSet;
import org.aya.util.Constants;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * A well-typed and terminating term.
 *
 * @author ice1000
 */
public sealed interface Term extends CoreTerm permits CallTerm, ElimTerm, ErrorTerm, FormTerm, IntroTerm, RefTerm, RefTerm.Field {
  <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

  default <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    visitor.traceEntrance(this, p);
    var ret = doAccept(visitor, p);
    visitor.traceExit(ret);
    return ret;
  }

  @Override default @Nullable Pat toPat(boolean explicit) {
    return TermToPat.toPat(this, explicit);
  }

  default @NotNull Term subst(@NotNull Var var, @NotNull Term term) {
    return subst(new Substituter.TermSubst(var, term));
  }

  default @NotNull Term subst(@NotNull Substituter.TermSubst subst) {
    return subst(subst, LevelSubst.EMPTY);
  }

  default @NotNull Term subst(@NotNull Map<Var, Term> subst) {
    return accept(new Substituter(subst, LevelSubst.EMPTY), Unit.unit());
  }

  default @NotNull Term subst(@NotNull Substituter.TermSubst subst, @NotNull LevelSubst levelSubst) {
    return accept(new Substituter(subst, levelSubst), Unit.unit());
  }

  default @NotNull Term zonk(@NotNull ExprTycker tycker, @Nullable SourcePos pos) {
    return new Zonker(tycker).zonk(this, pos);
  }

  @Override default int findUsages(@NotNull Var var) {
    var counter = new VarConsumer.UsageCounter(var);
    accept(counter, Unit.unit());
    return counter.usageCount();
  }

  @Override default @NotNull Buffer<LocalVar> scopeCheck(@NotNull ImmutableSeq<LocalVar> allowed) {
    var checker = new VarConsumer.ScopeChecker(allowed);
    accept(checker, Unit.unit());
    assert checker.isCleared() : "The scope checker is not properly cleared up";
    return checker.invalidVars;
  }

  @Override default @NotNull Term normalize(@NotNull NormalizeMode mode) {
    return accept(Normalizer.INSTANCE, mode);
  }

  default @NotNull Term freezeHoles(@Nullable LevelEqnSet eqnSet) {
    return accept(new TermFixpoint<>() {
      @Override public @NotNull Sort visitSort(@NotNull Sort sort, Unit unit) {
        return eqnSet != null ? eqnSet.applyTo(sort) : sort;
      }
    }, Unit.unit());
  }

  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return accept(new CoreDistiller(options), false);
  }
  default @NotNull Term computeType() {
    return accept(LittleTyper.INSTANCE, Unit.unit());
  }

  interface Visitor<P, R> {
    default void traceEntrance(@NotNull Term term, P p) {
    }
    default void traceExit(R r) {
    }
    R visitRef(@NotNull RefTerm term, P p);
    R visitLam(@NotNull IntroTerm.Lambda term, P p);
    R visitPi(@NotNull FormTerm.Pi term, P p);
    R visitSigma(@NotNull FormTerm.Sigma term, P p);
    R visitUniv(@NotNull FormTerm.Univ term, P p);
    R visitApp(@NotNull ElimTerm.App term, P p);
    R visitFnCall(CallTerm.@NotNull Fn fnCall, P p);
    R visitDataCall(CallTerm.@NotNull Data dataCall, P p);
    R visitConCall(CallTerm.@NotNull Con conCall, P p);
    R visitStructCall(CallTerm.@NotNull Struct structCall, P p);
    R visitPrimCall(@NotNull CallTerm.Prim prim, P p);
    R visitTup(@NotNull IntroTerm.Tuple term, P p);
    R visitNew(@NotNull IntroTerm.New newTerm, P p);
    R visitProj(@NotNull ElimTerm.Proj term, P p);
    R visitAccess(@NotNull CallTerm.Access term, P p);
    R visitHole(@NotNull CallTerm.Hole term, P p);
    R visitFieldRef(@NotNull RefTerm.Field term, P p);
    R visitError(@NotNull ErrorTerm term, P p);
  }

  /**
   * @author re-xyr
   */
  record Param(
    @NotNull LocalVar ref,
    @NotNull Term type,
    boolean explicit
  ) implements Bind, ParamLike<Term> {
    public static @NotNull ImmutableSeq<@NotNull Param> fromBuffer(Buffer<Tuple3<LocalVar, Boolean, Term>> buf) {
      return buf.view().map(tup -> new Param(tup._1, tup._3, tup._2)).toImmutableSeq();
    }

    @Contract(" -> new") public @NotNull Param implicitify() {
      return new Param(ref, type, false);
    }

    @Contract(" -> new") public @NotNull Param rename() {
      return new Param(new LocalVar(ref.name(), ref.definition()), type, explicit);
    }

    @Override @Contract(" -> new") public @NotNull Arg<@NotNull Term> toArg() {
      return new Arg<>(toTerm(), explicit);
    }

    @Contract(" -> new") public @NotNull RefTerm toTerm() {
      return new RefTerm(ref, type);
    }

    public @NotNull Param subst(@NotNull Var var, @NotNull Term term) {
      return subst(new Substituter.TermSubst(var, term));
    }

    public @NotNull Param subst(@NotNull Substituter.TermSubst subst) {
      return subst(subst, LevelSubst.EMPTY);
    }

    public static @NotNull ImmutableSeq<Param> subst(
      @NotNull ImmutableSeq<@NotNull Param> params,
      @NotNull Substituter.TermSubst subst, @NotNull LevelSubst levelSubst
    ) {
      return params.map(param -> param.subst(subst, levelSubst));
    }

    public static @NotNull ImmutableSeq<Param>
    subst(@NotNull ImmutableSeq<@NotNull Param> params, @NotNull LevelSubst levelSubst) {
      return params.map(param -> param.subst(Substituter.TermSubst.EMPTY, levelSubst));
    }

    public @NotNull Param subst(@NotNull LevelSubst levelSubst) {
      return subst(Substituter.TermSubst.EMPTY, levelSubst);
    }

    public @NotNull Param subst(@NotNull Substituter.TermSubst subst, @NotNull LevelSubst levelSubst) {
      return new Param(ref, type.subst(subst, levelSubst), explicit);
    }

    public static @NotNull Param mock(@NotNull Term hole, Expr.@NotNull Param param) {
      return new Param(new LocalVar(param.ref().name() + Constants.GENERATED_POSTFIX), hole, param.explicit());
    }

    @TestOnly @Contract(pure = true)
    public static boolean checkSubst(@NotNull SeqLike<@NotNull Param> params, @NotNull SeqLike<Arg<Term>> args) {
      var obj = new Object() {
        boolean ok = true;
      };
      params.forEachIndexed((i, param) -> obj.ok = obj.ok && param.explicit() == args.get(i).explicit());
      return obj.ok;
    }
  }

}
