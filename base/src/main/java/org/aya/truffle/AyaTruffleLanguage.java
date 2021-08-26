// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.MaterializedFrame;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple2;
import org.aya.core.def.Def;
import org.aya.truffle.node.AyaNode;
import org.aya.truffle.node.AyaRootNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author zaoqi
 */
@TruffleLanguage.Registration(id = "aya", name = "Aya", characterMimeTypes = AyaTruffleLanguage.MIME_TYPE, defaultMimeType = AyaTruffleLanguage.MIME_TYPE)
public final class AyaTruffleLanguage extends TruffleLanguage<Void> {
  public static final String MIME_TYPE = "application/x-aya";

  // TODO[zaoqi]: Creating a TruffleLanguage instance is not necessary? I am not sure about this.

  @Override
  protected @Nullable Void createContext(Env env) {
    return null;
  }

  public @NotNull Context runDefs(@NotNull ImmutableSeq<Def> defs) {
    var global = new Telescope(this);
    var transpiler = new Transpiler(global);
    var slotWithDefs = defs.map(def -> new Tuple2<>(global.add(def.ref()), def));
    var nodes = slotWithDefs.view()
      .<AyaNode>map(x -> transpiler.transpileDef(x._1, x._2))
      .appended(new AyaNode.FrameGetterNode())
      .toArray(AyaNode.class);
    var rootNode = AyaRootNode.create(global, nodes)
      .createDirectCallNode();
    var frame = (MaterializedFrame) rootNode.call();
    assert frame.getFrameDescriptor() == global.frameDescriptor();
    return new Context(global, frame);
  }
}
