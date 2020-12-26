// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.tyck.sort.Sort;
import org.mzi.util.Decision;

/**
 * @author ice1000
 */
public record UnivTerm(@NotNull Sort sort) implements Term {
  public static final /*@NotNull*/ UnivTerm OMEGA = new UnivTerm(Sort.OMEGA);

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitUniv(this, p);
  }

  @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitUniv(this, p, q);
  }

  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }

  @Override public @NotNull Decision neutral() {
    return Decision.NO;
  }
}
