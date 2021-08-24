// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle;

import com.oracle.truffle.api.frame.MaterializedFrame;
import kala.collection.immutable.ImmutableMap;
import kala.tuple.Tuple2;
import org.aya.api.ref.Var;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * @author zaoqi
 */
public record Context(@NotNull Telescope telescope, @NotNull MaterializedFrame frame) {
  public Context(@NotNull Telescope telescope, @NotNull MaterializedFrame frame) {
    assert frame.getFrameDescriptor() == telescope.frameDescriptor();
    this.telescope = telescope;
    this.frame = frame;
  }

  public @NotNull Object run(@NotNull Term term) {
    return new Transpiler(telescope).transpile(term).execute(frame);
  }

  public @NotNull ImmutableMap<Var, Object> toMap() {
    var global = this.telescope();
    var frame = this.frame();
    assert frame.getFrameDescriptor() == global.frameDescriptor();
    var defs = global.frameDescriptor().getIdentifiers();
    return defs.stream().map(def -> {
      Var id = (Var) def;
      return new Tuple2<>(id, frame.getValue(global.getLvl0(id)));
    }).collect(ImmutableMap.collector(x -> x.component1(), x -> x.component2()));
  }

  public @NotNull ImmutableMap<String, Object> toStrMap() {
    var map = this.toMap();
    return map.view().map((k, v) -> new Tuple2<>(k.name(), v))
      .collect(ImmutableMap.collector(x -> x.component1(), x -> x.component2()));
  }
}
