// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import org.aya.api.ref.Var;
import org.aya.generic.Level;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Not inspired from Arend.
 * <a href="https://github.com/JetBrains/Arend/blob/master/base/src/main/java/org/arend/core/sort/Sort.java"
 * >Sort.java</a>
 *
 * @author ice1000
 */
public record Sort(@NotNull Level<LvlVar> uLevel, @NotNull Level<LvlVar> hLevel) {
  public static final @NotNull Level<LvlVar> INF_LVL = new Level.Infinity<>();
  public static final @NotNull Sort OMEGA = new Sort(INF_LVL, INF_LVL);

  public @NotNull Sort substSort(@NotNull LevelSubst subst) {
    return new Sort(subst.applyTo(uLevel), subst.applyTo(hLevel));
  }

  public @NotNull Sort max(@NotNull Sort other) {
    return new Sort(max(uLevel, other.uLevel), max(hLevel, other.hLevel));
  }

  public static @NotNull Level<LvlVar> max(@NotNull Level<LvlVar> lhs, @NotNull Level<LvlVar> rhs) {
    if (lhs instanceof Level.Infinity || rhs instanceof Level.Infinity) return INF_LVL;
    if (lhs instanceof Level.Reference<LvlVar> l) {
      if (rhs instanceof Level.Reference<LvlVar> r) {
        if (l.ref() == r.ref()) return new Level.Reference<>(l.ref(), Math.max(l.lift(), r.lift()));
      } else if (rhs instanceof Level.Constant<LvlVar> r) {
        if (r.value() <= l.lift()) return l;
      }
    } else if (lhs instanceof Level.Constant<LvlVar> l) {
      if (rhs instanceof Level.Reference<LvlVar> r) {
        if (l.value() <= r.lift()) return r;
      } else if (rhs instanceof Level.Constant<LvlVar> r) {
        return new Level.Constant<>(Math.max(l.value(), r.value()));
      }
    }
    throw new UnsupportedOperationException("TODO: lmax");
  }

  @Contract("_-> new") public @NotNull Sort succ(int n) {
    return new Sort(uLevel.lift(n), hLevel.lift(n));
  }

  /**
   * @param bound true if this is a bound level var, otherwise it needs to be solved.
   *              In well-typed terms it should always be true.
   * @author ice1000
   */
  public static final record LvlVar(
    @NotNull String name,
    boolean bound
  ) implements Var {
    @Override public boolean equals(@Nullable Object o) {
      return this == o;
    }

    @Override public int hashCode() {
      return System.identityHashCode(this);
    }
  }
}