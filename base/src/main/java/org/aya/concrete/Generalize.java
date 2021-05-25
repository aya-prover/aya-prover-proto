// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.LevelGenVar;
import org.aya.api.util.WithPos;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

public sealed interface Generalize extends Stmt {
  @Override default @NotNull Accessibility accessibility() {
    return Accessibility.Private;
  }

  interface Visitor<P, R> {
    default void traceEntrance(@NotNull Generalize generalize, P p) {
    }
    default void traceExit(P p, R r) {
    }
    R visitLevels(@NotNull Generalize.Levels levels, P p);
    R visitVariables(@NotNull Generalize.Variables variables, P p);
  }

  @Override default <P, R> R doAccept(Stmt.@NotNull Visitor<P, R> visitor, P p) {
    return doAccept((Visitor<? super P, ? extends R>) visitor, p);
  }

  <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

  record Levels(
    @NotNull SourcePos sourcePos,
    @NotNull LevelGenVar.Kind kind,
    @NotNull ImmutableSeq<WithPos<LevelGenVar>> levels
  ) implements Generalize {
    @Override public <P, R> R doAccept(@NotNull Generalize.Visitor<P, R> visitor, P p) {
      return visitor.visitLevels(this, p);
    }
  }

  /**
   * Represents a variable statement.
   * For instance, <code>variable A : Nat</code> defines a generalized variable that can bound an
   * unbounded variables used in an {@link Expr} like <code>def add {a b : A} : A</code> later.
   * @author AustinZhu
   * @param sourcePos
   * @param params
   */
  record Variables(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Expr.Param> params
  ) implements Generalize {
    @Override
    public <P, R> R doAccept(Generalize.@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitVariables(this, p);
    }
  }
}
