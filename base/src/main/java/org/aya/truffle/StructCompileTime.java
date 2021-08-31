// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.tuple.Tuple;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.core.def.FieldDef;
import org.aya.core.def.StructDef;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * @author zaoqi
 */
public record StructCompileTime(@NotNull Var name,
                                @NotNull ImmutableMap<Var, Integer> map,
                                @NotNull ImmutableSeq<FieldDef> field) {
  public static @NotNull StructCompileTime create(@NotNull StructDef def) {
    return new StructCompileTime(def.ref(),
      def.fields.mapIndexed((i, f) -> Tuple.of(f.ref(), i)).toImmutableMap(),
      def.fields);
  }

  public static @NotNull Term addSelfTele(@NotNull ImmutableSeq<Term.Param> selfTele, @NotNull Term term) {
    for (var e : selfTele.reversed()) {
      term = new IntroTerm.Lambda(e, term);
    }
    return term;
  }
}
