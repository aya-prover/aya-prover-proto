package org.aya.truffle.node;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import org.aya.truffle.Value;
import org.jetbrains.annotations.NotNull;

/**
 * @author zaoqi
 */
public abstract class ElimNode extends AyaNode {

  public static final class Proj extends ElimNode {
    @Child
    @SuppressWarnings("FieldMayBeFinal")
    private AyaNode of;
    private final int ix;

    public Proj(AyaNode of, int ix) {
      this.of = of;
      this.ix = ix;
    }

    @Override
    public @NotNull Object execute(@NotNull VirtualFrame frame) {
      try {
        return of.executeTup(frame).arr()[ix - 1];
      } catch (UnexpectedResultException e) {
        throw new IllegalArgumentException("What is the Proj of something that is not a Tuple?", e);
      } catch (ArrayIndexOutOfBoundsException e) {
        throw new IllegalArgumentException("Invalid Proj", e);
      }
    }
  }

  public static final class Access extends ElimNode {
    @Child
    @SuppressWarnings("FieldMayBeFinal")
    private AyaNode of;
    private final int ix;

    public Access(AyaNode of, int ix) {
      this.of = of;
      this.ix = ix;
    }

    @Override
    public @NotNull Object execute(@NotNull VirtualFrame frame) {
      try {
        return of.executeStruct(frame).fields()[ix];
      } catch (UnexpectedResultException e) {
        throw new IllegalArgumentException("What is the Access of something that is not a Struct?", e);
      } catch (ArrayIndexOutOfBoundsException e) {
        throw new IllegalArgumentException("Invalid Struct", e);
      }
    }
  }

  public static final class App extends ElimNode {
    @Child
    @SuppressWarnings("FieldMayBeFinal")
    private AyaNode of;
    @Child
    @SuppressWarnings("FieldMayBeFinal")
    private AyaNode arg;

    public App(AyaNode of, AyaNode arg) {
      this.of = of;
      this.arg = arg;
    }

    @Override
    public @NotNull Object execute(@NotNull VirtualFrame frame) {
      Value.Lam lam;
      try {
        lam = of.executeLam(frame);
      } catch (UnexpectedResultException e) {
        throw new IllegalArgumentException("Can't apply a non-Lam value", e);
      }
      return lam.apply(arg.expectValue(frame));
    }
  }

  public static final class AppFn extends ElimNode {
    @Child
    @SuppressWarnings("FieldMayBeFinal")
    private AyaNode of;
    @Children
    @SuppressWarnings("FieldMayBeFinal")
    private AyaNode[] args;

    public AppFn(AyaNode of, AyaNode[] args) {
      this.of = of;
      this.args = args;
    }

    @Override
    @ExplodeLoop
    public @NotNull Object execute(@NotNull VirtualFrame frame) {
      Value.Fn fn;
      try {
        fn = of.executeFn(frame);
      } catch (UnexpectedResultException e) {
        throw new IllegalArgumentException("Can't apply a non-Fn value", e);
      }
      var xs = new Value[args.length];
      for (var i = 0; i < xs.length; i++) {
        xs[i] = args[i].expectValue(frame);
      }
      return fn.apply(xs);
    }
  }
}
