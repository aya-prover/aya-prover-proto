// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.error;

import org.aya.pretty.doc.Doc;
import org.aya.pretty.error.PrettyError;
import org.glavo.kala.control.Option;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * @author ice1000
 */
public interface Problem {
  enum Severity {
    INFO,
    GOAL,
    ERROR,
    WARN,
  }

  enum Stage {
    TERCK,
    TYCK,
    RESOLVE,
    PARSE,
    OTHER
  }

  @NotNull Option<String> sourceFile();
  @NotNull SourcePos sourcePos();
  @NotNull Doc describe();
  @NotNull Severity level();
  default @NotNull Stage stage() {
    return Stage.OTHER;
  }
  default @NotNull Doc hint() {
    return Doc.empty();
  }

  default @NotNull PrettyError toPrettyError(
    @NotNull Path filePath,
    @NotNull String sourceCode
  ) {
    return new PrettyError(
      filePath.toString(),
      sourcePos().toSpan(sourceCode),
      switch (level()) {
        case WARN -> Doc.plain("Warning:");
        case GOAL -> Doc.plain("Goal:");
        case INFO -> Doc.plain("Info:");
        case ERROR -> Doc.plain("Error:");
      },
      describe(),
      hint()
    );
  }
}
