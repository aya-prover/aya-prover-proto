// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

/**
 * The algorithm proposed by Cockx and Abel.
 *
 * @author ice1000
 */
public final class TreeBuilder {
  private void splitFirst(@NotNull ImmutableSeq<PatClassifier.TypedPats> clauses) {
    if (clauses.isEmpty()) return;
    var guide = clauses.first();
    var otherClasses = Buffer.<ImmutableSeq<PatClassifier.TypedPats>>of();
    guide.pats().forEachIndexed((patIx, pat) -> {
      var classification = PatClassifier.classify(pat, clauses);
      otherClasses.appendAll(classification.view().drop(1));
      splitFirst(classification.first());
    });
    otherClasses.forEach(this::splitFirst);
  }

  public void enter(@NotNull ImmutableSeq<Pat.PrototypeClause> clauses, Def.@NotNull Signature signature) {
    splitFirst(clauses.mapIndexed((index, clause) -> new PatClassifier.TypedPats(signature, index, clause)));
  }
}
