// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.module;

import org.aya.api.error.DelayedReporter;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.api.ref.Var;
import org.aya.api.util.InternalException;
import org.aya.api.util.InterruptException;
import org.aya.concrete.Signatured;
import org.aya.concrete.Stmt;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.parse.AyaProducer;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.context.ModuleContext;
import org.aya.concrete.resolve.visitor.StmtShallowResolver;
import org.aya.core.def.Def;
import org.aya.tyck.trace.Trace;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.MutableMap;
import org.glavo.kala.control.Option;
import org.glavo.kala.function.CheckedConsumer;
import org.glavo.kala.function.CheckedRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public final record FileModuleLoader(
  @NotNull SourceFileLocator locator,
  @NotNull Path basePath,
  @NotNull Reporter reporter,
  Trace.@Nullable Builder builder
) implements ModuleLoader {
  @Override public @Nullable MutableMap<Seq<String>, MutableMap<String, Var>>
  load(@NotNull Seq<@NotNull String> path, @NotNull ModuleLoader recurseLoader) {
    try {
      var sourceFile = path.foldLeft(basePath, Path::resolve);
      var sourceFileDisplay = locator.locate(sourceFile);
      var parser = AyaParsing.parser(sourceFileDisplay, sourceFile, reporter());
      var producer = new AyaProducer(sourceFileDisplay, reporter);
      var program = producer.visitProgram(parser.program());
      return tyckModule(sourceFileDisplay, recurseLoader, program, reporter, () -> {}, defs -> {}, builder).exports();
    } catch (IOException e) {
      reporter.reportString(e.getMessage());
      return null;
    } catch (InternalException e) {
      handleInternalError(e);
      return null;
    } catch (InterruptException e) {
      reporter.reportString(e.stage().name() + " interrupted due to error(s).");
      return null;
    }
  }

  public static <E extends Exception> @NotNull ModuleContext tyckModule(
    @NotNull Option<String> sourceFile,
    @NotNull ModuleLoader recurseLoader,
    @NotNull ImmutableSeq<Stmt> program,
    @NotNull Reporter reporter,
    @NotNull CheckedRunnable<E> onResolved,
    @NotNull CheckedConsumer<ImmutableSeq<Def>, E> onTycked,
    Trace.@Nullable Builder builder
  ) throws E {
    var context = new EmptyContext(sourceFile, reporter).derive();
    var shallowResolver = new StmtShallowResolver(sourceFile, recurseLoader);
    program.forEach(s -> s.accept(shallowResolver, context));
    var opSet = new BinOpSet(sourceFile, reporter);
    program.forEach(s -> s.resolve(sourceFile, opSet));
    opSet.sort();
    program.forEach(s -> s.desugar(sourceFile, reporter, opSet));
    onResolved.runChecked();
    // in case we have un-messaged TyckException
    try (var delayedReporter = new DelayedReporter(reporter)) {
      var wellTyped = program
        .mapNotNull(s -> s instanceof Signatured decl ? decl.tyck(sourceFile, delayedReporter, builder) : null);
      onTycked.acceptChecked(wellTyped);
    }
    return context;
  }

  public static void handleInternalError(@NotNull InternalException e) {
    e.printStackTrace();
    e.printHint();
    System.err.println("""
      Please report the stacktrace to the developers so a better error handling could be made.
      Don't forget to inform the version of Aya you're using and attach your code for reproduction.""");
  }
}
