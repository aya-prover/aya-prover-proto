// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli.single;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.CountingReporter;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.api.util.InternalException;
import org.aya.api.util.InterruptException;
import org.aya.cli.utils.MainArgs;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.resolve.module.ModuleListLoader;
import org.aya.concrete.stmt.Decl;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;

public record SingleFileCompiler(
  @NotNull Reporter reporter,
  @Nullable SourceFileLocator locator,
  Trace.@Nullable Builder builder
) {
  public int compile(
    @NotNull Path sourceFile,
    @NotNull CompilerFlags flags,
    @Nullable FileModuleLoader.FileModuleLoaderCallback moduleCallback
  ) throws IOException {
    var reporter = new CountingReporter(this.reporter);
    var locator = this.locator != null ? this.locator : new SourceFileLocator.Module(flags.modulePaths());
    try {
      var program = AyaParsing.program(locator, reporter, sourceFile);
      var distillInfo = flags.distillInfo();
      distill(sourceFile, distillInfo, program, MainArgs.DistillStage.raw);
      var loader = new ModuleListLoader(flags.modulePaths().view().map(path ->
        new CachedModuleLoader(new FileModuleLoader(locator, path, reporter, moduleCallback, builder))).toImmutableSeq());
      FileModuleLoader.tyckModule(ImmutableSeq.of("Mian"), loader, program, reporter,
        () -> {
          distill(sourceFile, distillInfo, program, MainArgs.DistillStage.scoped);
          if (moduleCallback != null) moduleCallback.onResolved(sourceFile, program);
        },
        defs -> {
          distill(sourceFile, distillInfo, defs, MainArgs.DistillStage.typed);
          if (moduleCallback != null) moduleCallback.onTycked(sourceFile, program, defs);
        }, builder);
    } catch (InternalException e) {
      FileModuleLoader.handleInternalError(e);
      reporter.reportString("Internal error");
      return e.exitCode();
    } catch (InterruptException e) {
      reporter.reportString(e.stage().name() + " interrupted due to error(s).");
      if (flags.interruptedTrace()) e.printStackTrace();
    } finally {
      PrimDef.PrimFactory.INSTANCE.clear();
    }
    if (reporter.isEmpty()) {
      reporter.reportString(flags.message().successNotion());
      return 0;
    } else {
      reporter.reportString(flags.message().failNotion());
      return -1;
    }
  }

  private void distill(
    @NotNull Path sourceFile,
    @Nullable CompilerFlags.DistillInfo flags,
    ImmutableSeq<? extends AyaDocile> doc,
    @NotNull MainArgs.DistillStage currentStage
  ) throws IOException {
    if (flags == null || currentStage != flags.distillStage()) return;
    var ayaFileName = sourceFile.getFileName().toString();
    var dotIndex = ayaFileName.indexOf('.');
    var distillDir = sourceFile.resolveSibling(flags.distillDir());
    if (!Files.exists(distillDir)) Files.createDirectories(distillDir);
    var fileName = ayaFileName
      .substring(0, dotIndex > 0 ? dotIndex : ayaFileName.length());
    switch (flags.distillFormat()) {
      case html -> doWrite(doc, distillDir, fileName, ".html", Doc::renderToHtml);
      case latex -> doWrite(doc, distillDir, fileName, ".tex", (thisDoc, $) -> thisDoc.renderToTeX());
      case plain -> doWrite(doc, distillDir, fileName, ".txt", (thisDoc, $) -> thisDoc.debugRender());
      case unix -> doWrite(doc, distillDir, fileName, ".txt", (thisDoc, $) -> thisDoc.renderToString(StringPrinterConfig.unixTerminal()));
    }
  }

  private void doWrite(
    ImmutableSeq<? extends AyaDocile> doc, Path distillDir,
    String fileName, String fileExt, BiFunction<Doc, Boolean, String> toString
  ) throws IOException {
    var docs = Buffer.<Doc>create();
    for (int i = 0; i < doc.size(); i++) {
      var item = doc.get(i);
      var thisDoc = item.toDoc(DistillerOptions.DEFAULT);
      Files.writeString(distillDir.resolve(fileName + "-" + nameOf(i, item) + fileExt), toString.apply(thisDoc, false));
      docs.append(thisDoc);
    }
    Files.writeString(distillDir.resolve(fileName + fileExt), toString.apply(Doc.vcat(docs), true));
  }

  @NotNull private String nameOf(int i, AyaDocile item) {
    return item instanceof Def def ? def.ref().name()
      : item instanceof Decl decl ? decl.ref().name() : String.valueOf(i);
  }
}
