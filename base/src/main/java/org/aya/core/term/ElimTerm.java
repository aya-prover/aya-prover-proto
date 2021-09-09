// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.api.util.Arg;
import org.aya.core.visitor.Substituter;
import org.jetbrains.annotations.NotNull;

/**
 * Elimination rules.
 *
 * @author ice1000
 */
public sealed interface ElimTerm extends Term {
  @NotNull Term of();

  /**
   * @author re-xyr
   */
  record Proj(@NotNull Term of, int ix) implements ElimTerm {
    public static @NotNull Substituter.TermSubst
    projSubst(@NotNull Term term, int index, ImmutableSeq<Param> telescope) {
      // instantiate the type
      var subst = new Substituter.TermSubst(MutableMap.create());
      telescope.view().take(index).reversed().forEachIndexed((i, param) ->
        subst.add(param.ref(), new Proj(term, i + 1)));
      return subst;
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitProj(this, p);
    }

  }

  record App(@NotNull Term of, @NotNull Arg<@NotNull Term> arg) implements ElimTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitApp(this, p);
    }

  }
}
