// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli;

import org.aya.api.error.SourceFileLocator;
import org.glavo.kala.control.Option;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class DefaultLocator implements SourceFileLocator {
  @Override public @NotNull Option<String> locate(@NotNull Path path) {
    return Option.some(path.toAbsolutePath().toString());
  }
}
