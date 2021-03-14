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
import org.glavo.kala.collection.SeqView;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public final class PatClassifier implements Pat.Visitor<
  Tuple2<
    @NotNull ImmutableSeq<PatClassifier.@NotNull TypedPats>,
    Term>,
  SeqLike<@NotNull ImmutableSeq<PatClassifier.@NotNull TypedPats>>> {
  @Override
  public SeqLike<@NotNull ImmutableSeq<@NotNull TypedPats>> visitBind(
    Pat.@NotNull Bind bind,
    Tuple2<ImmutableSeq<TypedPats>, Term> clausesType
  ) {
    return ImmutableSeq.empty();
  }

  @Override
  public SeqLike<@NotNull ImmutableSeq<@NotNull TypedPats>> visitTuple(
    Pat.@NotNull Tuple tuple,
    Tuple2<ImmutableSeq<TypedPats>, Term> clausesType
  ) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SeqLike<@NotNull ImmutableSeq<@NotNull TypedPats>> visitCtor(
    Pat.@NotNull Ctor ctor,
    Tuple2<ImmutableSeq<TypedPats>, Term> clausesType
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
        .map(typedPats -> unifyPattern(newPat, typedPats))
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

  private @Nullable PatClassifier.TypedPats unifyPattern(Pat.Ctor newPat, @NotNull PatClassifier.TypedPats typedPats) {
    return PatUnify
      .unify(typedPats.pats.first(), newPat)
      .map(subst -> typedPats.inst(newPat.toTerm(), subst))
      .getOrNull();
  }

  /**
   * @param ix        the index of the original clause
   * @param pats      the current list of patterns, might be nested
   * @param signature the current telescope
   * @author ice1000
   */
  public record TypedPats(
    @NotNull SeqView<@NotNull Pat> pats,
    int ix,
    Def.@NotNull Signature signature
  ) {
    public @NotNull TypedPats inst(@NotNull Term inst, @NotNull Substituter.TermSubst subst) {
      return new TypedPats(pats.drop(1), ix, signature.inst(inst).subst(subst));
    }
  }
}
