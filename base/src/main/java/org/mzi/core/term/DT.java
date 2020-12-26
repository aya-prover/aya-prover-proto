// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.term;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.util.DTKind;
import org.mzi.core.Tele;
import org.mzi.util.Decision;

/**
 * @author re-xyr, kiva
 */
public record DT(@NotNull DTKind kind, @NotNull Tele telescope, @NotNull Term last) implements Term {
  @Override @Contract(pure = true) public @NotNull Decision whnf() {
    return Decision.YES;
  }

  @Override @Contract(pure = true) public @NotNull Decision neutral() {
    return Decision.NO;
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitDT(this, p);
  }

  @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitDT(this, p, q);
  }
}
