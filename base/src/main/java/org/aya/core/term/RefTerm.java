// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.api.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record RefTerm(@NotNull LocalVar var, @NotNull Term type) implements Term {
  @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitRef(this, p);
  }
}
