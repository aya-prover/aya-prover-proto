// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Unit;
import org.aya.api.core.CoreDef;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.DefVar;
import org.aya.concrete.stmt.Signatured;
import org.aya.core.sort.Sort;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.distill.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author ice1000
 */
public sealed interface Def extends CoreDef permits SubLevelDef, TopLevelDef {
  static @NotNull ImmutableSeq<Term.Param> defTele(@NotNull DefVar<? extends Def, ? extends Signatured> defVar) {
    if (defVar.core != null) return defVar.core.telescope();
      // guaranteed as this is already a core term
    else return Objects.requireNonNull(defVar.concrete.signature).param;
  }
  static @NotNull ImmutableSeq<Sort.LvlVar> defLevels(@NotNull DefVar<? extends Def, ? extends Signatured> defVar) {
    var core = defVar.core;
    return switch (core) {
      case TopLevelDef topLevel -> topLevel.levels;
      case CtorDef ctor -> defLevels(ctor.dataRef);
      case FieldDef field -> defLevels(field.structRef);
      // guaranteed as this is already a core term
      case null, default -> Objects.requireNonNull(defVar.concrete.signature).sortParam();
    };
  }
  static @NotNull Term defResult(@NotNull DefVar<? extends Def, ? extends Signatured> defVar) {
    if (defVar.core != null) return defVar.core.result();
      // guaranteed as this is already a core term
    else return Objects.requireNonNull(defVar.concrete.signature).result;
  }
  static @NotNull ImmutableSeq<Term.Param>
  substParams(@NotNull SeqLike<Term.@NotNull Param> param, Substituter.@NotNull TermSubst subst) {
    return param.view().drop(1).map(p -> p.subst(subst)).toImmutableSeq();
  }

  @Override @NotNull Term result();
  @Override @NotNull DefVar<? extends Def, ? extends Signatured> ref();
  @Override @NotNull ImmutableSeq<Term.Param> telescope();

  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);
  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return accept(new CoreDistiller(options), Unit.unit());
  }

  /**
   * @author re-xyr
   */
  interface Visitor<P, R> {
    R visitFn(@NotNull FnDef def, P p);
    R visitData(@NotNull DataDef def, P p);
    R visitCtor(@NotNull CtorDef def, P p);
    R visitStruct(@NotNull StructDef def, P p);
    R visitField(@NotNull FieldDef def, P p);
    R visitPrim(@NotNull PrimDef def, P p);
  }

  /**
   * Signature of a definition, used in concrete and tycking.
   *
   * @author ice1000
   */
  record Signature(
    @NotNull ImmutableSeq<Sort.@NotNull LvlVar> sortParam,
    @NotNull ImmutableSeq<Term.@NotNull Param> param,
    @NotNull Term result
  ) implements AyaDocile {
    @Contract("_ -> new") public @NotNull Signature inst(@NotNull Term term) {
      var subst = new Substituter.TermSubst(param.first().ref(), term);
      return new Signature(sortParam, substParams(param, subst), result.subst(subst));
    }

    @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.sep(param.view().map(p -> p.toDoc(options))), Doc.symbol("->"), result.toDoc(options));
    }

    @Contract("_ -> new") public @NotNull Signature mapTerm(@NotNull Term term) {
      return new Signature(sortParam, param, term);
    }

    public @NotNull Signature subst(@NotNull Substituter.TermSubst subst) {
      return new Signature(sortParam, param.map(p -> p.subst(subst)), result.subst(subst));
    }
  }
}
