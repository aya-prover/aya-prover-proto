// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle.node;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import org.aya.api.ref.Var;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.Term;
import org.aya.truffle.StructCompileTime;
import org.aya.truffle.Transpiler;
import org.aya.truffle.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author zaoqi
 */
public sealed abstract class IntroNode extends AyaNode {

  public static final class Lambda extends IntroNode {
    private final @NotNull DirectCallNode node;

    public Lambda(@NotNull Transpiler recurseTranspiler, @NotNull Var identifier, @NotNull Term body) {
      this(recurseTranspiler, identifier, b -> b.transpile(body));
    }

    public Lambda(@NotNull Transpiler recurseTranspiler, @NotNull Var identifier, @NotNull java.util.function.Function<Transpiler, AyaNode> body) {
      var transpiler = recurseTranspiler.sub();
      FrameSlot argSlot = transpiler.telescope().add(identifier);
      this.node = AyaRootNode.create(transpiler.telescope(), new FrameSlot[]{argSlot}, new AyaNode[]{body.apply(transpiler)}).createDirectCallNode();
    }

    @Override
    public @NotNull Object execute(@NotNull VirtualFrame frame) {
      return this.executeLam(frame);
    }

    @Override
    public @NotNull Value.Lam executeLam(@NotNull VirtualFrame frame) {
      return new Value.Lam(node, frame.materialize());
    }
  }

  public static final class Function extends IntroNode {
    private final @NotNull DirectCallNode node;

    public Function(@NotNull Transpiler recurseTranspiler, Var @NotNull [] identifiers, @NotNull Term body) {
      this(recurseTranspiler, identifiers, b -> b.transpile(body));
    }

    public Function(@NotNull Transpiler recurseTranspiler, Var @NotNull [] identifiers, @NotNull java.util.function.Function<Transpiler, AyaNode> body) {
      var transpiler = recurseTranspiler.sub();
      FrameSlot[] argsSlot = AyaRootNode.createFrameSlotArr(transpiler.telescope(), identifiers);
      this.node = AyaRootNode.create(transpiler.telescope(), argsSlot, new AyaNode[]{body.apply(transpiler)}).createDirectCallNode();
    }

    @Override
    public @NotNull Object execute(@NotNull VirtualFrame frame) {
      return this.executeFn(frame);
    }

    @Override
    public @NotNull Value.Fn executeFn(@NotNull VirtualFrame frame) {
      return new Value.Fn(node, frame.materialize());
    }
  }

  public static final class New extends IntroNode {
    @Child
    @SuppressWarnings("FieldMayBeFinal")
    private @NotNull AyaNode of;
    @Children
    @SuppressWarnings("FieldMayBeFinal")
    private AyaNode @Nullable [] params;

    public New(@NotNull Transpiler recurseTranspiler, IntroTerm.@NotNull New term) {
      var struct = StructCompileTime.create(Objects.requireNonNull(term.struct().ref().core));
      this.of = new RefNode(recurseTranspiler.telescope().get(term.struct().ref()));
      var params = new AyaNode[struct.map().size()];
      assert struct.map().size() == struct.fields().length;
      term.params().iterator().forEach((v, x) -> params[struct.map().get(v)] = recurseTranspiler.transpile(x));
      this.params = params;
    }

    @Override
    public @NotNull Object execute(@NotNull VirtualFrame frame) {
      return this.executeStruct(frame);
    }

    @Override
    @ExplodeLoop
    public @NotNull Value.Struct executeStruct(@NotNull VirtualFrame frame) {
      Value.StructDecl decl;
      try {
        decl = of.executeStructDecl(frame);
      } catch (UnexpectedResultException e) {
        throw new IllegalArgumentException("need a struct", e);
      }
      var fieldDefaults = decl.bodies();
      assert fieldDefaults.length == params.length;
      var result = new Value[params.length];
      for (var i = 0; i < result.length; i++) {
        if (params[i] != null) {
          result[i] = params[i].expectValue(frame);
        }
      }
      for (var i = 0; i < result.length; i++) {
        if (params[i] == null) {
          result[i] = Objects.requireNonNull(fieldDefaults[i]).apply(result);
        }
      }
      return new Value.Struct(result);
    }
  }

  public static final class Tuple extends IntroNode {
    @Children
    @SuppressWarnings("FieldMayBeFinal")
    private AyaNode @NotNull [] items;

    public Tuple(AyaNode @NotNull [] items) {
      this.items = items;
    }

    @Override
    public @NotNull Object execute(@NotNull VirtualFrame frame) {
      return this.executeTup(frame);
    }

    @Override
    @ExplodeLoop
    public @NotNull Value.Tup executeTup(@NotNull VirtualFrame frame) {
      var result = new Value[items.length];
      for (var i = 0; i < result.length; i++) {
        result[i] = items[i].expectValue(frame);
      }
      return new Value.Tup(result);
    }
  }

  public static final class Con extends IntroNode {
    private final int tag;
    @Children
    @SuppressWarnings("FieldMayBeFinal")
    private AyaNode @NotNull [] items;

    public Con(int tag, AyaNode @NotNull [] items) {
      this.tag = tag;
      this.items = items;
    }

    @Override
    public @NotNull Object execute(@NotNull VirtualFrame frame) {
      return this.executeCtor(frame);
    }

    @Override
    @ExplodeLoop
    public @NotNull Value.Ctor executeCtor(@NotNull VirtualFrame frame) {
      var result = new Value[items.length];
      for (var i = 0; i < result.length; i++) {
        result[i] = items[i].expectValue(frame);
      }
      return new Value.Ctor(this.tag, result);
    }
  }
}
