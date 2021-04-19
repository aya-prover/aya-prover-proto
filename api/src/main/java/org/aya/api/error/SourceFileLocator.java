// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.error;

import org.glavo.kala.control.Option;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface SourceFileLocator {
  /**
   * Resolve absolute source file path to module-root.
   * for example, assuming we have a module root `/path/to/root`,
   * resolving the source file path `/path/to/root/A/B/C.aya`
   * should result in `A/B/C.aya`
   *
   * @param path Path to source file
   * @return source name relative to its module root
   */
  @NotNull Option<String> locate(@NotNull Path path);
}
