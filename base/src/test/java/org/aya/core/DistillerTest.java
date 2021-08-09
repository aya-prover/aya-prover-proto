// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core;

import org.aya.api.distill.DistillerOptions;
import org.aya.core.def.PrimDef;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.TyckDeclTest;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class DistillerTest {
  @Test public void fn() {
    var doc1 = declDoc("def id {A : Set} (a : A) : A => a");
    var doc2 = declDoc("def id {A : Set} (a : A) => a");
    var doc3 = declDoc("""
      def curry3 (A  B  C  D : Set)
                  (f : Pi (x : Sig A B ** C) -> D)
                  (a : A) (b : B) (c : C) : D
        => f (a, b, c)
      def uncurry3 (A : Set) (B : Set) (C : Set) (D : Set)
                    (f : Pi A B C -> D)
                    (p : Sig A B ** C) : D
        => f (p.1) (p.2) (p.3)""");
    assertFalse(Doc.cat(doc1, doc2, doc3).renderToHtml().isEmpty());
  }

  @Test public void data() {
    @Language("TEXT") var code = """
      open data Nat : Set | zero | suc Nat
      open data Int : Set | pos Nat | neg Nat { | zero => pos zero }
      open data Fin (n : Nat) : Set | suc m => fzero | suc m => fsuc (Fin m)
      """;
    assertFalse(declDoc(code).renderToHtml().isEmpty());
    assertFalse(declCDoc(code).renderToHtml().isEmpty());
  }

  @Test public void neo() {
    assertFalse(declDoc("""
      prim I prim left
      prim right

      struct Pair (A : Type) (B : Type) : Type
        | fst : A
        | snd : B
        | we-are-together : Sig A ** B => (fst, snd)

      def test-nat-pair : Pair I I =>
        new Pair I I { | fst => left | snd => left }

      def make-pair (A B : Set) (a : A) (b : B) : Pair A B =>
        new Pair A B { | fst => a | snd => b }
      """).renderToHtml().isEmpty());
  }

  @Test public void path() {
    @Language("TEXT") var code = """
      prim I prim left prim right
      struct Path (A : Pi I -> Type) (a : A left) (b : A right) : Type
       | at (i : I) : A i {
         | left => a
         | right => b
       }
      def path {A : Pi I -> Type} (p : Pi (i : I) -> A i)
        => new Path A (p left) (p right) { | at i => p i }
      """;
    assertFalse(declDoc(code).renderToTeX().isEmpty());
    tearDown();
    assertFalse(declCDoc(code).renderToTeX().isEmpty());
  }

  @AfterEach public void tearDown() {
    PrimDef.clearConcrete();
  }

  private @NotNull Doc declDoc(@Language("TEXT") String text) {
    return Doc.vcat(TyckDeclTest.successTyckDecls(text).map(d -> d.toDoc(DistillerOptions.DEBUG)));
  }

  private @NotNull Doc declCDoc(@Language("TEXT") String text) {
    return Doc.vcat(TyckDeclTest.successDesugarDecls(text).map(s -> s.toDoc(DistillerOptions.DEBUG)));
  }
}
