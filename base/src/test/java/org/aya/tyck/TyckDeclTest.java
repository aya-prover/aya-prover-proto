// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.SourceFile;
import org.aya.concrete.ParseTest;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.parse.AyaProducer;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.module.EmptyModuleLoader;
import org.aya.concrete.resolve.visitor.StmtShallowResolver;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.term.CallTerm;
import org.aya.test.ThrowingReporter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TyckDeclTest {
  private FnDef successTyckFn(@NotNull @NonNls @Language("TEXT") String code) {
    var decl = ParseTest.parseDecl(code)._1;
    decl.ctx = new EmptyContext(ThrowingReporter.INSTANCE).derive("decl");
    var opSet = new BinOpSet(ThrowingReporter.INSTANCE);
    decl.resolve(opSet);
    opSet.sort();
    decl.desugar(ThrowingReporter.INSTANCE, opSet);
    var def = decl.tyck(ThrowingReporter.INSTANCE, null);
    assertNotNull(def);
    assertTrue(def instanceof FnDef);
    return ((FnDef) def);
  }

  @Test public void ctorPatScoping() {
    var defs = successTyckDecls("""
      data Nat : Type | zero | suc Nat
      def xyr (zero : Nat) : Nat
        | zero => zero
        | suc n => zero""");
    // ^ the latter `zero` refers to the `zero` parameter, and will be substituted as `suc n`
    var nat = (DataDef) defs.get(0);
    var xyr = (FnDef) defs.get(1);
    var ctors = nat.body;
    assertEquals(2, ctors.size());
    var clauses = xyr.body.getRightValue();
    var sucToZero = clauses.get(1);
    var sucCtor = ctors.get(1);
    assertEquals(1, sucCtor.selfTele.size());
    assertEquals(((CallTerm.Con) sucToZero.body()).ref(), sucCtor.ref);
  }

  public static @NotNull ImmutableSeq<Stmt> successDesugarDecls(@Language("TEXT") @NonNls @NotNull String text) {
    var decls = new AyaProducer(SourceFile.NONE,
      ThrowingReporter.INSTANCE).visitProgram(AyaParsing.parser(text).program());
    var ssr = new StmtShallowResolver(new EmptyModuleLoader(), null);
    var ctx = new EmptyContext(ThrowingReporter.INSTANCE).derive("decl");
    decls.forEach(d -> d.accept(ssr, ctx));
    var opSet = new BinOpSet(ThrowingReporter.INSTANCE);
    decls.forEach(s -> s.resolve(opSet));
    opSet.sort();
    decls.forEach(stmt -> stmt.desugar(ThrowingReporter.INSTANCE, opSet));
    return decls;
  }

  public static @NotNull ImmutableSeq<Def> successTyckDecls(@Language("TEXT") @NonNls @NotNull String text) {
    return successDesugarDecls(text).view()
      .map(i -> i instanceof Decl s ? s.tyck(ThrowingReporter.INSTANCE, null) : null)
      .filter(Objects::nonNull).toImmutableSeq();
  }
}
