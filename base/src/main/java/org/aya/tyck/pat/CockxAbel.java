// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.core.pat.Pat;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

/**
 * The algorithm proposed by Cockx and Abel.
 *
 * @author ice1000
 */
public final class CockxAbel {
  public static void splitFirst(@NotNull SeqLike<PatClassifier.TypedPats> clauses) {
    if (clauses.isEmpty()) return;
    var guide = clauses.first();
    var otherClasses = Buffer.<ImmutableSeq<PatClassifier.TypedPats>>of();
    guide.pats().forEach(pat -> {
      var classification = pat.accept(PatClassifier.INSTANCE, clauses);
      splitFirst(classification.first());
      otherClasses.appendAll(classification.view().drop(1));
    });
    otherClasses.forEach(CockxAbel::splitFirst);
  }

  public void enter(@NotNull ImmutableSeq<Pat.PrototypeClause> clauses) {
    splitFirst(clauses.mapIndexed(PatClassifier.TypedPats::new));
  }
}
