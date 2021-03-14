// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatUnify;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.SeqView;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Option;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public final class PatClassifier implements Pat.Visitor<
  @NotNull SeqLike<PatClassifier.@NotNull TypedPats>,
  SeqLike<@NotNull ImmutableSeq<PatClassifier.@NotNull TypedPats>>> {
  private static final @NotNull PatClassifier INSTANCE = new PatClassifier();

  /**
   * @param pat     the pattern to split
   * @param clauses all the other patterns to classify
   * @apiNote The first returned group is the group that matches the current split pattern.
   */
  public static @NotNull ImmutableSeq<@NotNull ImmutableSeq<@NotNull TypedPats>>
  classify(@NotNull Pat pat, @NotNull SeqLike<@NotNull TypedPats> clauses) {
    return pat.accept(PatClassifier.INSTANCE, clauses)
      .toImmutableSeq();
  }

  private PatClassifier() {
  }

  @Override
  public SeqLike<@NotNull ImmutableSeq<@NotNull TypedPats>>
  visitBind(Pat.@NotNull Bind bind, SeqLike<TypedPats> clauses) {
    return ImmutableSeq.of(clauses.toImmutableSeq());
  }

  @Override
  public SeqLike<@NotNull ImmutableSeq<@NotNull TypedPats>>
  visitTuple(Pat.@NotNull Tuple tuple, SeqLike<TypedPats> clauses) {
    return ImmutableSeq.of(clauses.toImmutableSeq());
  }

  @Override
  public SeqLike<@NotNull ImmutableSeq<@NotNull TypedPats>>
  visitCtor(Pat.@NotNull Ctor ctor, SeqLike<TypedPats> clauses) {
    var available = ctor.type().availableCtors().toImmutableSeq();
    var groups = available
      .view()
      .filter(c -> c.ref() != ctor.ref())
      .map(pat -> pat.freshPat(ctor.explicit()))
      .map(newPat -> clauses.view()
        .flatMap(typedPats -> unifyPattern(newPat, typedPats))
        .toImmutableSeq());
    // TODO[ice]: indexed inductive type
    assert available.anyMatch(c -> c.ref() == ctor.ref());
    var patCtor = ctor.ref().core;
    var neighbor = clauses.view()
      .flatMap(typedPats -> unifyPattern(patCtor.freshPat(ctor.explicit()), typedPats));
    CockxAbel.splitFirst(neighbor);
    // TODO[ice]: ^ how to use the results?
    return groups;
  }

  private Option<PatClassifier.TypedPats> unifyPattern(Pat.Ctor newPat, @NotNull PatClassifier.TypedPats typedPats) {
    return PatUnify
      .unify(typedPats.pats.first(), newPat)
      .map(subst -> typedPats.inst(newPat.toTerm(), subst));
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
    @NotNull ImmutableSeq<Term.@NotNull Param> param,
    @NotNull Option<Term> body
  ) {
    public TypedPats(Def.@NotNull Signature signature, int ix, Pat.@NotNull PrototypeClause clause) {
      this(clause.patterns().view(), ix, signature.param(), clause.expr());
    }

    /** @apiNote parameter <code>subst</code> will be modified. */
    @Contract(value = "_, _ -> new")
    public @NotNull TypedPats inst(@NotNull Term inst, Substituter.@NotNull TermSubst subst) {
      subst.add(param.first().ref(), inst);
      var term = body.map(t -> t.subst(subst));
      return new TypedPats(pats.drop(1), ix, Def.substParams(param, subst), term);
    }
  }
}
