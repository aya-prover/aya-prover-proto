// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle;

import org.aya.tyck.TyckDeclTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author zaoqi
 */
public class TruffleTest {
  @Test
  public void basic() {
    var defs = TyckDeclTest.successTyckDecls("""
      open data Nat : Set | zero | suc Nat
      def a: Nat => suc zero""");
    var lang = new AyaTruffleLanguage();
    var context = lang.runDefs(defs);
    var map = context.toStrMap();
    assertEquals(2, map.size());
    assertTrue(map.get("a") instanceof Value.Ctor);
    assertNotNull(map.get("Nat"));
  }

  @Test
  public void complex() {
    var defs = TyckDeclTest.successTyckDecls("""
      open data Nat : Set | zero | suc Nat
      def tracy (a b : Nat) : Nat
       | zero, a => a
       | a, zero => a
       | suc a, b => suc (tracy a b)
       | a, suc b => suc (tracy a b)
      def xyr : Nat => tracy zero (suc zero)
      def kiva : Nat => tracy (suc zero) zero
      def overlap (a : Nat) : Nat => tracy a zero
      def overlap2 (a : Nat) : Nat => tracy zero a""");
    var lang = new AyaTruffleLanguage();
    var context = lang.runDefs(defs);
    var map = context.toStrMap();
    assertTrue(map.get("xyr") instanceof Value.Ctor);
    assertTrue(map.get("kiva") instanceof Value.Ctor);
    assertTrue(map.get("overlap") instanceof Value.Fn);
    assertTrue(map.get("overlap2") instanceof Value.Fn);
  }

  @Test
  public void I() {
    var defs = TyckDeclTest.successTyckDecls("""
      prim I
      prim left
      prim right
      struct Path (A : I -> Type) (a : A left) (b : A right) : Type
       | at (i : I) : A i {
         | left => a
         | right => b
       }
      def path {A : I -> Type} (p : Pi (i : I) -> A i)
        => new Path A (p left) (p right) { | at i => p i }
      def `=` Eq {A : Type} (a b : A) : Type => Path (\\ i => A) a b
      def idp {A : Type} (a : A) : a = a => path (\\ i => a)""");
    var lang = new AyaTruffleLanguage();
    var context = lang.runDefs(defs);
    var map = context.toStrMap();
  }
}
