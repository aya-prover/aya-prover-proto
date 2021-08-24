// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle;

import kala.tuple.Tuple2;
import org.aya.api.ref.Var;
import org.aya.core.def.FieldDef;
import org.aya.core.def.StructDef;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author zaoqi
 */
public record StructCompileTime(@NotNull Var name, @NotNull Map<Var, Integer> map, Var @NotNull [] fields) {
  public static @NotNull StructCompileTime create(@NotNull StructDef def) {
    return new StructCompileTime(def.ref(),
      def.fields
        .mapIndexed((i, f) -> new Tuple2<>(f.ref(), i))
        .collect(Collectors.toMap(x -> x.component1(), x -> x.component2())),
      def.fields.map(FieldDef::ref).toArray(Var.class));
  }
}
