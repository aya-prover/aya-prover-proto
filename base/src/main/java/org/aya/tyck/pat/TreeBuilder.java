// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

/**
 * The algorithm proposed by Cockx and Abel.
 *
 * @author ice1000
 */
public final class TreeBuilder {
  private void splitFirst(@NotNull ImmutableSeq<Pat.Clause> clauses, Def.@NotNull Signature signature) {
    if (clauses.isEmpty()) return;
    var guide = clauses.first();
    guide.patterns().forEachIndexed((patIx, pat) -> {
      var totalPats = clauses.view().mapIndexed((clauseIx, clause) ->
        new PatClassifier.TypedPats(clause.patterns().view(), clauseIx, signature.param()));
      var param = signature.param().get(patIx);
      var classification = PatClassifier.classify(pat, totalPats, param.type());

    });
  }
}
