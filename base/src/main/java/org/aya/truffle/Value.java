// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle;

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author zaoqi
 */
public sealed interface Value {
  record Ctor(int tag, Value @NotNull [] data) implements Value {
  }

  final class Erased implements Value {
    private Erased() {
    }

    public final static @NotNull Value.Erased INSTANCE = new Erased();
  }

  record Fn(@NotNull DirectCallNode callTarget, @NotNull MaterializedFrame frame) implements Value {
    public @NotNull Value apply(Value @NotNull [] rawArgs) {
      var args = new Object[rawArgs.length + 1];
      args[0] = frame;
      System.arraycopy(rawArgs, 0, args, 1, rawArgs.length);
      return (Value) callTarget.call(args);
    }
  }

  record Lam(@NotNull DirectCallNode callTarget, @NotNull MaterializedFrame frame) implements Value {
    public @NotNull Value apply(@NotNull Value object) {
      return (Value) callTarget.call(frame, object);
    }
  }

  record Struct(Value @NotNull [] fields) implements Value {
  }

  record StructDecl(StructField @Nullable [] bodies) implements Value {
  }

  record StructField(@NotNull DirectCallNode callTarget, @NotNull MaterializedFrame frame) implements Value {
    public @NotNull Value apply(Value @NotNull [] rawArgs) {
      var args = new Object[rawArgs.length + 1];
      args[0] = frame;
      System.arraycopy(rawArgs, 0, args, 1, rawArgs.length);
      return (Value) callTarget.call(args);
    }
  }

  record Tup(Value @NotNull [] arr) implements Value {
  }
}
