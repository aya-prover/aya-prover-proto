// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.error;

import org.aya.pretty.doc.Doc;
import org.aya.pretty.error.PrettyError;
import org.glavo.kala.control.Option;
import org.jetbrains.annotations.NotNull;

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

  /**
   * The file path of this problem, relative to module root.
   *
   * @return filePath if specified, {@link Option#none()} if it is from junit tests.
   */
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

  default @NotNull PrettyError toPrettyError(@NotNull String sourceCode) {
    return new PrettyError(
      sourceFile().getOrDefault("<unknown-file>"),
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
