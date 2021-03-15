// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.pat.PatTree;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record MissingCaseError(
  @NotNull SourcePos sourcePos,
  @NotNull Buffer<PatTree> pats
) implements Problem {
  @Override public @NotNull Doc describe() {
    var sb = new StringBuilder();
    var started = false;
    for (var pat : pats) {
      if (started) sb.append(", ");
      pat.buildString(sb);
      started = true;
    }
    return Doc.hcat(
      Doc.plain("Unhandled case: "),
      Doc.plain(sb.toString())
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
