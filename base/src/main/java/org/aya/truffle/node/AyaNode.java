// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle.node;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import org.aya.api.ref.Var;
import org.aya.truffle.Slot;
import org.aya.truffle.Telescope;
import org.aya.truffle.TypesGen;
import org.aya.truffle.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author zaoqi
 */
@NodeInfo(language = "Aya", description = "The base node")
public abstract class AyaNode extends Node {
  public abstract @NotNull Object execute(@NotNull VirtualFrame frame);

  // Some internal nodes don't return Value.
  public @NotNull Value executeValue(@NotNull VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectValue(this.execute(frame));
  }

  public final @NotNull Value expectValue(@NotNull VirtualFrame frame) {
    return TypesGen.asValue(this.execute(frame));
  }

  public @NotNull Value.Erased executeErased(@NotNull VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectErased(this.execute(frame));
  }

  public @NotNull Value.Tup executeTup(@NotNull VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectTup(this.execute(frame));
  }

  public @NotNull Value.Lam executeLam(@NotNull VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectLam(this.execute(frame));
  }

  public @NotNull Value.Fn executeFn(@NotNull VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectFn(this.execute(frame));
  }

  public @NotNull Value.StructDecl executeStructDecl(@NotNull VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectStructDecl(this.execute(frame));
  }

  public @NotNull Value.Struct executeStruct(@NotNull VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectStruct(this.execute(frame));
  }

  public @NotNull Value.Ctor executeCtor(@NotNull VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectCtor(this.execute(frame));
  }

  public static final class ArgNode extends AyaNode {
    private final int index;

    public ArgNode(int index) {
      this.index = index;
    }

    @Override
    public @NotNull Object execute(@NotNull VirtualFrame frame) {
      try {
        return Objects.requireNonNull(frame.getArguments()[this.index + 1]);
      } catch (ArrayIndexOutOfBoundsException e) {
        throw new IllegalArgumentException("not enough arguments provided", e);
      }
    }
  }

  public static class ErasedNode extends AyaNode {
    // A Truffle Node must not be a singleton because it must have exactly one parent.
    private ErasedNode() {
    }

    public static @NotNull AyaNode.ErasedNode create() {
      return new ErasedNode();
    }

    @Override
    public @NotNull Object execute(@NotNull VirtualFrame frame) {
      return Value.Erased.INSTANCE;
    }

    @Override
    public @NotNull Value.Erased executeErased(@NotNull VirtualFrame frame) {
      return Value.Erased.INSTANCE;
    }
  }

  public static class InvalidPatNode extends AyaNode {
    // A Truffle Node must not be a singleton because it must have exactly one parent.
    private InvalidPatNode() {
    }

    public static @NotNull AyaNode.InvalidPatNode create() {
      return new InvalidPatNode();
    }

    @Override
    public @NotNull Object execute(@NotNull VirtualFrame frame) {
      throw new IllegalStateException("InvalidPatNode");
    }
  }

  public static final class PatCtorNode extends AyaNode {
    @Child
    @SuppressWarnings("FieldMayBeFinal")
    private AyaNode of;
    private final int tag;
    private final int len;
    @Child
    @SuppressWarnings("FieldMayBeFinal")
    private IntroNode.Function continuation;
    @Child
    @SuppressWarnings("FieldMayBeFinal")
    private AyaNode otherwise;

    public PatCtorNode(AyaNode of, int tag, int len, IntroNode.Function continuation, AyaNode otherwise) {
      this.of = of;
      this.tag = tag;
      this.len = len;
      this.continuation = continuation;
      this.otherwise = otherwise;
    }

    @Override
    public @NotNull Object execute(@NotNull VirtualFrame frame) {
      Value.Ctor ctor;
      try {
        ctor = of.executeCtor(frame);
      } catch (UnexpectedResultException e) {
        throw new IllegalStateException("Expect Ctor", e);
      }
      if (ctor.tag() == this.tag) {
        assert len == ctor.data().length;
        var itselfWithData = new Value[len + 1];
        itselfWithData[0] = ctor;
        System.arraycopy(ctor.data(), 0, itselfWithData, 1, len);
        return continuation.executeFn(frame).apply(itselfWithData);
      } else {
        return otherwise.execute(frame);
      }
    }
  }

  public static final class RefNode extends AyaNode {
    private final @NotNull Slot slot;

    public RefNode(@NotNull Slot slot) {
      this.slot = slot;
    }

    public RefNode(@NotNull Telescope telescope, @NotNull Var identifier) {
      this(telescope.get(identifier));
    }

    @Override
    public @NotNull Object execute(@NotNull VirtualFrame frame) {
      return slot.getValue(frame);
    }
  }

  public static final class FrameGetterNode extends AyaNode {
    @Override
    public @NotNull Object execute(@NotNull VirtualFrame frame) {
      return frame.materialize();
    }
  }
}
