// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle;

import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.jetbrains.annotations.NotNull;

/**
 * @author zaoqi
 */
public final class GenVar {
  // TODO: rewrite this hackish code
  public static @NotNull LocalVar create() {
    return new LocalVar("_" + Integer.toHexString((new Object()).hashCode()));
  }

  public static @NotNull LocalVar createFromObject(@NotNull Object x) {
    return new LocalVar("_" + x.getClass().getName() + "@" + Integer.toHexString(x.hashCode()));
  }
}
