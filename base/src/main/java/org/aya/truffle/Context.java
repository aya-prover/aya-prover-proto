// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle;

import com.oracle.truffle.api.frame.MaterializedFrame;
import kala.collection.Seq;
import kala.collection.immutable.ImmutableMap;
import kala.tuple.Tuple;
import org.aya.api.ref.Var;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * @author zaoqi
 */
public record Context(@NotNull Telescope telescope, @NotNull MaterializedFrame frame) {
  public Context {
    assert frame.getFrameDescriptor() == telescope.frameDescriptor();
  }

  public @NotNull Object run(@NotNull Term term) {
    return new Transpiler(telescope).transpile(term).execute(frame);
  }

  public @NotNull ImmutableMap<Var, Object> toMap() {
    var global = telescope();
    var frame = frame();
    var globalDescriptor = global.frameDescriptor();
    assert frame.getFrameDescriptor() == globalDescriptor;
    var defs = Seq.from(globalDescriptor.getIdentifiers());
    return defs.view()
      .map(def -> (Var) def)
      .map(def -> Tuple.of(def, frame.getValue(global.getLvl0(def))))
      .toImmutableMap();
  }

  public @NotNull ImmutableMap<String, Object> toStrMap() {
    return toMap().view().map((k, v) -> Tuple.of(k.name(), v)).toImmutableMap();
  }
}
