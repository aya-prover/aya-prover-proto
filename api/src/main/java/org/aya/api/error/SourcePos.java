// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.error;

import org.aya.api.Global;
import org.aya.pretty.error.LineColSpan;
import org.aya.pretty.error.RangeSpan;
import org.aya.pretty.error.Span;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Position in source code.
 * This class is usually constructed using antlr4's utility function
 * {@code ctx.getStart()} and {@code ctx.getStop()}.
 *
 * @author kiva
 */
@SuppressWarnings("unused")
public record SourcePos(
  @NotNull SourceFile file,
  int tokenStartIndex,
  int tokenEndIndex,
  int startLine,
  int startColumn,
  int endLine,
  int endColumn
) implements Comparable<SourcePos> {
  public static final int UNAVAILABLE_AND_FUCK_ANTLR4 = -114514;

  /**
   * Single instance SourcePos for mocking tests and other usages.
   */
  public static final SourcePos NONE = new SourcePos(SourceFile.NONE, -1, -1, -1, -1, -1, -1);

  public Span toSpan() {
    if (indexAvailable()) {
      return new RangeSpan(file().sourceCode(), tokenStartIndex, tokenEndIndex);
    } else {
      return new LineColSpan(file().sourceCode(), startLine, startColumn, endLine, endColumn);
    }
  }

  private boolean indexAvailable() {
    return tokenStartIndex != UNAVAILABLE_AND_FUCK_ANTLR4
      && tokenEndIndex != UNAVAILABLE_AND_FUCK_ANTLR4;
  }

  private static int min(int x, int y) {
    if (x == -1) return y;
    if (y == -1) return x;
    return Math.min(x, y);
  }

  private static int max(int x, int y) {
    if (x == -1) return y;
    if (y == -1) return x;
    return Math.max(x, y);
  }

  @Contract("_ -> new") public @NotNull SourcePos union(@NotNull SourcePos other) {
    return new SourcePos(
      file,
      min(tokenStartIndex, other.tokenStartIndex),
      max(tokenEndIndex, other.tokenEndIndex),
      min(startLine, other.startLine),
      max(startColumn, other.startColumn),
      max(endLine, other.endLine),
      max(endColumn, other.endColumn)
    );
  }

  @Override public boolean equals(Object o) {
    // we return true when in tests because we
    // don't want to check source pos manually
    // as it is guaranteed to be correct by antlr.
    if (Global.UNITE_SOURCE_POS || this == o) return true;
    if (!(o instanceof SourcePos sourcePos)) return false;
    return tokenStartIndex == sourcePos.tokenStartIndex &&
      tokenEndIndex == sourcePos.tokenEndIndex &&
      startLine == sourcePos.startLine &&
      startColumn == sourcePos.startColumn &&
      endLine == sourcePos.endLine &&
      endColumn == sourcePos.endColumn;
  }

  public boolean contains(int line, int column) {
    return line >= startLine && line <= endLine && column >= startColumn && column <= endColumn;
  }

  public boolean contains(int pos) {
    return pos >= tokenStartIndex && pos <= tokenEndIndex;
  }

  public boolean belongsToSomeFile() {
    return this != SourcePos.NONE && file.isSomeFile();
  }

  @Override public String toString() {
    return "(" + tokenStartIndex + "-" + tokenEndIndex + ") ["
      + startLine + "," + startColumn + "-" + endLine + "," + endColumn + ']';
  }

  @Override
  public int hashCode() {
    // the equals() returns true in tests, so hashCode() should
    // be a constant according to JLS
    if (Global.UNITE_SOURCE_POS) return 0;
    return Objects.hash(tokenStartIndex, tokenEndIndex, startLine, startColumn, endLine, endColumn);
  }

  @Override public int compareTo(@NotNull SourcePos o) {
    if (indexAvailable()) return Integer.compare(tokenStartIndex, o.tokenStartIndex);
    var line = Integer.compare(startLine, o.startLine);
    if (line != 0) return line;
    return Integer.compare(startColumn, o.startColumn);
  }
}
