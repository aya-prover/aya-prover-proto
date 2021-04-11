// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.visitor;

import org.aya.concrete.Decl;
import org.aya.concrete.Stmt;
import org.glavo.kala.tuple.Unit;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves expressions inside stmts, after {@link StmtShallowResolver}
 *
 * @author re-xyr, iec1000
 */
public final class StmtResolver implements Stmt.Visitor<Unit, Unit> {
  public static final @NotNull StmtResolver INSTANCE = new StmtResolver();

  private StmtResolver() {
  }

  @Override public Unit visitModule(Stmt.@NotNull ModuleStmt mod, Unit unit) {
    visitAll(mod.contents(), unit);
    return unit;
  }

  @Override public Unit visitImport(Stmt.@NotNull ImportStmt cmd, Unit unit) {
    return Unit.unit();
  }

  @Override public Unit visitOpen(Stmt.@NotNull OpenStmt cmd, Unit unit) {
    return Unit.unit();
  }

  /** @apiNote Note that this function MUTATES the decl. */
  @Override public Unit visitData(Decl.@NotNull DataDecl decl, Unit unit) {
    var local = ExprResolver.INSTANCE.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    decl.result = decl.result.resolve(local._2);
    for (var ctor : decl.body) {
      var localCtxWithPat = new Ref<>(local._2);
      if (ctor.patterns.isNotEmpty())
        ctor.patterns = ctor.patterns.map(pattern -> PatResolver.INSTANCE.subpatterns(localCtxWithPat, pattern));
      var ctorLocal = ExprResolver.INSTANCE.resolveParams(ctor.telescope, localCtxWithPat.value);
      ctor.telescope = ctorLocal._1;
      ctor.clauses = ctor.clauses.map(clause -> PatResolver.INSTANCE.matchy(clause, ctorLocal._2));
    }
    return unit;
  }

  @Override public Unit visitStruct(Decl.@NotNull StructDecl decl, Unit unit) {
    var local = ExprResolver.INSTANCE.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    decl.result = decl.result.resolve(local._2);

    decl.fields.forEach(field -> {
      var fieldLocal = ExprResolver.INSTANCE.resolveParams(field.telescope, local._2);
      field.telescope = fieldLocal._1;
      field.result = field.result.resolve(fieldLocal._2);
      field.body = field.body.map(e -> e.resolve(fieldLocal._2));
      field.clauses = field.clauses.map(clause -> PatResolver.INSTANCE.matchy(clause, fieldLocal._2));
    });

    return unit;
  }

  /** @apiNote Note that this function MUTATES the decl. */
  @Override public Unit visitFn(Decl.@NotNull FnDecl decl, Unit unit) {
    var local = ExprResolver.INSTANCE.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    decl.result = decl.result.resolve(local._2);
    decl.body = decl.body.map(
      expr -> expr.resolve(local._2),
      pats -> pats.map(clause -> PatResolver.INSTANCE.matchy(clause, local._2)));
    return Unit.unit();
  }

  @Override public Unit visitPrim(@NotNull Decl.PrimDecl decl, Unit unit) {
    var local = ExprResolver.INSTANCE.resolveParams(decl.telescope, decl.ctx);
    decl.telescope = local._1;
    if (decl.result != null) decl.result = decl.result.resolve(local._2);
    return unit;
  }
}
