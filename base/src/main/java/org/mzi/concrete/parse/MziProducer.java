// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.parse;

import asia.kala.Tuple;
import asia.kala.Tuple2;
import asia.kala.collection.immutable.ImmutableList;
import asia.kala.collection.mutable.Buffer;
import org.antlr.v4.runtime.ParserRuleContext;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.SourcePos;
import org.mzi.api.ref.Var;
import org.mzi.concrete.Decl;
import org.mzi.concrete.Expr;
import org.mzi.concrete.Param;
import org.mzi.concrete.Stmt;
import org.mzi.concrete.Stmt.CmdStmt.Cmd;
import org.mzi.generic.Assoc;
import org.mzi.generic.Modifier;
import org.mzi.parser.MziBaseVisitor;
import org.mzi.parser.MziParser;
import org.mzi.ref.LocalVar;

import java.util.EnumSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author ice1000
 */
public class MziProducer extends MziBaseVisitor<Object> {
  public enum UseHide {
    Use, Hide,
  }

  @Override
  public Stmt visitStmt(MziParser.StmtContext ctx) {
    var cmd = ctx.cmd();
    if (cmd != null) return visitCmd(cmd);
    var decl = ctx.decl();
    if (decl != null) return visitDecl(decl);
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public Decl visitDecl(MziParser.DeclContext ctx) {
    var fnDecl = ctx.fnDecl();
    if (fnDecl != null) return visitFnDecl(fnDecl);
    var dataDecl = ctx.dataDecl();
    if (dataDecl != null) return visitDataDecl(dataDecl);
    var structDecl = ctx.structDecl();
    if (structDecl != null) return visitStructDecl(structDecl);
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public Decl visitFnDecl(MziParser.FnDeclContext ctx) {
    var modifiers = ctx.fnModifiers().stream()
      .map(this::visitFnModifiers)
      .distinct()
      .collect(Collectors.toCollection(() -> EnumSet.noneOf(Modifier.class)));
    var assocCtx = ctx.assoc();
    var assoc = assocCtx == null
      ? null
      : visitAssoc(assocCtx);
    var tele = ctx.tele().stream().map(this::parseTele).collect(Buffer.factory());
    var typeCtx = ctx.type();
    var type = typeCtx == null
      ? new Expr.HoleExpr(sourcePosOf(ctx), null, null) // TODO: is that correct to use HoleExpr?
      : visitType(typeCtx);
    var abuseCtx = ctx.abuse();
    var abuse = abuseCtx == null ? Buffer.<Stmt>of() : visitAbuse(abuseCtx);

    return new Decl.FnDecl(
      sourcePosOf(ctx),
      modifiers,
      assoc,
      ctx.ID().getText(),
      tele,
      type,
      visitFnBody(ctx.fnBody()),
      abuse
    );
  }

  @Override
  public Buffer<Stmt> visitAbuse(MziParser.AbuseContext ctx) {
    return ctx.stmt().stream()
      .map(this::visitStmt)
      .collect(Buffer.factory());
  }

  @Override
  public Expr visitFnBody(MziParser.FnBodyContext ctx) {
    return visitExpr(ctx.expr());
  }

  @Override
  public Expr visitLiteral(MziParser.LiteralContext ctx) {
    if (ctx.CALM_FACE() != null) return new Expr.HoleExpr(sourcePosOf(ctx), "_", null);
    throw new IllegalArgumentException("TODO");
  }

  public Param parseTele(MziParser.TeleContext ctx) {
    var literal = ctx.literal();
    if (literal != null) return new Param(sourcePosOf(ctx), Buffer.of(new LocalVar("_")), visitLiteral(literal), true);
    var teleTypedExpr = ctx.teleTypedExpr();
    if (ctx.LPAREN() != null) return visitTeleTypedExpr(teleTypedExpr).apply(true);
    assert ctx.LBRACE() != null;
    return visitTeleTypedExpr(teleTypedExpr).apply(false);
  }

  @Override
  public Function<Boolean, Param> visitTeleTypedExpr(MziParser.TeleTypedExprContext ctx) {
    var type = visitType(ctx.type());
    return explicit -> new Param(sourcePosOf(ctx), visitIds(ctx.ids())
      .<Var>map(LocalVar::new)
      .collect(Buffer.factory()), type, explicit);
  }

  public Expr visitExpr(MziParser.ExprContext ctx) {
    if (ctx instanceof MziParser.ProjContext proj) return new Expr.ProjExpr(
      sourcePosOf(proj),
      visitExpr(proj.expr()),
      Integer.parseInt(proj.NUMBER().getText())
    );
    return new Expr.HoleExpr(sourcePosOf(ctx), null, null);
  }

  @Override
  public Decl visitDataDecl(MziParser.DataDeclContext ctx) {
    // TODO: visit data decl
    throw new IllegalStateException("unimplemented");
  }

  @Override
  public Decl visitStructDecl(MziParser.StructDeclContext ctx) {
    // TODO: visit struct decl
    throw new IllegalStateException("unimplemented");
  }

  @Override
  public Expr visitType(MziParser.TypeContext ctx) {
    return visitExpr(ctx.expr());
  }

  @Override
  public Stmt visitCmd(MziParser.CmdContext ctx) {
    var cmd = ctx.OPEN() != null ? Cmd.Open : Cmd.Import;
    var using = Buffer.<String>of();
    var hiding = Buffer.<String>of();

    for (var useHind : ctx.useHide()) {
      var useOrHide = visitUseHide(useHind);
      (switch (useOrHide._1) {
        case Use -> using;
        case Hide -> hiding;
      }).appendAll(useOrHide._2);
    }

    return new Stmt.CmdStmt(
      sourcePosOf(ctx),
      cmd,
      visitModuleName(ctx.moduleName()),
      using.toImmutableList(),
      hiding.toImmutableList()
    );
  }

  @Override
  public Tuple2<UseHide, ImmutableList<String>> visitUseHide(MziParser.UseHideContext ctx) {
    var type = ctx.USING() != null ? UseHide.Use : UseHide.Hide;
    return Tuple.of(type, visitIds(ctx.ids()).collect(ImmutableList.factory()));
  }

  @Override
  public Stream<String> visitIds(MziParser.IdsContext ctx) {
    return ctx.ID().stream().map(t -> t.getSymbol().getText());
  }

  @Override
  public String visitModuleName(MziParser.ModuleNameContext ctx) {
    return ctx.ID().stream()
      .map(t -> t.getSymbol().getText())
      .collect(Collectors.joining("."));
  }

  @Override
  public @NotNull Assoc visitAssoc(MziParser.AssocContext ctx) {
    if (ctx.FIX() != null) return Assoc.Fix;
    if (ctx.FIXL() != null) return Assoc.FixL;
    if (ctx.FIXR() != null) return Assoc.FixR;
    if (ctx.INFIX() != null) return Assoc.Infix;
    if (ctx.INFIXL() != null) return Assoc.InfixL;
    if (ctx.INFIXR() != null) return Assoc.InfixR;
    if (ctx.TWIN() != null) return Assoc.Twin;
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public Modifier visitFnModifiers(MziParser.FnModifiersContext ctx) {
    if (ctx.ERASE() != null) return Modifier.Erase;
    if (ctx.INLINE() != null) return Modifier.Inline;
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  private @NotNull SourcePos sourcePosOf(ParserRuleContext ctx) {
    var interval = ctx.getSourceInterval();
    var start = ctx.getStart();
    var end = ctx.getStop();
    return new SourcePos(
      interval.a,
      interval.b,
      start.getLine(),
      start.getCharPositionInLine(),
      end.getLine(),
      end.getCharPositionInLine()
    );
  }
}
