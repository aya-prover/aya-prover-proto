// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.parse;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFile;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LevelGenVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Assoc;
import org.aya.api.util.WithPos;
import org.aya.concrete.*;
import org.aya.concrete.desugar.BinOpParser;
import org.aya.concrete.resolve.error.RedefinitionError;
import org.aya.concrete.resolve.error.UnknownPrimError;
import org.aya.core.def.PrimDef;
import org.aya.generic.Modifier;
import org.aya.parser.AyaBaseVisitor;
import org.aya.parser.AyaParser;
import org.aya.util.Constants;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.LinkedBuffer;
import org.glavo.kala.collection.mutable.MutableHashSet;
import org.glavo.kala.control.Either;
import org.glavo.kala.control.Option;
import org.glavo.kala.function.BooleanFunction;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author ice1000, kiva
 */
public final class AyaProducer extends AyaBaseVisitor<Object> {
  private final @NotNull Reporter reporter;
  private final @NotNull SourceFile sourceFile;

  public AyaProducer(@NotNull SourceFile sourceFile, @NotNull Reporter reporter) {
    this.sourceFile = sourceFile;
    this.reporter = reporter;
  }

  @Override public ImmutableSeq<Stmt> visitProgram(AyaParser.ProgramContext ctx) {
    return ImmutableSeq.from(ctx.stmt()).flatMap(this::visitStmt).toImmutableSeq();
  }

  @Override public Decl.PrimDecl visitPrimDecl(AyaParser.PrimDeclContext ctx) {
    var id = ctx.ID();
    var name = id.getText();
    var core = PrimDef.PRIMITIVES.getOption(name);
    var sourcePos = sourcePosOf(id);
    if (core.isEmpty()) {
      reporter.report(new UnknownPrimError(sourcePos, name));
      throw new ParsingInterruptedException();
    }
    var type = ctx.type();
    var ref = core.get().ref();
    if (ref.concrete != null) {
      reporter.report(new RedefinitionError(RedefinitionError.Kind.Prim, name, sourcePos));
      throw new ParsingInterruptedException();
    }
    return new Decl.PrimDecl(
      sourcePos,
      visitAssoc(ctx.assoc()),
      ref,
      visitTelescope(ctx.tele()),
      type == null ? null : visitType(type)
    );
  }

  @Override public @NotNull SeqLike<Stmt> visitStmt(AyaParser.StmtContext ctx) {
    var importCmd = ctx.importCmd();
    if (importCmd != null) return ImmutableSeq.of(visitImportCmd(importCmd));
    var openCmd = ctx.openCmd();
    if (openCmd != null) return visitOpenCmd(openCmd);
    var decl = ctx.decl();
    if (decl != null) {
      var result = visitDecl(decl);
      return result._2.view().prepended(result._1);
    }
    var mod = ctx.module();
    if (mod != null) return ImmutableSeq.of(visitModule(mod));
    var levels = ctx.levels();
    if (levels != null) return ImmutableSeq.of(visitLevels(levels));
    var bind = ctx.bind();
    if (bind != null) return ImmutableSeq.of(visitBind(bind));
    return unreachable(ctx);
  }

  @Override public Generalize visitLevels(AyaParser.LevelsContext ctx) {
    var kind = ctx.HLEVEL() != null ? LevelGenVar.Kind.Homotopy : LevelGenVar.Kind.Universe;
    return new Generalize.Levels(sourcePosOf(ctx), kind, visitIds(ctx.ids())
      .map(t -> t.map(data -> new LevelGenVar(kind, data)))
      .collect(ImmutableSeq.factory()));
  }

  @Override public Generalize visitGeneralize(AyaParser.GeneralizeContext ctx) {
    var ids = visitIds(ctx.ids());
    var type = visitType(ctx.type());
    return new Generalize.Variables(
      sourcePosOf(ctx),
      ids.map(id -> new Expr.Param(id._1, new LocalVar(id._2), type, true)).collect(ImmutableSeq.factory())
    );
  }

  @Override public Stmt.@NotNull BindStmt visitBind(AyaParser.BindContext ctx) {
    return new Stmt.BindStmt(
      sourcePosOf(ctx),
      visitQualifiedId(ctx.qualifiedId(0)),
      visitBindPred(ctx.bindPred()),
      visitQualifiedId(ctx.qualifiedId(1)),
      new Ref<>(null),
      new Ref<>(null),
      new Ref<>(null)
    );
  }

  @Override public Stmt.@NotNull BindPred visitBindPred(AyaParser.BindPredContext ctx) {
    if (ctx.TIGHTER() != null) return Stmt.BindPred.Tighter;
    if (ctx.LOOSER() != null) return Stmt.BindPred.Looser;
    return unreachable(ctx);
  }

  private <T> T unreachable(ParserRuleContext ctx) {
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override public @NotNull Tuple2<Decl, ImmutableSeq<Stmt>> visitDecl(AyaParser.DeclContext ctx) {
    var accessibility = ctx.PRIVATE() == null ? Stmt.Accessibility.Public : Stmt.Accessibility.Private;
    var fnDecl = ctx.fnDecl();
    if (fnDecl != null) return Tuple.of(visitFnDecl(fnDecl, accessibility), ImmutableSeq.of());
    var dataDecl = ctx.dataDecl();
    if (dataDecl != null) return visitDataDecl(dataDecl, accessibility);
    var structDecl = ctx.structDecl();
    if (structDecl != null) return Tuple.of(visitStructDecl(structDecl, accessibility), ImmutableSeq.of());
    var primDecl = ctx.primDecl();
    if (primDecl != null) return Tuple.of(visitPrimDecl(primDecl), ImmutableSeq.of());
    return unreachable(ctx);
  }

  public Decl.@NotNull FnDecl visitFnDecl(AyaParser.FnDeclContext ctx, Stmt.Accessibility accessibility) {
    var modifiers = ctx.fnModifiers().stream()
      .map(this::visitFnModifiers)
      .distinct()
      .collect(Collectors.toCollection(() -> EnumSet.noneOf(Modifier.class)));
    var assocCtx = ctx.assoc();
    var abuseCtx = ctx.abuse();

    return new Decl.FnDecl(
      sourcePosOf(ctx.ID()),
      accessibility,
      modifiers,
      visitAssoc(assocCtx),
      ctx.ID().getText(),
      visitTelescope(ctx.tele()),
      type(ctx.type(), sourcePosOf(ctx)),
      visitFnBody(ctx.fnBody()),
      abuseCtx == null ? ImmutableSeq.of() : visitAbuse(abuseCtx)
    );
  }

  public @NotNull ImmutableSeq<Expr.@NotNull Param> visitTelescope(List<AyaParser.TeleContext> telescope) {
    return ImmutableSeq.from(telescope).flatMap(t -> visitTele(t, false));
  }

  public @NotNull ImmutableSeq<Expr.@NotNull Param> visitLamTelescope(List<AyaParser.TeleContext> telescope) {
    return ImmutableSeq.from(telescope).flatMap(t -> visitTele(t, true));
  }

  @Override public @NotNull ImmutableSeq<@NotNull Stmt> visitAbuse(AyaParser.AbuseContext ctx) {
    return ImmutableSeq.from(ctx.stmt()).flatMap(this::visitStmt);
  }

  @Override public @NotNull Either<Expr, ImmutableSeq<Pattern.Clause>> visitFnBody(AyaParser.FnBodyContext ctx) {
    var expr = ctx.expr();
    if (expr != null) return Either.left(visitExpr(expr));
    return Either.right(ImmutableSeq.from(ctx.clause()).map(this::visitClause));
  }

  @Override public QualifiedID visitQualifiedId(AyaParser.QualifiedIdContext ctx) {
    return new QualifiedID(sourcePosOf(ctx),
      ctx.ID().stream().map(ParseTree::getText)
        .collect(ImmutableSeq.factory()));
  }

  @Override public @NotNull Expr visitLiteral(AyaParser.LiteralContext ctx) {
    var pos = sourcePosOf(ctx);
    if (ctx.CALM_FACE() != null) return new Expr.HoleExpr(pos, false, null);
    var id = ctx.qualifiedId();
    if (id != null) return new Expr.UnresolvedExpr(pos, visitQualifiedId(id));
    if (ctx.TYPE() != null)
      return new Expr.RawUnivExpr(pos, Expr.RawUnivExpr.POLYMORPHIC, Expr.RawUnivExpr.POLYMORPHIC);
    if (ctx.HTYPE() != null) return new Expr.RawUnivExpr(pos, Expr.RawUnivExpr.POLYMORPHIC, Expr.RawUnivExpr.NEEDED);
    if (ctx.UTYPE() != null) return new Expr.RawUnivExpr(pos, Expr.RawUnivExpr.NEEDED, Expr.RawUnivExpr.POLYMORPHIC);
    if (ctx.SET_UNIV() != null) return new Expr.RawUnivExpr(pos, Expr.RawUnivExpr.POLYMORPHIC, 2);
    if (ctx.INF_TYPE() != null)
      return new Expr.RawUnivExpr(pos, Expr.RawUnivExpr.POLYMORPHIC, Expr.RawUnivExpr.INFINITY);
    if (ctx.PROP() != null) return new Expr.RawUnivExpr(pos, 0, 1);
    if (ctx.LGOAL() != null) {
      var fillingExpr = ctx.expr();
      var filling = fillingExpr == null ? null : visitExpr(fillingExpr);
      return new Expr.HoleExpr(pos, true, filling);
    }
    var number = ctx.NUMBER();
    if (number != null) return new Expr.LitIntExpr(pos, Integer.parseInt(number.getText()));
    var string = ctx.STRING();
    if (string != null) return new Expr.LitStringExpr(pos, string.getText());
    return unreachable(ctx);
  }

  public int visitOptNumber(@Nullable TerminalNode number, int defaultVal) {
    return Option.of(number)
      .map(ParseTree::getText)
      .map(Integer::parseInt)
      .getOrDefault(defaultVal);
  }

  private @NotNull LocalVar visitParamLiteral(AyaParser.LiteralContext ctx) {
    var idCtx = ctx.qualifiedId();
    if (idCtx == null) {
      reporter.report(new ParseError(sourcePosOf(ctx),
        "`" + ctx.getText() + "` is not a parameter name"));
      throw new ParsingInterruptedException();
    }
    var id = visitQualifiedId(idCtx);
    if (id.isQualified()) {
      reporter.report(new ParseError(sourcePosOf(ctx),
        "parameter name `" + ctx.getText() + "` should not be qualified"));
      throw new ParsingInterruptedException();
    }
    return new LocalVar(id.justName(), sourcePosOf(idCtx));
  }

  public @NotNull ImmutableSeq<Expr.@NotNull Param> visitTele(AyaParser.TeleContext ctx, boolean isLamTele) {
    var literal = ctx.literal();
    if (literal != null) return ImmutableSeq.of(isLamTele
      ? new Expr.Param(sourcePosOf(ctx), visitParamLiteral(literal), type(null, sourcePosOf(ctx)), true)
      : new Expr.Param(sourcePosOf(ctx), Constants.anonymous(), visitLiteral(literal), true)
    );
    var teleBinder = ctx.teleBinder();
    var teleMaybeTypedExpr = ctx.teleMaybeTypedExpr();
    if (teleBinder != null) {
      var type = teleBinder.expr();
      if (type != null)
        return ImmutableSeq.of(new Expr.Param(sourcePosOf(ctx), Constants.anonymous(), visitExpr(type), true));
      teleMaybeTypedExpr = teleBinder.teleMaybeTypedExpr();
    }
    if (ctx.LPAREN() != null) return visitTeleMaybeTypedExpr(teleMaybeTypedExpr).apply(true);
    if (ctx.LBRACE() != null) return visitTeleMaybeTypedExpr(teleMaybeTypedExpr).apply(false);
    return unreachable(ctx);
  }

  @Override
  public @NotNull Function<Boolean, ImmutableSeq<Expr.Param>> visitTeleMaybeTypedExpr(AyaParser.TeleMaybeTypedExprContext ctx) {
    var type = type(ctx.type(), sourcePosOf(ctx.ids()));
    return explicit -> visitIds(ctx.ids())
      .map(v -> new Expr.Param(v.sourcePos(), WithPos.toVar(v), type, explicit))
      .collect(ImmutableSeq.factory());
  }

  public @NotNull Expr visitExpr(AyaParser.ExprContext ctx) {
    if (ctx instanceof AyaParser.SingleContext sin) return visitAtom(sin.atom());
    if (ctx instanceof AyaParser.AppContext app) return visitApp(app);
    if (ctx instanceof AyaParser.ProjContext proj) return visitProj(proj);
    if (ctx instanceof AyaParser.PiContext pi) return visitPi(pi);
    if (ctx instanceof AyaParser.SigmaContext sig) return visitSigma(sig);
    if (ctx instanceof AyaParser.LamContext lam) return visitLam(lam);
    if (ctx instanceof AyaParser.ArrContext arr) return visitArr(arr);
    if (ctx instanceof AyaParser.NewContext n) return visitNew(n);
    if (ctx instanceof AyaParser.LsucContext lsuc) return visitLsuc(lsuc);
    if (ctx instanceof AyaParser.LmaxContext lmax) return visitLmax(lmax);
    // TODO: match
    throw new UnsupportedOperationException("TODO: " + ctx.getClass());
  }

  @Override public @NotNull Expr visitLsuc(AyaParser.LsucContext ctx) {
    return new Expr.LSucExpr(sourcePosOf(ctx), visitExpr(ctx.expr()));
  }

  @Override public @NotNull Expr visitLmax(AyaParser.LmaxContext ctx) {
    return new Expr.LMaxExpr(sourcePosOf(ctx), ImmutableSeq.from(ctx.expr()).map(this::visitExpr));
  }

  @Override public @NotNull Expr visitNew(AyaParser.NewContext ctx) {
    return new Expr.NewExpr(
      sourcePosOf(ctx),
      visitExpr(ctx.expr()),
      ImmutableSeq.from(ctx.newArg())
        .map(na -> new Expr.Field(na.ID().getText(), visitIds(na.ids())
          .map(t -> new WithPos<>(t.sourcePos(), WithPos.toVar(t)))
          .collect(ImmutableSeq.factory()), visitExpr(na.expr())))
    );
  }

  @Override public @NotNull Expr visitArr(AyaParser.ArrContext ctx) {
    var from = visitExpr(ctx.expr(0));
    var to = visitExpr(ctx.expr(1));
    return new Expr.PiExpr(
      sourcePosOf(ctx),
      false,
      new Expr.Param(sourcePosOf(ctx.expr(0)), Constants.anonymous(), from, true),
      to
    );
  }

  @Override public @NotNull Expr visitApp(AyaParser.AppContext ctx) {
    var head = new BinOpParser.Elem(null, visitExpr(ctx.expr()), true);
    var tail = ctx.argument().stream()
      .map(this::visitArgument)
      .collect(LinkedBuffer.factory());
    tail.push(head);
    return new Expr.BinOpSeq(sourcePosOf(ctx), tail.toImmutableSeq());
  }

  @Override public @NotNull Expr visitAtom(AyaParser.AtomContext ctx) {
    var literal = ctx.literal();
    if (literal != null) return visitLiteral(literal);

    final var expr = ctx.expr();
    if (expr.size() == 1) return visitExpr(expr.get(0));
    return new Expr.TupExpr(
      sourcePosOf(ctx),
      expr.stream()
        .map(this::visitExpr)
        .collect(ImmutableSeq.factory())
    );
  }

  @Override public @NotNull BinOpParser.Elem visitArgument(AyaParser.ArgumentContext ctx) {
    var atom = ctx.atom();
    if (atom != null) {
      var fixes = ctx.projFix();
      var expr = visitAtom(atom);
      var projected = ImmutableSeq.from(fixes)
        .foldLeft(Tuple.of(sourcePosOf(ctx), expr),
          (acc, proj) -> Tuple.of(acc._2.sourcePos(), buildProj(acc._1, acc._2, proj)))
        ._2;
      return new BinOpParser.Elem(projected, true);
    }
    if (ctx.LBRACE() != null) {
      var items = ctx.expr().stream()
        .map(this::visitExpr)
        .collect(ImmutableSeq.factory());
      var id = ctx.ID();
      var name = id != null ? id.getText() : null;
      if (items.sizeEquals(1)) return new BinOpParser.Elem(name, items.first(), false);
      var tupExpr = new Expr.TupExpr(sourcePosOf(ctx), items);
      return new BinOpParser.Elem(tupExpr, false);
    }
    throw new UnsupportedOperationException(ctx.getClass().getName());
  }

  @Override public Expr.@NotNull LamExpr visitLam(AyaParser.LamContext ctx) {
    return (Expr.LamExpr) buildLam(
      sourcePosOf(ctx),
      visitLamTelescope(ctx.tele()).view(),
      visitLamBody(ctx)
    );
  }

  public static @NotNull Expr buildLam(
    SourcePos sourcePos,
    SeqLike<Expr.Param> params,
    Expr body
  ) {
    if (params.isEmpty()) return body;
    return new Expr.LamExpr(
      sourcePos,
      params.first(),
      buildLam(sourcePosForSubExpr(sourcePos.file(), params, body), params.view().drop(1), body)
    );
  }

  private @NotNull Expr visitLamBody(@NotNull AyaParser.LamContext ctx) {
    var bodyExpr = ctx.expr();

    if (bodyExpr == null) {
      var impliesToken = ctx.IMPLIES();
      var bodyHolePos = impliesToken == null
        ? sourcePosOf(ctx)
        : sourcePosOf(impliesToken);

      return new Expr.HoleExpr(bodyHolePos, false, null);
    }

    return visitExpr(bodyExpr);
  }

  @Override public Expr.@NotNull SigmaExpr visitSigma(AyaParser.SigmaContext ctx) {
    return new Expr.SigmaExpr(
      sourcePosOf(ctx),
      false,
      visitTelescope(ctx.tele()).appended(new Expr.Param(
        visitExpr(ctx.expr()).sourcePos(),
        Constants.anonymous(),
        visitExpr(ctx.expr()),
        true))
    );
  }

  @Override public Expr.@NotNull PiExpr visitPi(AyaParser.PiContext ctx) {
    return (Expr.PiExpr) buildPi(
      sourcePosOf(ctx),
      false,
      visitTelescope(ctx.tele()).view(),
      visitExpr(ctx.expr())
    );
  }

  public static @NotNull Expr buildPi(
    SourcePos sourcePos,
    boolean co,
    SeqLike<Expr.Param> params,
    Expr body
  ) {
    if (params.isEmpty()) return body;
    var first = params.first();
    return new Expr.PiExpr(
      sourcePos,
      co,
      first,
      buildPi(sourcePosForSubExpr(sourcePos.file(), params, body), co, params.view().drop(1), body)
    );
  }

  @NotNull
  private static SourcePos sourcePosForSubExpr(@NotNull SourceFile sourceFile, SeqLike<Expr.Param> params, Expr body) {
    var restParamSourcePos = params.stream().skip(1)
      .map(Expr.Param::sourcePos)
      .reduce(SourcePos.NONE, (acc, it) -> {
        if (acc == SourcePos.NONE) return it;
        return new SourcePos(sourceFile, acc.tokenStartIndex(), it.tokenEndIndex(),
          acc.startLine(), acc.startColumn(), it.endLine(), it.endColumn());
      });
    var bodySourcePos = body.sourcePos();
    return new SourcePos(
      sourceFile,
      restParamSourcePos.tokenStartIndex(),
      bodySourcePos.tokenEndIndex(),
      restParamSourcePos.startLine(),
      restParamSourcePos.startColumn(),
      bodySourcePos.endLine(),
      bodySourcePos.endColumn()
    );
  }

  @Override public Expr.@NotNull ProjExpr visitProj(AyaParser.ProjContext proj) {
    return buildProj(sourcePosOf(proj), visitExpr(proj.expr()), proj.projFix());
  }

  private Expr.@NotNull ProjExpr buildProj(@NotNull SourcePos sourcePos,
                                           @NotNull Expr projectee,
                                           @NotNull AyaParser.ProjFixContext fix) {
    var number = fix.NUMBER();
    return new Expr.ProjExpr(
      sourcePos,
      projectee,
      number != null
        ? Either.left(Integer.parseInt(number.getText()))
        : Either.right(new WithPos<>(sourcePosOf(fix), fix.ID().getText())),
      new Ref<>(null)
    );
  }

  public @NotNull Tuple2<Decl, ImmutableSeq<Stmt>> visitDataDecl(AyaParser.DataDeclContext ctx, Stmt.Accessibility accessibility) {
    var abuseCtx = ctx.abuse();
    var openAccessibility = ctx.PUBLIC() != null ? Stmt.Accessibility.Public : Stmt.Accessibility.Private;
    var body = ctx.dataBody().stream().map(this::visitDataBody).collect(ImmutableSeq.factory());
    checkRedefinition(RedefinitionError.Kind.Ctor,
      body.view().map(ctor -> new WithPos<>(ctor.sourcePos, ctor.ref.name())));
    var data = new Decl.DataDecl(
      sourcePosOf(ctx.ID()),
      accessibility,
      visitAssoc(ctx.assoc()),
      ctx.ID().getText(),
      visitTelescope(ctx.tele()),
      type(ctx.type(), sourcePosOf(ctx)),
      body,
      abuseCtx == null ? ImmutableSeq.of() : visitAbuse(abuseCtx)
    );
    return Tuple2.of(data, ctx.OPEN() == null ? ImmutableSeq.of() : ImmutableSeq.of(
      new Stmt.OpenStmt(
        sourcePosOf(ctx.ID()),
        openAccessibility,
        ImmutableSeq.of(ctx.ID().getText()),
        Stmt.OpenStmt.UseHide.EMPTY
      )
    ));
  }

  public @NotNull Expr type(@Nullable AyaParser.TypeContext typeCtx, SourcePos sourcePos) {
    return typeCtx == null
      ? new Expr.HoleExpr(sourcePos, false, null)
      : visitType(typeCtx);
  }

  private @NotNull Decl.DataCtor visitDataBody(AyaParser.DataBodyContext ctx) {
    if (ctx instanceof AyaParser.DataCtorsContext dcc) return visitDataCtor(ImmutableSeq.empty(), dcc.dataCtor());
    if (ctx instanceof AyaParser.DataClausesContext dcc) return visitDataCtorClause(dcc.dataCtorClause());
    return unreachable(ctx);
  }

  public Decl.DataCtor visitDataCtor(@NotNull ImmutableSeq<Pattern> patterns, AyaParser.DataCtorContext ctx) {
    var telescope = visitTelescope(ctx.tele());
    var id = ctx.ID();

    return new Decl.DataCtor(
      sourcePosOf(id),
      visitAssoc(ctx.assoc()),
      id.getText(),
      telescope,
      visitClauses(ctx.clauses()),
      patterns,
      ctx.COERCE() != null
    );
  }

  @Override public ImmutableSeq<Pattern.Clause> visitClauses(@Nullable AyaParser.ClausesContext ctx) {
    if (ctx == null) return ImmutableSeq.empty();
    return ImmutableSeq.from(ctx.clause()).map(this::visitClause);
  }

  @Override public @NotNull Decl.DataCtor visitDataCtorClause(AyaParser.DataCtorClauseContext ctx) {
    return visitDataCtor(visitPatterns(ctx.patterns()), ctx.dataCtor());
  }

  @Override public @NotNull Pattern visitPattern(AyaParser.PatternContext ctx) {
    return visitAtomPatterns(ctx.atomPatterns()).apply(true, null);
  }

  @Override
  public BiFunction<Boolean, LocalVar, Pattern> visitAtomPatterns(@NotNull AyaParser.AtomPatternsContext ctx) {
    var atoms = ctx.atomPattern().stream()
      .map(this::visitAtomPattern)
      .collect(ImmutableSeq.factory());
    if (atoms.sizeEquals(1)) return (ex, as) -> atoms.first().apply(ex);

    // this apply does nothing on explicitness because we only used its bind
    var first = atoms.first().apply(true);
    if (!(first instanceof Pattern.Bind bind)) {
      reporter.report(new ParseError(first.sourcePos(),
        "`" + first.toDoc().debugRender() + "` is not a constructor name"));
      throw new ParsingInterruptedException();
    }
    return (ex, as) -> new Pattern.Ctor(
      sourcePosOf(ctx),
      ex,
      new WithPos<>(bind.sourcePos(), bind.bind().name()),
      atoms.view().drop(1).map(p -> p.apply(true)).collect(ImmutableSeq.factory()),
      as,
      new Ref<>(null)
    );
  }

  @Override public @NotNull BooleanFunction<Pattern> visitAtomPattern(AyaParser.AtomPatternContext ctx) {
    var sourcePos = sourcePosOf(ctx);
    if (ctx.LPAREN() != null || ctx.LBRACE() != null) {
      var forceEx = ctx.LPAREN() != null;
      var id = ctx.ID();
      var as = id != null ? new LocalVar(id.getText(), sourcePosOf(id)) : null;
      var tupElem = ctx.patterns().pattern().stream()
        .map(t -> visitAtomPatterns(t.atomPatterns()))
        .collect(ImmutableSeq.factory());
      return tupElem.sizeEquals(1)
        ? (exIgnored -> tupElem.first().apply(forceEx, as))
        : (exIgnored -> new Pattern.Tuple(
        sourcePos,
        forceEx,
        tupElem.map(p -> p.apply(true, null)),
        as));
    }
    if (ctx.CALM_FACE() != null) return ex -> new Pattern.CalmFace(sourcePos, ex);
    var number = ctx.NUMBER();
    if (number != null) return ex -> new Pattern.Number(sourcePos, ex, Integer.parseInt(number.getText()));
    var id = ctx.ID();
    if (id != null) return ex -> new Pattern.Bind(sourcePos, ex, new LocalVar(id.getText(), sourcePosOf(id)), new Ref<>());
    if (ctx.ABSURD() != null) return ex -> new Pattern.Absurd(sourcePos, ex);

    return unreachable(ctx);
  }

  @Override public @NotNull ImmutableSeq<Pattern> visitPatterns(AyaParser.PatternsContext ctx) {
    return ctx.pattern().stream()
      .map(this::visitPattern)
      .collect(ImmutableSeq.factory());
  }

  @Override public @NotNull Pattern.Clause visitClause(AyaParser.ClauseContext ctx) {
    return new Pattern.Clause(sourcePosOf(ctx), visitPatterns(ctx.patterns()),
      Option.of(ctx.expr()).map(this::visitExpr));
  }

  private void checkRedefinition(@NotNull RedefinitionError.Kind kind,
                                 @NotNull SeqLike<WithPos<String>> names) {
    var set = MutableHashSet.<String>of();
    var redefs = names.view().filterNot(n -> set.add(n.data())).toImmutableSeq();
    if (redefs.isNotEmpty()) {
      var last = redefs.last();
      reporter.report(new RedefinitionError(kind, last.data(), last.sourcePos()));
      throw new ParsingInterruptedException();
    }
  }

  public @NotNull Decl.StructDecl visitStructDecl(AyaParser.StructDeclContext ctx, Stmt.Accessibility accessibility) {
    var abuseCtx = ctx.abuse();
    var id = ctx.ID();
    var fields = visitFields(ctx.field());
    checkRedefinition(RedefinitionError.Kind.Field,
      fields.view().map(field -> new WithPos<>(field.sourcePos, field.ref.name())));
    return new Decl.StructDecl(
      sourcePosOf(id),
      accessibility,
      visitAssoc(ctx.assoc()),
      id.getText(),
      visitTelescope(ctx.tele()),
      type(ctx.type(), sourcePosOf(ctx)),
      // ctx.ids(),
      fields,
      abuseCtx == null ? ImmutableSeq.of() : visitAbuse(abuseCtx)
    );
  }

  private ImmutableSeq<Decl.StructField> visitFields(List<AyaParser.FieldContext> field) {
    return ImmutableSeq.from(field).map(fieldCtx -> {
      if (fieldCtx instanceof AyaParser.FieldDeclContext fieldDecl) return visitFieldDecl(fieldDecl);
      else if (fieldCtx instanceof AyaParser.FieldImplContext fieldImpl) return visitFieldImpl(fieldImpl);
      else throw new IllegalArgumentException(fieldCtx.getClass() + " is neither FieldDecl nor FieldImpl!");
    });
  }

  @Override public Decl.StructField visitFieldImpl(AyaParser.FieldImplContext ctx) {
    var telescope = visitTelescope(ctx.tele());
    var id = ctx.ID();
    return new Decl.StructField(
      sourcePosOf(id),
      id.getText(),
      telescope,
      type(ctx.type(), sourcePosOf(ctx)),
      Option.of(ctx.expr()).map(this::visitExpr),
      ImmutableSeq.empty(),
      false
    );
  }

  @Override public Decl.StructField visitFieldDecl(AyaParser.FieldDeclContext ctx) {
    var telescope = visitTelescope(ctx.tele());
    var id = ctx.ID();
    return new Decl.StructField(
      sourcePosOf(id),
      id.getText(),
      telescope,
      type(ctx.type(), sourcePosOf(ctx)),
      Option.none(),
      visitClauses(ctx.clauses()),
      ctx.COERCE() != null
    );
  }

  @Override public @NotNull Expr visitType(@NotNull AyaParser.TypeContext ctx) {
    return visitExpr(ctx.expr());
  }

  @Override public @NotNull Stmt visitImportCmd(AyaParser.ImportCmdContext ctx) {
    final var id = ctx.ID();
    return new Stmt.ImportStmt(
      sourcePosOf(ctx.moduleName()),
      visitModuleName(ctx.moduleName()),
      id == null ? null : id.getText()
    );
  }

  @Override public @NotNull ImmutableSeq<Stmt> visitOpenCmd(AyaParser.OpenCmdContext ctx) {
    var accessibility = ctx.PUBLIC() == null
      ? Stmt.Accessibility.Private
      : Stmt.Accessibility.Public;
    var useHide = ctx.useHide();
    var modNameCtx = ctx.moduleName();
    var namePos = sourcePosOf(modNameCtx);
    var modName = visitModuleName(modNameCtx);
    var open = new Stmt.OpenStmt(
      namePos,
      accessibility,
      modName,
      useHide != null ? visitUseHide(useHide) : Stmt.OpenStmt.UseHide.EMPTY
    );
    if (ctx.IMPORT() != null) return ImmutableSeq.of(
      new Stmt.ImportStmt(namePos, modName, null),
      open
    );
    else return ImmutableSeq.of(open);
  }

  public Stmt.OpenStmt.UseHide visitUse(List<AyaParser.UseContext> ctxs) {
    return new Stmt.OpenStmt.UseHide(
      ctxs.stream()
        .map(AyaParser.UseContext::useHideList)
        .map(AyaParser.UseHideListContext::idsComma)
        .flatMap(this::visitIdsComma)
        .collect(ImmutableSeq.factory()),
      Stmt.OpenStmt.UseHide.Strategy.Using);
  }

  public Stmt.OpenStmt.UseHide visitHide(List<AyaParser.HideContext> ctxs) {
    return new Stmt.OpenStmt.UseHide(
      ctxs.stream()
        .map(AyaParser.HideContext::useHideList)
        .map(AyaParser.UseHideListContext::idsComma)
        .flatMap(this::visitIdsComma)
        .collect(ImmutableSeq.factory()),
      Stmt.OpenStmt.UseHide.Strategy.Hiding);
  }

  @Override public @NotNull Stmt.OpenStmt.UseHide visitUseHide(@NotNull AyaParser.UseHideContext ctx) {
    var use = ctx.use();
    if (use != null) return visitUse(use);
    return visitHide(ctx.hide());
  }

  @Override public @NotNull Stmt.ModuleStmt visitModule(AyaParser.ModuleContext ctx) {
    return new Stmt.ModuleStmt(
      sourcePosOf(ctx.ID()),
      ctx.ID().getText(),
      ImmutableSeq.from(ctx.stmt()).flatMap(this::visitStmt)
    );
  }

  @Override public @NotNull Stream<WithPos<String>> visitIds(AyaParser.IdsContext ctx) {
    return ctx.ID().stream().map(id -> new WithPos<>(sourcePosOf(id), id.getText()));
  }

  @Override public @NotNull Stream<String> visitIdsComma(AyaParser.IdsCommaContext ctx) {
    return ctx.ID().stream().map(ParseTree::getText);
  }

  @Override public @NotNull ImmutableSeq<@NotNull String> visitModuleName(AyaParser.ModuleNameContext ctx) {
    return ImmutableSeq.from(ctx.ID()).map(ParseTree::getText);
  }

  @Override public @Nullable Tuple2<@Nullable String, @NotNull Assoc> visitAssoc(@Nullable AyaParser.AssocContext ctx) {
    if (ctx == null) return null;
    if (ctx.FIX() != null) return Tuple.of(null, Assoc.Fix);
    if (ctx.FIXL() != null) return Tuple.of(null, Assoc.FixL);
    if (ctx.FIXR() != null) return Tuple.of(null, Assoc.FixR);
    if (ctx.INFIX() != null) return Tuple.of(ctx.INFIX().getText().replace("`", ""), Assoc.Infix);
    if (ctx.INFIXL() != null) return Tuple.of(null, Assoc.InfixL);
    if (ctx.INFIXR() != null) return Tuple.of(null, Assoc.InfixR);
    if (ctx.TWIN() != null) return Tuple.of(null, Assoc.Twin);
    return unreachable(ctx);
  }

  @Override public @NotNull Modifier visitFnModifiers(AyaParser.FnModifiersContext ctx) {
    if (ctx.ERASE() != null) return Modifier.Erase;
    if (ctx.INLINE() != null) return Modifier.Inline;
    return unreachable(ctx);
  }

  private @NotNull SourcePos sourcePosOf(ParserRuleContext ctx) {
    var start = ctx.getStart();
    var end = ctx.getStop();
    return new SourcePos(
      sourceFile,
      start.getStartIndex(),
      end.getStopIndex(),
      start.getLine(),
      start.getCharPositionInLine(),
      end.getLine(),
      end.getCharPositionInLine() + end.getText().length() - 1
    );
  }

  private @NotNull SourcePos sourcePosOf(TerminalNode node) {
    var token = node.getSymbol();
    var line = token.getLine();
    return new SourcePos(
      sourceFile,
      token.getStartIndex(),
      token.getStopIndex(),
      line,
      token.getCharPositionInLine(),
      line,
      token.getCharPositionInLine() + token.getText().length() - 1
    );
  }
}
