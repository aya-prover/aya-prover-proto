// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.concrete;

import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.pretty.doc.Docile;
import org.glavo.kala.control.Option;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public interface ConcreteExpr extends Docile {
  @NotNull SourcePos sourcePos();
  @NotNull ConcreteExpr resolve(@NotNull Option<String> sourceFile, @NotNull Reporter reporter);
  @NotNull ConcreteExpr desugar(@NotNull Option<String> sourceFile, @NotNull Reporter reporter);
}
