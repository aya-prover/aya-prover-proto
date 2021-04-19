// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.control.Option;
import org.jetbrains.annotations.NotNull;

public record RedefinitionError(
  @NotNull Option<String> sourceFile,
  @NotNull Kind kind,
  @NotNull String name,
  @NotNull SourcePos sourcePos
) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.hcat(Doc.plain("Redefinition of "), Doc.plain(kind.prettyName),
      Doc.plain(" `"), Doc.plain(name), Doc.plain("`"));
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }

  public enum Kind {
    Prim("primitive"),
    Ctor("constructor"),
    Field("field");

    final @NotNull String prettyName;

    Kind(@NotNull String name) {
      prettyName = name;
    }
  }
}
