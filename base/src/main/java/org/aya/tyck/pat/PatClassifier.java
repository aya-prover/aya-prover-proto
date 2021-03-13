// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.api.util.NormalizeMode;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatUnify;
import org.aya.core.term.AppTerm;
import org.aya.core.term.Term;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record PatClassifier(
  @NotNull ImmutableSeq<@NotNull TypedClause> initialClauses,
  @NotNull Buffer<@NotNull ImmutableSeq<@NotNull TypedClause>> groups
) implements Pat.Visitor<Term, Unit> {
  @Override public Unit visitBind(Pat.@NotNull Bind bind, Term term) {
    return Unit.unit();
  }

  @Override public Unit visitTuple(Pat.@NotNull Tuple tuple, Term term) {
    throw new UnsupportedOperationException();
  }

  @Override public Unit visitCtor(Pat.@NotNull Ctor ctor, Term term) {
    if (!(term.normalize(NormalizeMode.WHNF) instanceof AppTerm.DataCall data)) {
      var s = term.toDoc().renderWithPageWidth(100);
      throw new IllegalArgumentException(s + " is not a dataCall");
    }
    data.availableCtors()
      .filter(c -> c.ref() != ctor.ref())
      .forEach(otherCtor -> {
        var newPat = otherCtor.freshPat(ctor.explicit());
        var filtered = initialClauses.map(typedClause -> {
          var clause = typedClause.clauses;
          var subst = PatUnify.unify(clause.patterns().first(), newPat);
          if (subst.isEmpty()) return null;
          return typedClause.inst(newPat.toTerm());
        }).filterNotNull();
        groups.append(filtered);
      });
    // TODO[ice]: current ctor recursion
    return Unit.unit();
  }

  /**
   * @author ice1000
   */
  public record TypedClause(
    Pat.@NotNull Clause clauses,
    Def.@NotNull Signature signature
  ) {
    public @NotNull TypedClause inst(@NotNull Term inst) {
      var clauses = new Pat.Clause(this.clauses.patterns().drop(1), this.clauses.expr());
      return new TypedClause(clauses, signature.inst(inst));
    }
  }
}
