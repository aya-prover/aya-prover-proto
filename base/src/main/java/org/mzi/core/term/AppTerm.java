// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.term;

import asia.kala.collection.Seq;
import asia.kala.collection.mutable.Buffer;
import asia.kala.control.Option;
import asia.kala.ref.OptionRef;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;
import org.mzi.core.def.FnDef;
import org.mzi.core.visitor.Substituter;
import org.mzi.generic.Arg;
import org.mzi.api.ref.DefVar;
import org.mzi.util.Decision;

import java.util.HashMap;

/**
 * @author ice1000
 * @see org.mzi.core.term.AppTerm#make(Term, Arg)
 */
public sealed interface AppTerm extends Term {
  @NotNull Term fn();
  @NotNull Seq<@NotNull ? extends @NotNull Arg<? extends Term>> args();

  @Contract(pure = true) static @NotNull Term make(@NotNull Term f, @NotNull Arg<? extends Term> arg) {
    if (f instanceof HoleApp holeApp) {
      holeApp.argsBuf().append(Arg.uncapture(arg));
      return holeApp;
    }
    if (!(f instanceof LamTerm lam)) return new Apply(f, arg);
    var tele = lam.telescope();
    var next = tele.next();
    return (next != null ? new LamTerm(next, lam.body()) : lam.body()).subst(new Substituter.TermSubst(tele.ref(), arg.term()));
  }

   @Contract(pure = true) static @NotNull Term make(@NotNull Term f, @NotNull Seq<? extends Arg<? extends Term>> args) {
    if (args.isEmpty()) return f;
    if (f instanceof HoleApp holeApp) {
      holeApp.argsBuf().appendAll(args.view().map(Arg::uncapture));
      return holeApp;
    }
    if (!(f instanceof LamTerm lam)) return make(new Apply(f, args.first()), args.view().drop(1));
    var next = lam.telescope();
    var subst = new Substituter.TermSubst(new HashMap<>());
    for (int i = 0; i < args.size(); i++) {
      if (next != null) {
        subst.add(next.ref(), args.get(i).term());
        next = next.next();
      } else return make(lam.body().subst(subst), args.view().drop(i));
    }
    return (next != null ? new LamTerm(next, lam.body()) : lam.body()).subst(subst);
  }

  record FnCall(
    @NotNull DefVar<FnDef> fnRef,
    @NotNull Seq<@NotNull ? extends @NotNull Arg<? extends Term>> args
  ) implements AppTerm {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitFnCall(this, p);
    }

    @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitFnCall(this, p, q);
    }

    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      return Decision.NO;
    }

    @Contract(pure = true) @Override public @NotNull Decision neutral() {
      return Decision.NO; // TODO[xyr]: This is problematic. In mutual recursions this should be YES.
                          //  Also, after adding inductive datatypes, we need to check if the fn pattern matches.
    }

    @Contract(value = " -> new", pure = true)
    @Override public @NotNull Term fn() {
      return new RefTerm(fnRef);
    }
  }

  record Apply(
    @NotNull Term fn,
    @NotNull Arg<? extends Term> arg
  ) implements AppTerm {
    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      return Decision.NO;
    }

    @Contract(pure = true) @Override public @NotNull Decision neutral() {
      if (fn instanceof LamTerm) return Decision.NO;
      else return Decision.YES;
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitApp(this, p);
    }

    @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitApp(this, p, q);
    }

    @Contract(" -> new")
    @Override public @NotNull Seq<@NotNull Arg<? extends Term>> args() {
      return Seq.of(arg());
    }
  }

  /**
   * @author ice1000
   */
  record HoleApp(
    @NotNull OptionRef<@NotNull Term> solution,
    @NotNull Var var,
    @NotNull Buffer<@NotNull Arg<Term>> argsBuf
  ) implements AppTerm {
    public HoleApp(
      @Nullable Term solution, @NotNull Var var,
      @NotNull Buffer<@NotNull Arg<Term>> args
    ) {
      this(new OptionRef<>(Option.of(solution)), var, args);
    }

    public HoleApp(@NotNull Var var) {
      this((Term) null, var, Buffer.of());
    }

    @Override public @NotNull Seq<@NotNull ? extends @NotNull Arg<? extends Term>> args() {
      return argsBuf;
    }

    @Contract(" -> new") @Override public @NotNull Term fn() {
      return new RefTerm(var);
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitHole(this, p);
    }

    @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitHole(this, p, q);
    }

    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      return Decision.MAYBE;
    }

    @Contract(pure = true) @Override public @NotNull Decision neutral() {
      return Decision.MAYBE;
    }
  }
}
