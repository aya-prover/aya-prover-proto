// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle.node;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.aya.api.ref.Var;
import org.aya.truffle.Telescope;
import org.jetbrains.annotations.NotNull;

/**
 * @author zaoqi
 */
public final class DeclNode extends AyaNode {
  private final @NotNull FrameSlot slot;
  @Child
  @SuppressWarnings("FieldMayBeFinal")
  private @NotNull AyaNode node;

  public DeclNode(@NotNull FrameSlot slot, @NotNull AyaNode node) {
    this.slot = slot;
    this.node = node;
  }

  @Override
  @SuppressWarnings("deprecation")
  public @NotNull Object execute(@NotNull VirtualFrame frame) {
    Object value = this.node.execute(frame);
    if (this.slot.getKind() != FrameSlotKind.Object) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      this.slot.setKind(FrameSlotKind.Object);
    }
    frame.setObject(this.slot, value);
    return value;
  }
}
