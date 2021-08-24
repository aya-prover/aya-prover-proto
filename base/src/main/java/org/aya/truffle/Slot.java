// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.jetbrains.annotations.NotNull;

/**
 * @author zaoqi
 */
public record Slot(@NotNull FrameSlot frameSlot, int level) {
  public static @NotNull Slot lvl0(@NotNull FrameSlot frameSlot) {
    return new Slot(frameSlot, 0);
  }

  @ExplodeLoop
  public @NotNull Object getValue(@NotNull VirtualFrame frame) {
    for (var i = 0; i < level; i++) {
      frame = (VirtualFrame) frame.getArguments()[0];
    }
    return frame.getValue(frameSlot);
  }
}
