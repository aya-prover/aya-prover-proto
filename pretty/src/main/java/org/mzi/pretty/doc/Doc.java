package org.mzi.pretty.doc;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.pretty.backend.DocStringPrinter;
import org.mzi.pretty.printer.Printer;
import org.mzi.pretty.printer.PrinterConfig;

import java.util.Arrays;
import java.util.function.BinaryOperator;
import java.util.function.Function;

/**
 * This class reimplemented Haskell
 * <a href="https://hackage.haskell.org/package/prettyprinter-1.7.0/docs/src/Prettyprinter.Internal.html">
 * PrettyPrint library's Doc module</a>.
 *
 * @author kiva
 */
public sealed interface Doc {
  default String renderToString(@NotNull DocStringPrinter.Config config) {
    var printer = new DocStringPrinter();
    return this.render(printer, config);
  }

  default <Out, Config extends PrinterConfig>
  @NotNull Out render(@NotNull Printer<Out, Config> printer,
                      @NotNull Config config) {
    return printer.render(config, this);
  }

  //region Doc Variants

  /**
   * The empty document; conceptually the unit of 'Cat'
   */
  record Empty() implements Doc {
  }

  /**
   * Fail to flatten; used in {@link Flatten#flatDoc(Doc)}
   */
  record Fail() implements Doc {
  }

  /**
   * A plain text line without '\n'.
   */
  record PlainText(@NotNull String text) implements Doc {
  }

  /**
   * A clickable text line without '\n'.
   */
  record HyperText(@NotNull String text, @NotNull Link link) implements Doc {
  }

  /**
   * Hard line break
   */
  record Line() implements Doc {
  }

  /**
   * Lay out the defaultDoc 'Doc', but when flattened (via 'group'), prefer
   * the preferWhenFlatten.
   * The layout algorithms work under the assumption that the defaultDoc
   * alternative is less wide than the flattened preferWhenFlatten alternative.
   */
  record FlatAlt(@NotNull Doc defaultDoc, @NotNull Doc preferWhenFlatten) implements Doc {
  }

  /**
   * Concatenation of two documents
   */
  record Cat(@NotNull Doc first, @NotNull Doc second) implements Doc {
  }

  /**
   * Document indented by a number of columns
   */
  record Nest(int indent, @NotNull Doc doc) implements Doc {
  }

  /**
   * The first lines of first document should be shorter than the
   * first lines of the second one, so the layout algorithm can pick the one
   * that fits best. Used to implement layout alternatives for 'softline' and 'group'.
   */
  record Union(@NotNull Doc shorterOne, @NotNull Doc longerOne) implements Doc {
  }

  /**
   * A document that will react on the current cursor position.
   */
  record Column(@NotNull Function<Integer, Doc> docBuilder) implements Doc {
  }

  /**
   * A document that will react on the current nest level.
   */
  record Nesting(@NotNull Function<Integer, Doc> docBuilder) implements Doc {
  }

  /**
   * A document that will react on the page width.
   */
  record PageWidth(@NotNull Function<Integer, Doc> docBuilder) implements Doc {
  }

  //endregion

  //region DocFactory functions

  /**
   * The empty document; conceptually the unit of 'Cat'
   *
   * @return empty document
   */
  @Contract("-> new")
  static @NotNull Doc empty() {
    return new Empty();
  }

  /**
   * By default, flatAlt renders as {@param defaultDoc}. However when 'group'ed,
   * {@param preferWhenFlattened} will be preferred, with {@param defaultDoc} as
   * the fallback for the case when {@param preferWhenFlattened} doesn't fit.
   *
   * @param defaultDoc          default document
   * @param preferWhenFlattened document selected when flattened
   * @return alternative document
   */
  @Contract("_, _ -> new")
  static @NotNull Doc flatAlt(@NotNull Doc defaultDoc, @NotNull Doc preferWhenFlattened) {
    return new FlatAlt(defaultDoc, preferWhenFlattened);
  }

  /**
   * Layout a document depending on which column it starts at.
   * {@link Doc#align(Doc)} is implemented in terms of {@code column}.
   *
   * <pre>
   * >>> column (\l -> "Columns are" <+> pretty l <> "-based.")
   * Columns are 0-based.
   *
   * >>> let doc = "prefix" <+> column (\l -> "| <- column" <+> pretty l)
   * >>> vsep [indent n doc | n <- [0,4,8]]
   * prefix | <- column 7
   *     prefix | <- column 11
   *         prefix | <- column 15
   * </pre>
   *
   * @param docBuilder document generator when current position provided
   * @return column action document
   */
  @Contract("_ -> new")
  static @NotNull Doc column(@NotNull Function<Integer, Doc> docBuilder) {
    return new Column(docBuilder);
  }

  /**
   * Layout a document depending on the current 'nest'ing level.
   * {@link Doc#align(Doc)} is implemented in terms of {@code nesting}.
   *
   * <pre>
   * >>> let doc = "prefix" <+> nesting (\l -> brackets ("Nested:" <+> pretty l))
   * >>> vsep [indent n doc | n <- [0,4,8]]
   * prefix [Nested: 0]
   *     prefix [Nested: 4]
   *         prefix [Nested: 8]
   * </pre>
   *
   * @param docBuilder document generator when current nest level provided
   * @return nest level action document
   */
  @Contract("_ -> new")
  static @NotNull Doc nesting(@NotNull Function<Integer, Doc> docBuilder) {
    return new Nesting(docBuilder);
  }

  /**
   * Layout a document depending on the page width, if one has been specified.
   *
   * <pre>
   * >>> let prettyPageWidth (AvailablePerLine l r) = "Width:" <+> pretty l <> ", ribbon fraction:" <+> pretty r
   * >>> let doc = "prefix" <+> pageWidth (brackets . prettyPageWidth)
   * >>> putDocW 32 (vsep [indent n doc | n <- [0,4,8]])
   * prefix [Width: 32, ribbon fraction: 1.0]
   *     prefix [Width: 32, ribbon fraction: 1.0]
   *         prefix [Width: 32, ribbon fraction: 1.0]
   * </pre>
   *
   * @param docBuilder document generator when page width provided
   * @return page width action document
   */
  @Contract("_ -> new")
  static @NotNull Doc pageWidth(@NotNull Function<Integer, Doc> docBuilder) {
    return new PageWidth(docBuilder);
  }

  /**
   * lays out the document {@param doc} with the current nesting level
   * (indentation of the following lines) increased by {@param indent}.
   * Negative values are allowed, and decrease the nesting level accordingly.
   *
   * @param indent indentation of the following lines
   * @param doc    the document to lay out
   * @return indented document
   */
  @Contract("_, _ -> new")
  static @NotNull Doc nest(int indent, @NotNull Doc doc) {
    return indent == 0
      ? doc
      : new Nest(indent, doc);
  }

  /**
   * align lays out the document {@param doc} with the nesting level set to the
   * current column. It is used for example to implement {@link Doc#hang(int, Doc)}.
   * <p>
   * As an example, we will put a document right above another one, regardless of
   * the current nesting level. Without 'align'ment, the second line is put simply
   * below everything we've had so far,
   *
   * <pre>
   * >>> "lorem" <+> vsep ["ipsum", "dolor"]
   * lorem ipsum
   * dolor
   * </pre>
   * <p>
   * If we add an 'align' to the mix, the @'vsep'@'s contents all start in the
   * same column,
   *
   * <pre>
   * >>> "lorem" <+> align (vsep ["ipsum", "dolor"])
   * lorem ipsum
   *       dolor
   * </pre>
   *
   * @param doc document to be aligned
   * @return aligned document
   */
  @Contract("_ -> new")
  static @NotNull Doc align(@NotNull Doc doc) {
    // note: nesting might be negative
    return column(k -> nesting(i -> nest(k - i, doc)));
  }

  /**
   * hang lays out the document {@param doc} with a nesting level set to the
   * /current column/ plus {@param deltaNest}.
   * Negative values are allowed, and decrease the nesting level accordingly.
   *
   * <pre>
   * >>> let doc = reflow "Indenting these words with hang"
   * >>> putDocW 24 ("prefix" <+> hang 4 doc)
   * prefix Indenting these
   *            words with
   *            hang
   * </pre>
   * <p>
   * This differs from {@link Doc#nest(int, Doc)}, which is based on
   * the /current nesting level/ plus {@code indent}.
   * When you're not sure, try the more efficient 'nest' first. In our
   * example, this would yield
   *
   * <pre>
   * >>> let doc = reflow "Indenting these words with nest"
   * >>> putDocW 24 ("prefix" <+> nest 4 doc)
   * prefix Indenting these
   *     words with nest
   * </pre>
   *
   * @param deltaNest change of nesting level, relative to the start of the first line
   * @param doc       document to indent
   * @return hang-ed document
   */
  @Contract("_, _ -> new")
  static @NotNull Doc hang(int deltaNest, @NotNull Doc doc) {
    return align(nest(deltaNest, doc));
  }

  /**
   * indent indents document {@param doc} by {@param indent} columns,
   * starting from the current cursor position.
   *
   * <pre>
   * >>> let doc = reflow "The indent function indents these words!"
   * >>> putDocW 24 ("prefix" <> indent 4 doc)
   * prefix    The indent
   *           function
   *           indents these
   *           words!
   * </pre>
   *
   * @param indent number of spaces to increase indentation by
   * @param doc    document
   * @return indented document
   */
  @Contract("_, _ -> new")
  static @NotNull Doc indent(int indent, @NotNull Doc doc) {
    return hang(indent, simpleCat(spaces(indent), doc));
  }

  /**
   * Plain text document
   *
   * @param text text that may contain '\n'
   * @return text document of the whole text
   */
  @Contract("_ -> new")
  static @NotNull Doc plain(String text) {
    if (!text.contains("\n")) {
      return new PlainText(text);
    }

    return Arrays.stream(text.split("\n"))
      .map(Doc::plain)
      .reduce(empty(), (x, y) -> simpleCat(simpleCat(x, line()), y));
  }

  /**
   * group tries laying out {@param doc} into a single line by removing the
   * contained line breaks; if this does not fit the page, or when a 'hardline'
   * within {@param doc} prevents it from being flattened, {@param doc} is laid out
   * without any changes.
   *
   * @param doc doc to flat
   * @return flatten document
   */
  @Contract("_ -> new")
  static @NotNull Doc group(@NotNull Doc doc) {
    if (doc instanceof Union) {
      return doc;

    } else if (doc instanceof FlatAlt alt) {
      var flattenResult = Flatten.flatDoc(alt.preferWhenFlatten());
      if (flattenResult instanceof Flatten.Flattened f) {
        return new Union(f.flattenedDoc(), alt.defaultDoc());

      } else if (flattenResult instanceof Flatten.AlreadyFlat) {
        return new Union(alt.preferWhenFlatten(), alt.defaultDoc());

      } else {
        return alt.defaultDoc();
      }

    } else {
      var flattenResult = Flatten.flatDoc(doc);
      if (flattenResult instanceof Flatten.Flattened f) {
        return new Union(f.flattenedDoc(), doc);

      } else {
        return doc;
      }
    }
  }

  /**
   * cat tries laying out the documents {@param docs} separated with nothing,
   * and if this does not fit the page, separates them with newlines. This is what
   * differentiates it from 'vcat', which always lays out its contents beneath
   * each other.
   *
   * @param docs documents to concat
   * @return cat document
   */
  @Contract("_ -> new")
  static @NotNull Doc cat(@NotNull Doc... docs) {
    return group(vcat(docs));
  }

  /**
   * vcat vertically concatenates the documents {@param docs}. If it is
   * 'group'ed, the line breaks are removed.
   * In other words vcat is like vsep, with newlines removed instead of
   * replaced by 'space's.
   *
   * <pre>
   * >>> let docs = Util.words "lorem ipsum dolor"
   * >>> vcat docs
   * lorem
   * ipsum
   * dolor
   * >>> group (vcat docs)
   * loremipsumdolor
   * </pre>
   * <p>
   * Since 'group'ing a 'vcat' is rather common, 'cat' is a built-in shortcut for
   * it.
   *
   * @param docs documents to concat
   * @return concat document
   */
  @Contract("_ -> new")
  static @NotNull Doc vcat(@NotNull Doc... docs) {
    return concatWith(
      (x, y) -> simpleCat(simpleCat(x, lineEmpty()), y),
      docs
    );
  }

  /**
   * hcat concatenates all documents docs horizontally without any spacing.
   *
   * @param docs documents to concat
   * @return concat document
   */
  @Contract("_ -> new")
  static @NotNull Doc hcat(@NotNull Doc... docs) {
    return concatWith(Doc::simpleCat, docs);
  }

  /**
   * sep tries laying out the documents {@param docs} separated with 'space's,
   * and if this does not fit the page, separates them with newlines. This is what
   * differentiates it from 'vsep', which always lays out its contents beneath
   * each other.
   *
   * <pre>
   * >>> let doc = "prefix" <+> sep ["text", "to", "lay", "out"]
   * >>> putDocW 80 doc
   * prefix text to lay out
   * </pre>
   * <p>
   * With a narrower layout, the entries are separated by newlines:
   *
   * <pre>
   * >>> putDocW 20 doc
   * prefix text
   * to
   * lay
   * out
   * </pre>
   *
   * @param docs documents to separate
   * @return separated documents
   */
  @Contract("_ -> new")
  static @NotNull Doc sep(@NotNull Doc... docs) {
    return group(vsep(docs));
  }

  /**
   * vsep concatenates all documents {@param docs} above each other. If a
   * 'group' undoes the line breaks inserted by vsep, the documents are
   * separated with a 'space' instead.
   * <p>
   * Using 'vsep' alone yields
   *
   * <pre>
   * >>> "prefix" <+> vsep ["text", "to", "lay", "out"]
   * prefix text
   * to
   * lay
   * out
   * </pre>
   * <p>
   * 'group'ing a 'vsep' separates the documents with a 'space' if it fits the
   * page (and does nothing otherwise). See the {@link Doc#sep(Doc...)} convenience
   * function for this use case.
   * <p>
   * The 'align' function can be used to align the documents under their first
   * element:
   *
   * <pre>
   * >>> "prefix" <+> align (vsep ["text", "to", "lay", "out"])
   * prefix text
   *        to
   *        lay
   *        out
   * </pre>
   * <p>
   * Since 'group'ing a 'vsep' is rather common, 'sep' is a built-in for doing
   * that.
   *
   * @param docs documents to separate
   * @return separated documents
   */
  @Contract("_ -> new")
  static @NotNull Doc vsep(@NotNull Doc... docs) {
    return concatWith(
      (x, y) -> simpleCat(simpleCat(x, line()), y),
      docs
    );
  }

  /**
   * hsep concatenates all documents {@param docs} horizontally with a space,
   * i.e. it puts a space between all entries.
   *
   * <pre>
   * >>> let docs = Util.words "lorem ipsum dolor sit amet"
   *
   * >>> hsep docs
   * lorem ipsum dolor sit amet
   *
   * </pre>
   * <p>
   * hsep does not introduce line breaks on its own, even when the page is too
   * narrow:
   *
   * <pre>
   * >>> putDocW 5 (hsep docs)
   * lorem ipsum dolor sit amet
   * </pre>
   *
   * @param docs documents to separate
   * @return separated documents
   */
  @Contract("_ -> new")
  static @NotNull Doc hsep(@NotNull Doc... docs) {
    return concatWith(Doc::simpleSpacedCat, docs);
  }

  /**
   * softline behaves like a {@code spaces(1)} if the resulting output fits the page,
   * otherwise like a {@code line()}.
   * <p>
   * For example, here, we have enough space to put everything in one line:
   *
   * <pre>
   * >>> let doc = "lorem ipsum" <> softline <> "dolor sit amet"
   * >>> putDocW 80 doc
   * lorem ipsum dolor sit amet
   * </pre>
   * If we narrow the page to width 10, the layouter produces a line break:
   * <pre>
   * >>> putDocW 10 doc
   * lorem ipsum
   * dolor sit amet
   * </pre>
   *
   * @return soft line document
   */
  @Contract("-> new")
  static @NotNull Doc softLine() {
    return new Union(plain(" "), line());
  }

  /**
   * Another softLine but result nothing on page when flattened.
   *
   * @return soft line document
   */
  @Contract("-> new")
  static @NotNull Doc softLineEmpty() {
    return new Union(empty(), line());
  }

  /**
   * Unconditionally line break
   *
   * @return hard line document
   */
  @Contract("-> new")
  static @NotNull Doc hardLine() {
    return new Line();
  }

  /**
   * The line document advances to the next line
   * and indents to the current nesting level.
   *
   * @return line document
   */
  @Contract("-> new")
  static @NotNull Doc line() {
    return new FlatAlt(new Line(), plain(" "));
  }

  /**
   * Another version of line() but result nothing on page when flattened.
   *
   * @return line document
   */
  @Contract("-> new")
  static @NotNull Doc lineEmpty() {
    return new FlatAlt(new Line(), empty());
  }

  /**
   * Insert a number of spaces. Negative values count as 0.
   *
   * @param count count of spaces
   * @return space document
   */
  @Contract("_ -> new")
  static @NotNull Doc spaces(int count) {
    return count < 0
      ? empty()
      : plain(" ".repeat(count));
  }

  //endregion

  //region utility functions

  private static @NotNull Doc concatWith(@NotNull BinaryOperator<Doc> f, @NotNull Doc... docs) {
    return Arrays.stream(docs).reduce(empty(), f);
  }

  private static @NotNull Doc simpleCat(@NotNull Doc a, @NotNull Doc b) {
    // optimization
    if (a instanceof Empty) {
      return b;
    }

    // optimization
    if (b instanceof Empty) {
      return a;
    }
    return new Cat(a, b);
  }

  private static @NotNull Doc simpleSpacedCat(@NotNull Doc a, @NotNull Doc b) {
    return simpleCat(simpleCat(a, plain(" ")), b);
  }

  //endregion
}
