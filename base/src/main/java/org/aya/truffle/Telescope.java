// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import org.aya.api.ref.Var;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author zaoqi
 */
public record Telescope(@NotNull AyaTruffleLanguage lang, @Nullable Telescope parent,
                        @NotNull FrameDescriptor frameDescriptor) {
  public Telescope(@NotNull AyaTruffleLanguage lang) {
    this(lang, null, new FrameDescriptor());
  }

  public Telescope(@NotNull Telescope parent) {
    this(parent.lang(), parent, new FrameDescriptor());
  }

  public @NotNull FrameSlot add(@NotNull Var identifier) {
    try {
      return frameDescriptor.addFrameSlot(identifier, FrameSlotKind.Object);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("identifier already defined", e);
    }
  }

  public @NotNull Slot get(@NotNull Var identifier) {
    var telescope = this;
    int level = 0;
    while (telescope != null) {
      var result = telescope.frameDescriptor.findFrameSlot(identifier);
      if (result != null) {
        return new Slot(result, level);
      }
      level++;
      telescope = telescope.parent;
    }
    throw new IllegalArgumentException("identifier not defined: " + identifier);
  }

  public @NotNull FrameSlot getLvl0(@NotNull Var identifier) {
    return Objects.requireNonNull(this.frameDescriptor.findFrameSlot(identifier), "identifier not defined in the level 0: " + identifier);
  }
}
