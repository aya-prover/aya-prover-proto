// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.error;

import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;

/**
 * @author ice1000
 */
public record StreamReporter(@NotNull String sourceCode, @NotNull PrintStream stream) implements Reporter {
  @Override public void report(@NotNull Problem problem) {
    var errorMsg = errorMsg(sourceCode, problem);
    stream.println(errorMsg);
  }

  public static @NotNull String errorMsg(@NotNull String sourceCode, @NotNull Problem problem) {
    if (problem.sourcePos() == SourcePos.NONE)
      return problem.describe().debugRender();
    var error = problem.toPrettyError(sourceCode).toDoc();
    return error.renderWithPageWidth(120);
  }
}
