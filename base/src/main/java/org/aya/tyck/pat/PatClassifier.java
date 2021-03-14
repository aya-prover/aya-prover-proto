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
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public final class PatClassifier implements Pat.Visitor<
  Tuple2<
    @NotNull SeqLike<PatClassifier.@NotNull TypedPats>,
    Term>,
  SeqLike<@NotNull ImmutableSeq<PatClassifier.@NotNull TypedPats>>> {
  private static final @NotNull PatClassifier INSTANCE = new PatClassifier();

  public static @NotNull ImmutableSeq<@NotNull ImmutableSeq<@NotNull TypedPats>>
  classify(@NotNull Pat pat, @NotNull SeqLike<@NotNull TypedPats> totalPats, @NotNull Term type) {
    return pat.accept(PatClassifier.INSTANCE, Tuple.of(totalPats, type))
      .toImmutableSeq();
  }

  private PatClassifier() {
  }

  @Override
  public SeqLike<@NotNull ImmutableSeq<@NotNull TypedPats>> visitBind(
    Pat.@NotNull Bind bind,
    Tuple2<SeqLike<TypedPats>, Term> clausesType
  ) {
    return ImmutableSeq.empty();
  }

  @Override
  public SeqLike<@NotNull ImmutableSeq<@NotNull TypedPats>> visitTuple(
    Pat.@NotNull Tuple tuple,
    Tuple2<SeqLike<TypedPats>, Term> clausesType
  ) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SeqLike<@NotNull ImmutableSeq<@NotNull TypedPats>> visitCtor(
    Pat.@NotNull Ctor ctor,
    Tuple2<SeqLike<TypedPats>, Term> clausesType
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

    return groups;
  }

  private @Nullable PatClassifier.TypedPats unifyPattern(Pat.Ctor newPat, @NotNull PatClassifier.TypedPats typedPats) {
    return PatUnify
      .unify(typedPats.pats.first(), newPat)
      .map(subst -> typedPats.inst(newPat.toTerm(), subst))
      .getOrNull();
  }

  /**
   * @param ix    the index of the original clause
   * @param pats  the current list of patterns, might be nested
   * @param param the current telescope, without the codomain
   *              because LHS check doesn't need it
   * @author ice1000
   */
  public record TypedPats(
    @NotNull SeqView<@NotNull Pat> pats,
    int ix,
    @NotNull ImmutableSeq<Term.@NotNull Param> param
  ) {
    /** @apiNote parameter <code>subst</code> will be modified. */
    @Contract(value = "_, _ -> new")
    public @NotNull TypedPats inst(@NotNull Term inst, Substituter.@NotNull TermSubst subst) {
      subst.add(param.first().ref(), inst);
      return new TypedPats(pats.drop(1), ix, Def.substParams(param, subst));
    }
  }
}
