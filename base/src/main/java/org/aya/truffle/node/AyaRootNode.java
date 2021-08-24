// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle.node;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import org.aya.api.ref.Var;
import org.aya.truffle.AyaTruffleLanguage;
import org.aya.truffle.Telescope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author zaoqi
 */
public final class AyaRootNode extends RootNode {
  @Children
  @SuppressWarnings("FieldMayBeFinal")
  private AyaNode[] nodes;

  public AyaRootNode(@NotNull AyaTruffleLanguage lang, @NotNull FrameDescriptor frameDescriptor, AyaNode @NotNull [] nodes) {
    // TODO[zaoqi]: Creating a TruffleLanguage instance is not necessary? I am not sure about this.
    //super(lang, frameDescriptor);
    super(null, frameDescriptor);
    this.nodes = nodes;
  }

  public static @NotNull AyaRootNode create(@NotNull AyaTruffleLanguage lang, @NotNull FrameDescriptor frameDescriptor,
                                            FrameSlot @NotNull [] args, AyaNode @NotNull [] bodyNodes) {
    var allNodes = new AyaNode[args.length + bodyNodes.length];
    for (var i = 0; i < args.length; i++) {
      allNodes[i] = new DeclNode(args[i], new AyaNode.ArgNode(i));
    }
    System.arraycopy(bodyNodes, 0, allNodes, args.length, bodyNodes.length);
    return new AyaRootNode(lang, frameDescriptor, allNodes);
  }

  public static @NotNull AyaRootNode create(@NotNull Telescope telescope,
                                            FrameSlot @NotNull [] args,
                                            AyaNode @NotNull [] nodes) {
    return AyaRootNode.create(telescope.lang(), telescope.frameDescriptor(), args, nodes);
  }

  public static @NotNull AyaRootNode create(@NotNull Telescope telescope, @NotNull AyaNode @NotNull [] nodes) {
    return new AyaRootNode(telescope.lang(), telescope.frameDescriptor(), nodes);
  }

  public static FrameSlot @NotNull [] createFrameSlotArr(@NotNull Telescope telescope, Var @NotNull [] identifiers) {
    FrameSlot[] argsSlot = new FrameSlot[identifiers.length];
    for (var i = 0; i < argsSlot.length; i++) {
      argsSlot[i] = telescope.add(identifiers[i]);
    }
    return argsSlot;
  }

  public @NotNull DirectCallNode createDirectCallNode() {
    return Truffle.getRuntime().createDirectCallNode(Truffle.getRuntime().createCallTarget(this));
  }

  @Override
  public @Nullable Object execute(@NotNull VirtualFrame frame) {
    Object result = null;
    for (AyaNode node : this.nodes) {
      result = node.execute(frame);
    }
    return result;
  }
}
