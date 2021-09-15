// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.unify;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.stmt.Decl;
import org.aya.core.Meta;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.sort.LevelSubst;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.aya.core.visitor.Substituter;
import org.aya.core.visitor.Unfolder;
import org.aya.tyck.error.HoleProblem;
import org.aya.tyck.trace.Trace;
import org.aya.util.EtaConversion;
import org.aya.util.Ordering;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 * @apiNote Use {@link UntypedDefEq#compare(Term, Term)} instead of visiting directly!
 */
public record UntypedDefEq(
  @NotNull TypedDefEq defeq, @NotNull Ordering cmp
) implements Term.Visitor<@NotNull Term, @Nullable Term> {
  public @Nullable Term compare(@NotNull Term lhs, @NotNull Term rhs) {
    // lhs & rhs will both be WHNF if either is not a potentially reducible call
    if (TypedDefEq.isCall(lhs) || TypedDefEq.isCall(rhs)) {
      final var ty = lhs.accept(this, rhs);
      if (ty != null) return ty.normalize(NormalizeMode.WHNF);
    }
    lhs = lhs.normalize(NormalizeMode.WHNF);
    rhs = rhs.normalize(NormalizeMode.WHNF);
    final var x = lhs.accept(this, rhs);
    return x != null ? x.normalize(NormalizeMode.WHNF) : null;
  }

  @Nullable Term compareApprox(@NotNull Term preLhs, @NotNull Term preRhs) {
    //noinspection ConstantConditions
    return switch (preLhs) {
      case CallTerm.Fn lhs && preRhs instanceof CallTerm.Fn rhs -> lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lhs.ref());
      case CallTerm.Prim lhs && preRhs instanceof CallTerm.Prim rhs -> lhs.ref() != rhs.ref() ? null : visitCall(lhs, rhs, lhs.ref());
      default -> null;
    };
  }

  @Override public void traceEntrance(@NotNull Term lhs, @NotNull Term rhs) {
    defeq.traceEntrance(new Trace.UnifyT(lhs.freezeHoles(defeq.levelEqns),
      rhs.freezeHoles(defeq.levelEqns), defeq.pos));
  }

  @Override public void traceExit(@Nullable Term term) {
    defeq.traceExit();
  }

  private @NotNull EqnSet.Eqn createEqn(@NotNull Term lhs, @NotNull Term rhs) {
    return new EqnSet.Eqn(lhs, rhs, cmp, defeq.pos, defeq.varSubst.toImmutableMap());
  }

  @Override public @Nullable Term visitRef(@NotNull RefTerm lhs, @NotNull Term preRhs) {
    if (preRhs instanceof RefTerm rhs
      && defeq.varSubst.getOrDefault(rhs.var(), rhs).var() == lhs.var()) {
      return rhs.type();
    }
    return null;
  }

  @Override public @Nullable Term visitApp(@NotNull ElimTerm.App lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof ElimTerm.App rhs)) return null;
    var preFnType = compare(lhs.of(), rhs.of());
    if (!(preFnType instanceof FormTerm.Pi fnType)) return null;
    if (!defeq.compare(lhs.arg().term(), rhs.arg().term(), fnType.param().type())) return null;
    return fnType.substBody(lhs.arg().term());
  }

  @Override public @Nullable Term visitAccess(CallTerm.@NotNull Access lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof CallTerm.Access rhs)) return null;
    var preStructType = compare(lhs.of(), rhs.of());
    if (!(preStructType instanceof CallTerm.Struct structType)) return null;
    if (lhs.ref() != rhs.ref()) return null;
    return Def.defResult(lhs.ref());
  }

  @Override public @Nullable Term visitProj(@NotNull ElimTerm.Proj lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof ElimTerm.Proj rhs)) return null;
    var preTupType = compare(lhs.of(), rhs.of());
    if (!(preTupType instanceof FormTerm.Sigma tupType)) return null;
    if (lhs.ix() != rhs.ix()) return null;
    var params = tupType.params();
    for (int i = 1; i < lhs.ix(); i++) {
      var l = new ElimTerm.Proj(lhs, i);
      var currentParam = params.first();
      params = params.view().drop(1)
        .map(x -> x.subst(currentParam.ref(), l)).toImmutableSeq();
    }
    if (params.isNotEmpty()) return params.first().type();
    return params.last().type();
  }

  private @Nullable Substituter.TermSubst extract(
    @NotNull CallTerm.Hole lhs, @NotNull Term rhs, @NotNull Meta meta
  ) {
    var subst = new Substituter.TermSubst(new MutableHashMap<>(/*spine.size() * 2*/));
    for (var arg : lhs.args().view().zip(meta.telescope)) {
      Term etaTerm = EtaConversion.simpleEtaReduction(arg._1.term());
      if (etaTerm instanceof RefTerm ref) {
        if (subst.map().containsKey(ref.var())) return null;
        subst.add(ref.var(), arg._2.toTerm());
      } else return null;
    }
    return subst;
  }

  @Override public @Nullable Term visitHole(CallTerm.@NotNull Hole lhs, @NotNull Term rhs) {
    var meta = lhs.ref().core();
    if (rhs instanceof CallTerm.Hole rcall && lhs.ref() == rcall.ref()) {
      var holeTy = FormTerm.Pi.make(meta.telescope, meta.result);
      for (var arg : lhs.args().view().zip(rcall.args())) {
        if (!(holeTy instanceof FormTerm.Pi holePi))
          throw new IllegalStateException("meta arg size larger than param size. this should not happen");
        if (!defeq.compare(arg._1.term(), arg._2.term(), holePi.param().type())) return null;
        holeTy = holePi.substBody(arg._1.term());
      }
      return holeTy;
    }
    var argSubst = extract(lhs, rhs, meta);
    if (argSubst == null) {
      defeq.reporter.report(new HoleProblem.BadSpineError(lhs, defeq.pos));
      return null;
    }
    var subst = Unfolder.buildSubst(meta.contextTele, lhs.contextArgs());
    // In this case, the solution may not be unique (see #608),
    // so we may delay its resolution to the end of the tycking when we disallow vague unification.
    if (!defeq.allowVague && subst.overlap(argSubst).anyMatch(var -> rhs.findUsages(var) > 0)) {
      defeq.termEqns.addEqn(createEqn(lhs, rhs));
      // Skip the unification and scope check
      return meta.result;
    }
    subst.add(argSubst);
    defeq.varSubst.forEach(subst::add);
    var solved = rhs.subst(subst);
    assert meta.body == null;
    compare(solved.computeType(), meta.result);
    var scopeCheck = solved.scopeCheck(meta.fullTelescope().map(Term.Param::ref).toImmutableSeq());
    if (scopeCheck.isNotEmpty()) {
      defeq.reporter.report(new HoleProblem.BadlyScopedError(lhs, solved, scopeCheck, defeq.pos));
      return new ErrorTerm(solved);
    }
    if (!meta.solve(lhs.ref(), solved)) {
      defeq.reporter.report(new HoleProblem.RecursionError(lhs, solved, defeq.pos));
      return new ErrorTerm(solved);
    }
    defeq.tracing(builder -> builder.append(new Trace.LabelT(defeq().pos, "Hole solved!")));
    return meta.result;
  }

  @Override public @NotNull Term visitError(@NotNull ErrorTerm term, @NotNull Term term2) {
    return ErrorTerm.typeOf(term.freezeHoles(defeq.levelEqns));
  }

  @Override public @Nullable Term visitPi(@NotNull FormTerm.Pi lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof FormTerm.Pi rhs)) return null;
    return defeq.checkParam(lhs.param(), rhs.param(), FormTerm.Univ.OMEGA, () -> null, () -> {
      var bodyIsOk = defeq.compare(lhs.body(), rhs.body(), FormTerm.Univ.OMEGA);
      if (!bodyIsOk) return null;
      return FormTerm.Univ.OMEGA;
    });
  }

  @Override public @Nullable Term visitSigma(@NotNull FormTerm.Sigma lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof FormTerm.Sigma rhs)) return null;
    return defeq.checkParams(lhs.params(), rhs.params(), () -> null, () -> {
      var bodyIsOk = defeq.compare(lhs.params().last().type(), rhs.params().last().type(), FormTerm.Univ.OMEGA);
      if (!bodyIsOk) return null;
      return FormTerm.Univ.OMEGA;
    });
  }

  @Override public @Nullable Term visitUniv(@NotNull FormTerm.Univ lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof FormTerm.Univ rhs)) return null;
    defeq.levelEqns.add(lhs.sort(), rhs.sort(), cmp, defeq.pos);
    return new FormTerm.Univ((cmp == Ordering.Lt ? lhs : rhs).sort().lift(1));
  }

  private static Term unreachable() {
    throw new IllegalStateException();
  }

  @Override public @NotNull Term visitTup(@NotNull IntroTerm.Tuple lhs, @NotNull Term preRhs) {
    return unreachable();
  }

  @Override public @NotNull Term visitNew(@NotNull IntroTerm.New newTerm, @NotNull Term term) {
    return unreachable();
  }

  @NotNull LevelSubst levels(
    @NotNull DefVar<? extends Def, ? extends Decl> def,
    ImmutableSeq<@NotNull Sort> l, ImmutableSeq<@NotNull Sort> r
  ) {
    var levelSubst = new LevelSubst.Simple(MutableMap.create());
    for (var levels : l.zip(r).zip(Def.defLevels(def))) {
      defeq.levelEqns.add(levels._1._1, levels._1._2, cmp, defeq.pos);
      levelSubst.solution().put(levels._2, levels._1._1);
    }
    return levelSubst;
  }

  @Override public @Nullable Term visitFnCall(@NotNull CallTerm.Fn lhs, @NotNull Term preRhs) {
    return null;
  }

  @Nullable public Term visitCall(
    @NotNull CallTerm lhs, @NotNull CallTerm rhs,
    @NotNull DefVar<? extends Def, ? extends Decl> lhsRef
  ) {
    var retType = getType(lhs, lhsRef);
    // Lossy comparison
    var subst = levels(lhsRef, lhs.sortArgs(), rhs.sortArgs());
    if (defeq.visitArgs(lhs.args(), rhs.args(), Term.Param.subst(Def.defTele(lhsRef), subst))) return retType;
    if (defeq.compareWHNF(lhs, rhs, retType)) return retType;
    else return null;
  }

  @NotNull private Term getType(@NotNull CallTerm lhs, @NotNull DefVar<? extends Def, ?> lhsRef) {
    var substMap = MutableMap.<Var, Term>create();
    for (var pa : lhs.args().view().zip(lhsRef.core.telescope().view())) {
      substMap.set(pa._2.ref(), pa._1.term());
    }
    return lhsRef.core.result().subst(substMap);
  }

  @Override public @Nullable Term visitDataCall(@NotNull CallTerm.Data lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof CallTerm.Data rhs) || lhs.ref() != rhs.ref()) return null;
    var subst = levels(lhs.ref(), lhs.sortArgs(), rhs.sortArgs());
    var args = defeq.visitArgs(lhs.args(), rhs.args(), Term.Param.subst(Def.defTele(lhs.ref()), subst));
    // Do not need to be computed precisely because unification won't need this info
    return args ? FormTerm.Univ.OMEGA : null;
  }

  @Override public @Nullable Term visitStructCall(@NotNull CallTerm.Struct lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof CallTerm.Struct rhs) || lhs.ref() != rhs.ref()) return null;
    var subst = levels(lhs.ref(), lhs.sortArgs(), rhs.sortArgs());
    var args = defeq.visitArgs(lhs.args(), rhs.args(), Term.Param.subst(Def.defTele(lhs.ref()), subst));
    return args ? FormTerm.Univ.OMEGA : null;
  }

  @Override public @Nullable Term visitPrimCall(CallTerm.@NotNull Prim lhs, @NotNull Term preRhs) {
    return null;
  }

  @Override public @Nullable Term visitConCall(@NotNull CallTerm.Con lhs, @NotNull Term preRhs) {
    if (!(preRhs instanceof CallTerm.Con rhs) || lhs.ref() != rhs.ref()) return null;
    var retType = getType(lhs, lhs.ref());
    // Lossy comparison
    var subst = levels(lhs.head().dataRef(), lhs.sortArgs(), rhs.sortArgs());
    if (defeq.visitArgs(lhs.conArgs(), rhs.conArgs(), Term.Param.subst(CtorDef.conTele(lhs.ref()), subst)))
      return retType;
    return null;
  }

  @Override public @NotNull Term visitLam(@NotNull IntroTerm.Lambda lhs, @NotNull Term preRhs) {
    return unreachable();
  }
}
