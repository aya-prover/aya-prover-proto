// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.SourcePos;
import org.aya.core.def.FnDef;
import org.aya.core.pat.Pat;
import org.aya.test.ThrowingReporter;
import org.aya.tyck.pat.PatClassifier;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CC = coverage and confluence
 */
public class PatCCTest {
  public static @NotNull ImmutableSeq<PatClassifier.PatClass> testClassify(@NotNull FnDef fnDef) {
    var clauses = fnDef.body.getRightValue().map(Pat.PrototypeClause::prototypify);
    return PatClassifier.classify(clauses, fnDef.telescope, ThrowingReporter.INSTANCE, SourcePos.NONE, true);
  }

  @Test public void addCC() {
    var decls = TyckDeclTest.successTyckDecls("""
      open data Nat : Type | zero | suc Nat
      def add (a b : Nat) : Nat
       | zero, b => b
       | a, zero => a
       | suc a, b => suc (add a b)
       | a, suc b => suc (add a b)""");
    var classified = testClassify((FnDef) decls.get(1));
    assertEquals(4, classified.size());
    classified.forEach(cls ->
      assertEquals(2, cls.contents().size()));
  }

  @Test public void maxCC() {
    var decls = TyckDeclTest.successTyckDecls("""
      open data Nat : Type | zero | suc Nat
      def max (a b : Nat) : Nat
       | zero, b => b
       | a, zero => a
       | suc a, suc b => suc (max a b)""");
    var classified = testClassify((FnDef) decls.get(1));
    assertEquals(4, classified.size());
    assertEquals(3, classified.filter(patClass -> patClass.contents().sizeEquals(1)).size());
    assertEquals(1, classified.filter(patClass -> patClass.contents().sizeEquals(2)).size());
  }

  @Test public void tupleCC() {
    var decls = TyckDeclTest.successTyckDecls("""
      open data Nat : Type | zero | suc Nat
      open data Unit : Type | unit Nat
      def max (a : Sig Nat ** Nat) (b : Unit) : Nat
       | (zero, b), unit x => b
       | (a, zero), y => a
       | (suc a, suc b), unit y => suc (max (a, b) (unit zero))""");
    var classified = testClassify((FnDef) decls.get(2));
    assertEquals(4, classified.size());
    assertEquals(3, classified.filter(patClass -> patClass.contents().sizeEquals(1)).size());
    assertEquals(1, classified.filter(patClass -> patClass.contents().sizeEquals(2)).size());
  }
}
