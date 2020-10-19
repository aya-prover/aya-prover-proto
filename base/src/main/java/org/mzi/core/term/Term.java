package org.mzi.core.term;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.core.term.CoreTerm;
import org.mzi.core.subst.TermSubst;
import org.mzi.core.visitor.SubstVisitor;
import org.mzi.util.Decision;

/**
 * @author ice1000
 */
public interface Term extends CoreTerm {
  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);
  @Contract(pure = true) @NotNull Decision whnf();

  default @NotNull Term subst(@NotNull TermSubst subst) {
    return accept(new SubstVisitor(subst), null);
  }

  interface Visitor<P, R> {
    R visitRef(@NotNull RefTerm term, P p);
    R visitLam(@NotNull LamTerm term, P p);
    R visitPi(@NotNull PiTerm term, P p);
    R visitUniv(@NotNull UnivTerm term, P p);
    R visitApp(@NotNull AppTerm.Apply term, P p);
  }
}
