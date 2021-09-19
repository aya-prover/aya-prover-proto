// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle;

import kala.tuple.Tuple;
import org.aya.api.ref.Var;
import org.aya.core.def.CtorDef;
import org.aya.core.def.DataDef;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author zaoqi
 */
public record DataCompileTime(Integer[] ctors, @NotNull Map<Var, Integer> tagMap) {
  public static @NotNull DataCompileTime create(@NotNull DataDef def) {
    return new DataCompileTime(def.body.map(x -> CtorDef.conTele(x.ref()).size()).toArray(Integer.class),
      def.body.mapIndexed((i, x) -> Tuple.of(x.ref, i))
        .collect(Collectors.toMap(x -> x.component1(), x -> x.component2())));
  }
}
