// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pat;

import kala.tuple.Unit;
import org.aya.tyck.LocalCtx;
import org.jetbrains.annotations.NotNull;

/**
 * A little typer for core patterns.
 *
 * @author ice1000
 */
record PatTyper(@NotNull LocalCtx localCtx) implements Pat.Visitor<Unit, Unit> {
  @Override public Unit visitBind(Pat.@NotNull Bind bind, Unit unit) {
    localCtx.put(bind.as(), bind.type());
    return unit;
  }

  @Override public Unit visitAbsurd(Pat.@NotNull Absurd absurd, Unit unit) {
    throw new IllegalStateException();
  }

  @Override public Unit visitPrim(Pat.@NotNull Prim prim, Unit unit) {
    return unit;
  }

  @Override public Unit visitTuple(Pat.@NotNull Tuple tuple, Unit unit) {
    if (tuple.as() != null) localCtx.put(tuple.as(), tuple.type());
    tuple.pats().forEach(pat -> pat.accept(this, Unit.unit()));
    return unit;
  }

  @Override public Unit visitCtor(Pat.@NotNull Ctor ctor, Unit unit) {
    if (ctor.as() != null) localCtx.put(ctor.as(), ctor.type());
    ctor.params().forEach(pat -> pat.accept(this, Unit.unit()));
    return unit;
  }
}
