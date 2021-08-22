// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.control.Either;
import kala.tuple.Unit;
import kala.value.Ref;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Signatured;
import org.aya.core.Matching;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.sort.LevelSubst;
import org.aya.core.sort.Sort;
import org.aya.core.term.CallTerm;
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.generic.GenericBuilder;
import org.aya.generic.Level;
import org.aya.tyck.pat.Conquer;
import org.aya.tyck.pat.PatClassifier;
import org.aya.tyck.pat.PatTycker;
import org.aya.tyck.trace.Trace;
import org.aya.util.FP;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * @author ice1000, kiva
 * @apiNote this class does not create {@link ExprTycker} instances itself,
 * but use the one passed to it. {@link StmtTycker#newTycker()} creates instances
 * of expr tyckers.
 */
public record StmtTycker(
  @NotNull Reporter reporter,
  Trace.@Nullable Builder traceBuilder
) implements Decl.Visitor<ExprTycker, Def> {
  public @NotNull ExprTycker newTycker() {
    return new ExprTycker(reporter, traceBuilder);
  }

  private void tracing(@NotNull Consumer<Trace.@NotNull Builder> consumer) {
    if (traceBuilder != null) consumer.accept(traceBuilder);
  }

  @Override public void traceEntrance(@NotNull Signatured sig, ExprTycker tycker) {
    tracing(builder -> builder.shift(new Trace.DeclT(sig.ref(), sig.sourcePos)));
    tycker.localCtx = tycker.localCtx.derive();
  }

  @Override public void traceExit(ExprTycker tycker, Def def) {
    tracing(Trace.Builder::reduce);
    var parent = tycker.localCtx.parent();
    assert parent != null;
    tycker.localCtx = parent;
  }

  @Override public PrimDef visitPrim(@NotNull Decl.PrimDecl decl, ExprTycker tycker) {
    assert tycker.localCtx.isEmpty();
    var core = decl.ref.core;
    var tele = checkTele(tycker, decl.telescope, null);
    if (tele.isNotEmpty()) {
      if (decl.result == null) {
        // TODO[ice]: Expect type and term
        throw new ExprTycker.TyckerException();
      }
      var result = decl.result.accept(tycker, null).wellTyped();
      var levelSubst = new LevelSubst.Simple(MutableMap.create());
      // Homotopy level goes first
      var levels = tycker.extractLevels();
      for (var lvl : core.levels.zip(levels))
        levelSubst.solution().put(lvl._1, new Sort.CoreLevel(new Level.Reference<>(lvl._2)));
      var target = FormTerm.Pi.make(core.telescope(), core.result())
        .subst(Substituter.TermSubst.EMPTY, levelSubst);
      tycker.unifyTyReported(FormTerm.Pi.make(tele, result), target, decl.result);
      decl.signature = new Def.Signature(levels, tele, result);
    } else if (decl.result != null) {
      var result = decl.result.accept(tycker, null).wellTyped();
      tycker.unifyTyReported(result, core.result(), decl.result);
    } else decl.signature = new Def.Signature(ImmutableSeq.empty(), core.telescope(), core.result());
    tycker.solveMetas();
    return core;
  }

  @Override public CtorDef visitCtor(Decl.@NotNull DataCtor ctor, ExprTycker tycker) {
    var dataRef = ctor.dataRef;
    var dataSig = dataRef.concrete.signature;
    assert dataSig != null;
    var dataArgs = dataSig.param().map(Term.Param::toArg);
    var sortParam = dataSig.sortParam();
    var dataCall = new CallTerm.Data(dataRef, sortParam.view()
      .map(Level.Reference::new)
      .map(Sort.CoreLevel::new)
      .toImmutableSeq(), dataArgs);
    var sig = new Ref<>(new Def.Signature(sortParam, dataSig.param(), dataCall));
    var patTycker = new PatTycker(tycker);
    var pat = patTycker.visitPatterns(sig, ctor.patterns);
    var tele = checkTele(tycker, ctor.telescope.map(param ->
      param.mapExpr(expr -> expr.accept(patTycker.subst(), Unit.unit()))), dataSig.result());
    var patSubst = patTycker.subst().clone();
    var dataParamView = dataSig.param().view();
    if (pat.isNotEmpty()) {
      var subst = dataParamView.map(Term.Param::ref)
        .zip(pat.view().map(Pat::toTerm))
        .<Var, Term>toImmutableMap();
      dataCall = (CallTerm.Data) dataCall.subst(subst);
    }
    var signature = new Def.Signature(sortParam, tele, dataCall);
    ctor.signature = signature;
    var elabClauses = patTycker.elabClauses(patSubst, signature, ctor.clauses);
    var matchings = elabClauses.flatMap(Pat.PrototypeClause::deprototypify);
    var implicits = pat.isEmpty() ? dataParamView.map(Term.Param::implicitify).toImmutableSeq() : Pat.extractTele(pat);
    var elaborated = new CtorDef(dataRef, ctor.ref, pat, implicits, tele, matchings, dataCall, ctor.coerce);
    ensureConfluent(tycker, signature, elabClauses, matchings, ctor.sourcePos, false);
    return elaborated;
  }

  private void ensureConfluent(
    ExprTycker tycker, Def.Signature signature, ImmutableSeq<Pat.PrototypeClause> elabClauses,
    ImmutableSeq<@NotNull Matching> matchings, @NotNull SourcePos pos,
    boolean coverage
  ) {
    if (!matchings.isNotEmpty()) return;
    tracing(builder -> builder.shift(new Trace.LabelT(pos, "confluence check")));
    var classification = PatClassifier.classify(elabClauses, tycker.reporter, pos, coverage);
    PatClassifier.confluence(elabClauses, tycker, pos, signature.result(), classification);
    Conquer.against(matchings, tycker, pos, signature);
    tycker.solveMetas();
    tracing(GenericBuilder::reduce);
  }

  @Override public DataDef visitData(Decl.@NotNull DataDecl decl, ExprTycker tycker) {
    var tele = checkTele(tycker, decl.telescope, null);
    final var result = tycker.checkExpr(decl.result, FormTerm.Univ.OMEGA).wellTyped();
    decl.signature = new Def.Signature(tycker.extractLevels(), tele, result);
    var body = decl.body.map(clause -> traced(clause, tycker, this::visitCtor));
    return new DataDef(decl.ref, tele, decl.signature.sortParam(), result, body);
  }

  @Override public StructDef visitStruct(Decl.@NotNull StructDecl decl, ExprTycker tycker) {
    var tele = checkTele(tycker, decl.telescope, null);
    final var result = tycker.checkExpr(decl.result, FormTerm.Univ.OMEGA).wellTyped();
    // var levelSubst = tycker.equations.solve();
    var levels = tycker.extractLevels();
    decl.signature = new Def.Signature(levels, tele, result);
    return new StructDef(decl.ref, tele, levels, result, decl.fields.map(field -> traced(field, tycker, this::visitField)));
  }

  @Override public FieldDef visitField(Decl.@NotNull StructField field, ExprTycker tycker) {
    var tele = checkTele(tycker, field.telescope, null);
    var structRef = field.structRef;
    var result = field.result.accept(tycker, null).wellTyped();
    var structSig = structRef.concrete.signature;
    assert structSig != null;
    field.signature = new Def.Signature(structSig.sortParam(), tele, result);
    var patTycker = new PatTycker(tycker);
    var elabClauses = patTycker.elabClauses(null, field.signature, field.clauses);
    var matchings = elabClauses.flatMap(Pat.PrototypeClause::deprototypify);
    var body = field.body.map(e -> e.accept(tycker, result).wellTyped());
    var elaborated = new FieldDef(structRef, field.ref, structSig.param(), tele, result, matchings, body, field.coerce);
    ensureConfluent(tycker, field.signature, elabClauses, matchings, field.sourcePos, false);
    return elaborated;
  }

  @Override public FnDef visitFn(Decl.@NotNull FnDecl decl, ExprTycker tycker) {
    tracing(builder -> builder.shift(new Trace.LabelT(decl.sourcePos, "telescope")));
    var resultTele = checkTele(tycker, decl.telescope, null);
    // It might contain unsolved holes, but that's acceptable.
    var resultRes = tycker.checkNoZonk(decl.result, null).wellTyped();
    tracing(GenericBuilder::reduce);
    var signature = new Ref<>(new Def.Signature(tycker.extractLevels(), resultTele, resultRes));
    decl.signature = signature.value;
    var patTycker = new PatTycker(tycker);
    var what = FP.distR(decl.body.map(
      left -> tycker.checkExpr(left, resultRes).toTuple(),
      right -> patTycker.elabClauses(right, signature)));
    var resultTy = what._1;
    var factory = FnDef.factory(body ->
      new FnDef(decl.ref, resultTele, signature.value.sortParam(), resultTy, body));
    if (what._2.isLeft()) return factory.apply(Either.left(what._2.getLeftValue()));
    var elabClauses = what._2.getRightValue();
    var matchings = elabClauses.flatMap(Pat.PrototypeClause::deprototypify);
    var elaborated = factory.apply(Either.right(matchings));
    ensureConfluent(tycker, signature.value, elabClauses, matchings, decl.sourcePos, true);
    return elaborated;
  }

  private @NotNull ImmutableSeq<Term.Param>
  checkTele(@NotNull ExprTycker exprTycker, @NotNull ImmutableSeq<Expr.Param> tele, @Nullable Term univ) {
    var okTele = tele.map(param -> {
      assert param.type() != null; // guaranteed by AyaProducer
      var paramRes = exprTycker.checkNoZonk(param.type(), univ);
      exprTycker.localCtx.put(param.ref(), paramRes.wellTyped());
      return new Term.Param(param.ref(), paramRes.wellTyped(), param.explicit());
    });
    exprTycker.solveMetas();
    return okTele.map(t -> {
      var term = t.type().zonk(exprTycker);
      exprTycker.localCtx.put(t.ref(), term);
      return new Term.Param(t.ref(), term, t.explicit());
    });
  }
}
