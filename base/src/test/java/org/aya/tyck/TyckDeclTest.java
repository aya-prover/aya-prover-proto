// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.ref.Var;
import org.aya.concrete.ParseTest;
import org.aya.concrete.Signatured;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.module.EmptyModuleLoader;
import org.aya.concrete.resolve.visitor.StmtShallowResolver;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.pat.Pat;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.generic.Matching;
import org.aya.test.Lisp;
import org.aya.test.ThrowingReporter;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.control.Option;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TyckDeclTest {
  @Test
  public void testIdFunc() {
    idFuncTestCase("def id {A : Set} (a : A) : A => a");
    idFuncTestCase("def id {A : Set} (a : A) => a");
  }

  public void idFuncTestCase(@NotNull @NonNls @Language("TEXT") String code) {
    var fnDef = successTyckFn(code);
    var vars = new MutableHashMap<String, @NotNull Var>();
    vars.put(fnDef.ref().name(), fnDef.ref());
    var expected = Lisp.parseDef("id",
      "(A (U) im (a (A) ex null))", "A", "a", vars);
    assertEquals(expected.telescope().map(Term.Param::toDoc), fnDef.telescope().map(Term.Param::toDoc));
    assertEquals(expected.body().getLeftValue().toDoc(), fnDef.body().getLeftValue().toDoc());
  }

  private FnDef successTyckFn(@NotNull @NonNls @Language("TEXT") String code) {
    var decl = ParseTest.parseDecl(code)._1;
    decl.ctx = new EmptyContext(Option.none(), ThrowingReporter.INSTANCE).derive();
    var opSet = new BinOpSet(Option.none(), ThrowingReporter.INSTANCE);
    decl.resolve(Option.none(), opSet);
    opSet.sort();
    decl.desugar(Option.none(), ThrowingReporter.INSTANCE, opSet);
    var def = decl.tyck(Option.none(), ThrowingReporter.INSTANCE, null);
    assertNotNull(def);
    assertTrue(def instanceof FnDef);
    return ((FnDef) def);
  }

  @Test
  public void ctorPatScoping() {
    var defs = successTyckDecls("""
      data Nat : Set | zero | suc Nat
      def xyr (zero : Nat) : Nat
        | zero => zero
        | suc n => zero""");
    var nat = (DataDef) defs.get(0);
    var xyr = (FnDef) defs.get(1);
    var ctors = nat.body();
    assertEquals(2, ctors.size());
    var clauses = xyr.body().getRightValue();
    var zeroToZero = ((Matching<?, ?>) clauses.get(0));
    var zeroCtor = ctors.get(0);
    assertEquals(0, zeroCtor.conTele().size());
    var zeroParam = xyr.telescope().get(0);
    assertEquals(zeroToZero.body(), new RefTerm(zeroParam.ref()));
    assertEquals(zeroCtor.ref(), ((Pat.Ctor) zeroToZero.patterns().get(0)).ref());
  }

  public static @NotNull ImmutableSeq<Def> successTyckDecls(@Language("TEXT") @NonNls @NotNull String text) {
    var decls = ParseTest.INSTANCE
      .visitProgram(AyaParsing.parser(text).program());
    var ssr = new StmtShallowResolver(Option.none(), new EmptyModuleLoader());
    var ctx = new EmptyContext(Option.none(), ThrowingReporter.INSTANCE).derive();
    decls.forEach(d -> d.accept(ssr, ctx));
    var opSet = new BinOpSet(Option.none(), ThrowingReporter.INSTANCE);
    decls.forEach(s -> s.resolve(Option.none(), opSet));
    opSet.sort();
    decls.forEach(stmt -> stmt.desugar(Option.none(), ThrowingReporter.INSTANCE, opSet));
    return decls
      .map(i -> i instanceof Signatured s ? s.tyck(Option.none(), ThrowingReporter.INSTANCE, null) : null)
      .filter(Objects::nonNull);
  }
}
