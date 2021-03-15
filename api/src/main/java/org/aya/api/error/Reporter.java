// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.error;

import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface Reporter {
  /**
   * Report an problem
   *
   * @param problem problem to report
   */
  void report(@NotNull Problem problem);

  @ApiStatus.Internal
  default void reportString(@NotNull String s) {
    report(new Problem() {
      @Override public @NotNull SourcePos sourcePos() {
        return SourcePos.NONE;
      }

      @Override public @NotNull Severity level() {
        return Severity.INFO;
      }

      @Override public @NotNull Doc describe() {
        return Doc.plain(s);
      }
    });
  }
}
