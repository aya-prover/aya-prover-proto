// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.core.sort.Sort;
import org.aya.generic.Level;
import org.aya.util.Constants;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * @author ice1000
 */
public sealed interface SerLevel extends Serializable {
  @NotNull Level<Sort.LvlVar> de(@NotNull MutableMap<Integer, Sort.LvlVar> cache);

  /** @param num -1 means infinity */
  record Const(int num) implements SerLevel {
    @Override public @NotNull Level<Sort.LvlVar> de(@NotNull MutableMap<Integer, Sort.LvlVar> cache) {
      return num >= 0 ? new Level.Constant<>(num) : new Level.Infinity<>();
    }
  }

  record LvlVar(int id) implements Serializable {
    public @NotNull Sort.LvlVar de(@NotNull MutableMap<Integer, Sort.LvlVar> cache) {
      return cache.getOrPut(id, () -> new Sort.LvlVar(Constants.ANONYMOUS_PREFIX, null));
    }
  }

  record Ref(@NotNull LvlVar var, int lift) implements SerLevel {
    @Override public @NotNull Level<Sort.LvlVar> de(@NotNull MutableMap<Integer, Sort.LvlVar> cache) {
      return new Level.Reference<>(var.de(cache), lift);
    }
  }

  record Max(@NotNull ImmutableSeq<SerLevel> levels) implements Serializable {
    public @NotNull Sort de(@NotNull MutableMap<Integer, Sort.LvlVar> cache) {
      return new Sort(levels.map(l -> l.de(cache)));
    }
  }

  static @NotNull Max ser(@NotNull Sort level, @NotNull MutableMap<Sort.LvlVar, Integer> cache) {
    return new Max(level.levels().map(l -> ser(l, cache)));
  }

  static @NotNull SerLevel ser(@NotNull Level<Sort.LvlVar> level, @NotNull MutableMap<Sort.LvlVar, Integer> cache) {
    return switch (level) {
      case Level.Constant<Sort.LvlVar> constant -> new Const(constant.value());
      case Level.Infinity<Sort.LvlVar> l -> new Const(-1);
      case Level.Reference<Sort.LvlVar> ref -> new Ref(ser(ref.ref(), cache), ref.lift());
      default -> throw new IllegalStateException(level.toString());
    };
  }

  static LvlVar ser(Sort.@NotNull LvlVar ref, @NotNull MutableMap<Sort.LvlVar, Integer> cache) {
    return new LvlVar(cache.getOrPut(ref, cache::size));
  }
}
