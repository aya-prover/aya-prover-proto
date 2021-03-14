// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.api.util.NormalizeMode;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatUnify;
import org.aya.core.term.AppTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public final class PatClassifier implements Pat.Visitor<
  Tuple2<
    @NotNull ImmutableSeq<PatClassifier.@NotNull TypedClause>,
    Term>,
  SeqLike<@NotNull ImmutableSeq<PatClassifier.@NotNull TypedClause>>> {
  @Override
  public SeqLike<@NotNull ImmutableSeq<@NotNull TypedClause>> visitBind(
    Pat.@NotNull Bind bind,
    Tuple2<ImmutableSeq<TypedClause>, Term> clausesType
  ) {
    return ImmutableSeq.empty();
  }

  @Override
  public SeqLike<@NotNull ImmutableSeq<@NotNull TypedClause>> visitTuple(
    Pat.@NotNull Tuple tuple,
    Tuple2<ImmutableSeq<TypedClause>, Term> clausesType
  ) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SeqLike<@NotNull ImmutableSeq<@NotNull TypedClause>> visitCtor(
    Pat.@NotNull Ctor ctor,
    Tuple2<ImmutableSeq<TypedClause>, Term> clausesType
  ) {
    if (!(clausesType._2.normalize(NormalizeMode.WHNF) instanceof AppTerm.DataCall data)) {
      var s = clausesType._2.toDoc().renderWithPageWidth(100);
      throw new IllegalArgumentException(s + " is not a dataCall");
    }
    var available = data.availableCtors()
      .toImmutableSeq();
    var groups = available
      .view()
      .filter(c -> c.ref() != ctor.ref())
      .map(pat -> pat.freshPat(ctor.explicit()))
      .map(newPat -> clausesType._1
        .view()
        .map(typedClause -> unifyPattern(newPat, typedClause))
        .filterNotNull()
        .toImmutableSeq());
    // TODO[ice]: indexed inductive type
    assert available.anyMatch(c -> c.ref() == ctor.ref());
    /*
    var subclass = clausesType._1
      .view()
      .map(typedClause -> unifyPattern());
    */

    return groups;
  }

  private @Nullable TypedClause unifyPattern(Pat.Ctor newPat, @NotNull TypedClause typedClause) {
    return PatUnify
      .unify(typedClause.clauses.patterns().first(), newPat)
      .map(subst -> typedClause.inst(newPat.toTerm(), subst))
      .getOrNull();
  }

  /**
   * @author ice1000
   */
  public record TypedClause(
    Pat.@NotNull Clause clauses,
    Def.@NotNull Signature signature
  ) {
    public @NotNull TypedClause inst(@NotNull Term inst, @NotNull Substituter.TermSubst subst) {
      return new TypedClause(clauses, signature.inst(inst).subst(subst));
    }
  }
}
