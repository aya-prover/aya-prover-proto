// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.util.Decision;
import org.glavo.kala.collection.immutable.ImmutableMap;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public record NewTerm(
  @NotNull ImmutableMap<String, Term> params
) implements Term {
  @Override
  public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitNew(this, p);
  }

  @Override
  public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitNew(this, p, q);
  }

  @Override
  public @NotNull Decision whnf() {
    return Decision.YES;
  }
}
