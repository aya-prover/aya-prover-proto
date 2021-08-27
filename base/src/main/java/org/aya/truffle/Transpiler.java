// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Unit;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.core.Matching;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.*;
import org.aya.pretty.doc.Doc;
import org.aya.truffle.node.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * @author zaoqi
 */
public record Transpiler(@NotNull Telescope telescope) implements Term.Visitor<Unit, AyaNode> {
  public @NotNull Transpiler sub() {
    return new Transpiler(new Telescope(this.telescope()));
  }

  public @NotNull AyaNode transpile(@NotNull Term term) {
    return term.accept(this, Unit.unit());
  }

  public @NotNull DeclNode transpileDef(@NotNull FrameSlot slot, @NotNull Def core) {
    if (!(core instanceof TopLevelDef def)) {
      throw new IllegalArgumentException("Shall not have SubLevelDef evaluated.");
    }
    assert slot.getIdentifier() == def.ref();
    return switch (def) {
      case PrimDef prim -> new DeclNode(slot, this.transpile(prim.result()));
      case FnDef fn -> fn.body.fold(term -> this.buildTermFn(slot, fn.telescope.map(Term.Param::ref), b -> b.transpile(term)),
        matching -> this.buildMatchingFn(slot, fn.telescope, matching));
      case StructDef struct -> new DeclNode(slot,
        StructDeclNode.create(this,
          struct.fields.map(x -> Tuple.of(x.ref, x.body))));
      case DataDef data -> new DeclNode(slot, AyaNode.ErasedNode.create());
      case null, default -> throw new IllegalArgumentException("def can't be null.");
    };
  }

  public static final class StructDeclNode extends AyaNode {
    private final DirectCallNode @Nullable [] nodes;

    public StructDeclNode(DirectCallNode @Nullable [] nodes) {
      this.nodes = nodes;
    }

    private static @NotNull DirectCallNode createField(@NotNull Transpiler recurseTranspiler, Var @NotNull [] fields, @NotNull Term body) {
      var transpiler = recurseTranspiler.sub();
      FrameSlot[] argsSlot = AyaRootNode.createFrameSlotArr(transpiler.telescope(), fields);
      return AyaRootNode.create(transpiler.telescope(), argsSlot, new AyaNode[]{transpiler.transpile(body)}).createDirectCallNode();
    }

    public static @NotNull StructDeclNode create(@NotNull Transpiler recurseTranspiler, @NotNull ImmutableSeq<Tuple2<Var, Option<Term>>> fields) {
      var fieldNames = fields.map(x -> x._1).toArray(Var.class);
      var nodes = fields.map(x -> x._2.isDefined() ? createField(recurseTranspiler, fieldNames, x._2.get()) : null);
      return new StructDeclNode(nodes.toArray(DirectCallNode.class));
    }

    @Override
    public @NotNull Object execute(@NotNull VirtualFrame frame) {
      return this.executeStructDecl(frame);
    }

    @Override
    @ExplodeLoop
    public @NotNull Value.StructDecl executeStructDecl(@NotNull VirtualFrame frame) {
      Value.StructField[] result = new Value.StructField[nodes.length];
      for (var i = 0; i < result.length; i++) {
        result[i] = nodes[i] == null ? null : new Value.StructField(nodes[i], frame.materialize());
      }
      return new Value.StructDecl(result);
    }
  }

  public @NotNull DeclNode buildTermFn(@NotNull FrameSlot slot, @NotNull ImmutableSeq<LocalVar> args, @NotNull Function<Transpiler, AyaNode> body) {
    if (args.isEmpty()) {
      return new DeclNode(slot, body.apply(this));
    }
    return new DeclNode(slot, new IntroNode.Function(this, args.toArray(Var.class), body));
  }

  public @NotNull DeclNode buildMatchingFn(@NotNull FrameSlot slot, @NotNull ImmutableSeq<Term.Param> args, @NotNull ImmutableSeq<Matching> body) {
    return this.buildTermFn(slot, args.map(Term.Param::ref), b -> b.matching(args.<Term>map(param -> new RefTerm(param.ref(), param.type())).view(), body.view()));
  }

  public @NotNull AyaNode matching(@NotNull SeqView<Term> of, @NotNull SeqView<Matching> body) {
    for (var matching : body) {
      assert matching.patterns().size() == of.size();
    }
    return body.<Function<Transpiler, AyaNode>>foldRight(b -> AyaNode.InvalidPatNode.create(), (current, otherwise) -> b -> b.matching(of, current.patterns().view(), x -> x.transpile(current.body()), otherwise)).apply(this);
  }

  public @NotNull AyaNode matching(@NotNull SeqView<Term> ofs, @NotNull SeqView<Pat> patterns, @NotNull Function<Transpiler, AyaNode> body, @NotNull Function<Transpiler, AyaNode> otherwise) {
    assert ofs.size() == patterns.size();
    if (ofs.size() == 0) {
      return body.apply(this);
    }
    var ofsHead = ofs.first();
    var ofsTail = ofs.drop(1);
    var patternsHead = patterns.first();
    var patternsTail = patterns.drop(1);
    return this.matching(ofsHead, patternsHead, ofsTail, patternsTail, body, otherwise);
  }

  public @NotNull AyaNode matching(@NotNull Term of, @NotNull Pat pat, @NotNull SeqView<Term> ofs, @NotNull SeqView<Pat> patterns, @NotNull Function<Transpiler, AyaNode> body, @NotNull Function<Transpiler, AyaNode> otherwise) {
    assert ofs.size() == patterns.size();
    if (ofs.size() == 0) {
      return this.matching(of, pat, body, otherwise);
    }
    return this.matching(of, pat, b -> b.matching(ofs, patterns, body, otherwise), otherwise);
  }

  public @NotNull AyaNode matching(@NotNull Term of, @NotNull Pat pat, @NotNull Function<Transpiler, AyaNode> body, @NotNull Function<Transpiler, AyaNode> otherwise) {
    var self = this;
    return pat.accept(new Pat.Visitor<>() {
      @Override public @NotNull AyaNode visitBind(Pat.@NotNull Bind bind, Unit unit) {
        return new ElimNode.App(new IntroNode.Lambda(self, bind.as(), body), self.transpile(of));
      }

      @Override public @NotNull AyaNode visitTuple(Pat.@NotNull Tuple tuple, Unit unit) {
        var ofs = tuple.pats().<Term>mapIndexed((i, p) -> new ElimTerm.Proj(of, i + 1)).view();
        var pats = tuple.pats().view();
        if (tuple.as() == null) {
          return self.matching(ofs, pats, body, otherwise);
        } else {
          return self.matching(ofs.appended(of), pats.appended(new Pat.Bind(tuple.explicit(), tuple.as(), tuple.type())), body, otherwise);
        }
      }

      @Override
      public @NotNull AyaNode visitCtor(Pat.@NotNull Ctor ctor, Unit unit) {
        var data = DataCompileTime.create(ctor.ref().core.dataRef.core);
        var tag = data.tagMap().get(ctor.ref());
        var len = ctor.params().size();
        assert len == data.ctors()[tag];
        // TODO: rewrite this hackish code
        var ids = ctor.params().view().map(GenVar::createFromObject).prepended(ctor.as() == null ? GenVar.create() : ctor.as()).toImmutableSeq();
        var placeholder = new ErrorTerm(new Doc.Empty(), true);
        Function<Transpiler, AyaNode> continuation = b -> b.matching(ids.<Term>map(x -> new RefTerm(x, placeholder)).view(),
          ctor.params().prepended(new Pat.Bind(true, ctor.as() == null ? GenVar.create() : ctor.as(), placeholder)).view(),
          body, otherwise);
        return new AyaNode.PatCtorNode(self.transpile(of), tag, len, new IntroNode.Function(self, ids.toArray(Var.class), continuation), otherwise.apply(self));
      }

      @Override
      public @NotNull AyaNode visitAbsurd(Pat.@NotNull Absurd absurd, Unit unit) {
        return AyaNode.InvalidPatNode.create();
      }

      @Override
      public @NotNull AyaNode visitPrim(Pat.@NotNull Prim prim, Unit unit) {
        throw new UnsupportedOperationException("TODO");
      }
    }, Unit.unit());
  }

  @Override
  public @NotNull AyaNode visitRef(@NotNull RefTerm term, Unit unit) {
    return new AyaNode.RefNode(telescope, term.var());
  }

  @Override
  public @NotNull AyaNode visitLam(IntroTerm.@NotNull Lambda term, Unit unit) {
    return new IntroNode.Lambda(this, term.param().ref(), term.body());
  }

  @Override
  public @NotNull AyaNode visitPi(FormTerm.@NotNull Pi term, Unit unit) {
    return AyaNode.ErasedNode.create();
  }

  @Override
  public @NotNull AyaNode visitSigma(FormTerm.@NotNull Sigma term, Unit unit) {
    return AyaNode.ErasedNode.create();
  }

  @Override
  public @NotNull AyaNode visitUniv(FormTerm.@NotNull Univ term, Unit unit) {
    return AyaNode.ErasedNode.create();
  }

  @Override
  public @NotNull AyaNode visitApp(ElimTerm.@NotNull App term, Unit unit) {
    return new ElimNode.App(this.transpile(term.of()), this.transpile(term.arg().term()));
  }

  @Override
  public @NotNull AyaNode visitFnCall(@NotNull CallTerm.Fn fnCall, Unit unit) {
    return new ElimNode.AppFn(new AyaNode.RefNode(telescope, fnCall.ref()),
      fnCall.args().map(arg -> this.transpile(arg.term())).toArray(AyaNode.class));
  }

  @Override
  public @NotNull AyaNode visitDataCall(@NotNull CallTerm.Data dataCall, Unit unit) {
    return AyaNode.ErasedNode.create();
  }

  @Override
  public @NotNull AyaNode visitConCall(@NotNull CallTerm.Con conCall, Unit unit) {
    var data = DataCompileTime.create(conCall.head().dataRef().core);
    var tag = data.tagMap().get(conCall.head().ref());
    assert data.ctors()[tag] == conCall.conArgs().size();
    return new IntroNode.Con(tag, conCall.conArgs().map(arg -> this.transpile(arg.term())).toArray(AyaNode.class));
  }

  @Override
  public @NotNull AyaNode visitStructCall(@NotNull CallTerm.Struct structCall, Unit unit) {
    return AyaNode.ErasedNode.create();
  }

  @Override
  public @NotNull AyaNode visitPrimCall(CallTerm.@NotNull Prim prim, Unit unit) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Override
  public @NotNull AyaNode visitTup(IntroTerm.@NotNull Tuple term, Unit unit) {
    return new IntroNode.Tuple(term.items().map(this::transpile).toArray(AyaNode.class));
  }

  @Override
  public @NotNull AyaNode visitNew(IntroTerm.@NotNull New newTerm, Unit unit) {
    return new IntroNode.New(this, newTerm);
  }

  @Override
  public @NotNull AyaNode visitProj(ElimTerm.@NotNull Proj term, Unit unit) {
    return new ElimNode.Proj(this.transpile(term.of()), term.ix());
  }

  @Override
  public @NotNull AyaNode visitAccess(CallTerm.@NotNull Access term, Unit unit) {
    var struct = StructCompileTime.create(term.ref().core.structRef.core);
    var field = term.ref().core.ref;
    return new ElimNode.Access(this.transpile(term.of()), struct.map().get(field));
  }

  @Override
  public @NotNull AyaNode visitHole(CallTerm.@NotNull Hole term, Unit unit) {
    throw new AssertionError("Shall not have holes evaluated.");
  }

  @Override
  public @NotNull AyaNode visitError(@NotNull ErrorTerm term, Unit unit) {
    throw new AssertionError("Shall not have error term evaluated.");
  }
}
