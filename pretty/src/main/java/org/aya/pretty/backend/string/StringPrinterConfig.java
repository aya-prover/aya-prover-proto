// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.string;

import org.aya.pretty.backend.string.style.UnixTermStylist;
import org.aya.pretty.printer.PrinterConfig;
import org.jetbrains.annotations.NotNull;

public class StringPrinterConfig extends PrinterConfig.Basic {
  public final @NotNull StringStylist formatter;
  public final boolean unicode;

  public StringPrinterConfig(@NotNull StringStylist formatter, int pageWidth, boolean unicode) {
    super(pageWidth, INFINITE_SIZE);
    this.formatter = formatter;
    this.unicode = unicode;
  }

  @Override
  public @NotNull StringStylist getStyleFormatter() {
    return formatter;
  }

  public static @NotNull StringPrinterConfig unixTerminal(int pageWidth) {
    return new StringPrinterConfig(UnixTermStylist.INSTANCE, pageWidth, true);
  }

  public static @NotNull StringPrinterConfig unixTerminal() {
    return unixTerminal(INFINITE_SIZE);
  }
}
