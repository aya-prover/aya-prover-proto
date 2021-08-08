// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.hatate;

import org.aya.api.error.SourcePos;
import org.aya.concrete.stmt.Stmt;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public final class Remark implements Stmt {
  public @NotNull Literate literate;
  public final @NotNull SourcePos sourcePos;

  public Remark(@NotNull Literate literate, @NotNull SourcePos sourcePos) {
    this.literate = literate;
    this.sourcePos = sourcePos;
  }

  @Override public @NotNull Accessibility accessibility() {
    return Accessibility.Private;
  }

  @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitRemark(this, p);
  }

  public @NotNull SourcePos sourcePos() {
    return sourcePos;
  }
}