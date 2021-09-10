// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.doc;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import org.aya.pretty.backend.html.DocHtmlPrinter;
import org.aya.pretty.backend.latex.DocTeXPrinter;
import org.aya.pretty.backend.string.LinkId;
import org.aya.pretty.backend.string.StringPrinter;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.backend.string.style.DebugStylist;
import org.aya.pretty.printer.Printer;
import org.aya.pretty.printer.PrinterConfig;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntFunction;
import java.util.function.Supplier;

import static org.aya.pretty.printer.PrinterConfig.INFINITE_SIZE;

/**
 * This class reimplemented Haskell
 * <a href="https://hackage.haskell.org/package/prettyprinter-1.7.0/docs/src/Prettyprinter.Internal.html">
 * PrettyPrint library's Doc module</a>.
 *
 * @author kiva
 */
public sealed interface Doc extends Docile {
  @NotNull Doc ONE_WS = plain(" ");
  @NotNull Doc ALT_WS = flatAlt(ONE_WS, line());
  private static boolean nonEmpty(Doc doc) {
    return doc instanceof Empty;
  }
  @Override default @NotNull Doc toDoc() {
    return this;
  }
  default @NotNull SeqLike<Doc> asSeq() {
    return Seq.of(this);
  }

  //region Doc APIs
  default @NotNull String renderToString(@NotNull StringPrinterConfig config) {
    var printer = new StringPrinter<>();
    return this.render(printer, config);
  }

  default @NotNull String renderToHtml() {
    return renderToHtml(true);
  }

  default @NotNull String renderToHtml(boolean withHeader) {
    var printer = new DocHtmlPrinter();
    return this.render(printer, new DocHtmlPrinter.Config(withHeader));
  }

  default @NotNull String renderToTeX() {
    var printer = new DocTeXPrinter();
    return this.render(printer, new DocTeXPrinter.Config());
  }

  default <Out, Config extends PrinterConfig>
  @NotNull Out render(@NotNull Printer<Out, Config> printer,
                      @NotNull Config config) {
    return printer.render(config, this);
  }

  default @NotNull String renderWithPageWidth(int pageWidth, boolean unicode) {
    var config = new StringPrinterConfig(DebugStylist.INSTANCE, pageWidth, unicode);
    return this.renderToString(config);
  }

  /** Produce ASCII and infinite-width output */
  default @NotNull String debugRender() {
    return renderWithPageWidth(INFINITE_SIZE, false);
  }

  /** Produce unicode and 80-width output */
  default @NotNull String commonRender() {
    return renderWithPageWidth(80, true);
  }

  //endregion

  //region Doc Variants

  /** The empty document; conceptually the unit of 'Cat' */
  record Empty() implements Doc {
    static final @NotNull Empty INSTANCE = new Empty();

    @Override public @NotNull SeqLike<Doc> asSeq() {
      return Seq.of();
    }
  }

  /**
   * A plain text line without '\n'.
   */
  record PlainText(@NotNull String text) implements Doc {
  }

  /**
   * A special symbol that may get rendered in a special way
   *
   * @author ice1000
   */
  record SpecialSymbol(@NotNull String text) implements Doc {
  }

  /**
   * A clickable text line without '\n'.
   */
  record HyperLinked(@NotNull Doc doc, @NotNull LinkId link, @Nullable String id) implements Doc {
    @Override public String toString() {
      return doc.toString();
    }
  }

  /**
   * Styled document
   */
  record Styled(@NotNull Seq<Style> styles, @NotNull Doc doc) implements Doc {
  }

  /**
   * Hard line break
   */
  record Line() implements Doc {
    public static final @NotNull Line INSTANCE = new Line();
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
  record Cat(@NotNull ImmutableSeq<Doc> inner) implements Doc {
    @Override public @NotNull SeqLike<Doc> asSeq() {
      return inner.view().flatMap(Doc::asSeq);
    }
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
  record Column(@NotNull IntFunction<Doc> docBuilder) implements Doc {
  }

  /**
   * A document that will react on the current nest level.
   */
  record Nesting(@NotNull IntFunction<Doc> docBuilder) implements Doc {
  }

  /**
   * A document that will react on the page width.
   */
  record PageWidth(@NotNull IntFunction<Doc> docBuilder) implements Doc {
  }

  //endregion

  //region DocFactory functions
  static @NotNull Doc linkDef(@NotNull Doc doc, int hashCode) {
    return new HyperLinked(doc, new LinkId("#" + hashCode), String.valueOf(hashCode));
  }

  static @NotNull Doc linkRef(@NotNull Doc doc, int hashCode) {
    return new HyperLinked(doc, new LinkId("#" + hashCode), null);
  }

  static @NotNull Doc hyperLink(@NotNull Doc doc, @NotNull LinkId link) {
    return new HyperLinked(doc, link, null);
  }

  static @NotNull Doc hyperLink(@NotNull String plain, @NotNull LinkId link) {
    return hyperLink(Doc.plain(plain), link);
  }

  static @NotNull Doc styled(@NotNull Style style, @NotNull Doc doc) {
    return new Doc.Styled(Seq.of(style), doc);
  }

  static @NotNull Doc styled(@NotNull Style style, @NotNull String plain) {
    return new Doc.Styled(Seq.of(style), Doc.plain(plain));
  }

  static @NotNull Doc styled(@NotNull Styles builder, @NotNull Doc doc) {
    return new Doc.Styled(builder.styles, doc);
  }

  static @NotNull Doc styled(@NotNull Styles builder, @NotNull String plain) {
    return new Doc.Styled(builder.styles, Doc.plain(plain));
  }

  static @NotNull Doc licit(boolean explicit, Doc doc) {
    return wrap(explicit ? "(" : "{", explicit ? ")" : "}", doc);
  }

  static @NotNull Doc wrap(String leftSymbol, String rightSymbol, Doc doc) {
    return Doc.cat(Doc.symbol(leftSymbol), doc, Doc.symbol(rightSymbol));
  }

  static @NotNull Doc braced(Doc doc) {
    return wrap("{", "}", doc);
  }
  static @NotNull Doc angled(Doc doc) {
    return wrap("<", ">", doc);
  }
  static @NotNull Doc parened(Doc doc) {
    return wrap("(", ")", doc);
  }

  /**
   * Return conditional {@link Doc#empty()}
   *
   * @param cond      condition
   * @param otherwise otherwise
   * @return {@link Empty} when {@code cond} is true, otherwise {@code otherwise}
   */
  static @NotNull Doc emptyIf(boolean cond, Supplier<@NotNull Doc> otherwise) {
    return cond ? empty() : otherwise.get();
  }

  /**
   * The empty document; conceptually the unit of 'Cat'
   *
   * @return empty document
   */
  static @NotNull Doc empty() {
    return Empty.INSTANCE;
  }

  /**
   * By default, flatAlt renders as {@param defaultDoc}. However, when 'group'-ed,
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
   * @param docBuilder document generator when current position provided
   * @return column action document
   */
  @Contract("_ -> new")
  static @NotNull Doc column(@NotNull IntFunction<Doc> docBuilder) {
    return new Column(docBuilder);
  }

  /**
   * Layout a document depending on the current 'nest'-ing level.
   * {@link Doc#align(Doc)} is implemented in terms of {@code nesting}.
   *
   * @param docBuilder document generator when current nest level provided
   * @return nest level action document
   */
  @Contract("_ -> new")
  static @NotNull Doc nesting(@NotNull IntFunction<Doc> docBuilder) {
    return new Nesting(docBuilder);
  }

  /**
   * Layout a document depending on the page width, if one has been specified.
   *
   * @param docBuilder document generator when page width provided
   * @return page width action document
   */
  @Contract("_ -> new")
  static @NotNull Doc pageWidth(@NotNull IntFunction<Doc> docBuilder) {
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
    return indent == 0 ? doc : new Nest(indent, doc);
  }

  /**
   * align lays out the document {@param doc} with the nesting level set to the
   * current column. It is used for example to implement {@link Doc#hang(int, Doc)}.
   * <p>
   * As an example, we will put a document right above another one, regardless of
   * the current nesting level. Without 'align'-ment, the second line is put simply
   * below everything we've had so far,
   * <p>
   * If we add an 'align' to the mix, the @'vsep'@'s contents all start in the
   * same column,
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
   * <p>
   * This differs from {@link Doc#nest(int, Doc)}, which is based on
   * the /current nesting level/ plus {@code indent}.
   * When you're not sure, try the more efficient 'nest' first. In our
   * example, this would yield
   *
   * @param deltaNest change of nesting level, relative to the start of the first line
   * @param doc       document to indent
   * @return hang-ed document
   */
  @Contract("_, _ -> new")
  static @NotNull Doc hang(int deltaNest, @NotNull Doc doc) {
    return align(nest(deltaNest, doc));
  }

  @Contract("_ -> new")
  static @NotNull Doc ordinal(int n) {
    var m = n % 100;
    if (m >= 4 && m <= 20) return Doc.plain(n + "th");
    return Doc.plain(n + switch (n % 10) {
      case 1 -> "st";
      case 2 -> "nd";
      case 3 -> "rd";
      default -> "th";
    });
  }

  /**
   * Plain text document
   *
   * @param text text that may not contain '\n'
   * @return text document of the whole text
   */
  @Contract("_ -> new") static @NotNull Doc plain(String text) {
    return new PlainText(text);
  }

  @Contract("_ -> new") static @NotNull Doc english(String text) {
    if (!text.contains(" ")) return plain(text);
    return sep(Seq.from(text.split(" ", -1)).view().map(Doc::plain));
  }

  /**
   * @param text '\n' not allowed!
   * @return special symbol
   */
  @Contract("_ -> new") static @NotNull Doc symbol(String text) {
    assert !text.contains("\n");
    return new SpecialSymbol(text);
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
  @Contract("_ -> new") static @NotNull Doc cat(@NotNull SeqLike<Doc> docs) {
    return simpleCat(docs);
  }

  /** @see Doc#cat(Doc...) */
  @Contract("_ -> new") static @NotNull Doc cat(Doc @NotNull ... docs) {
    return cat(Seq.of(docs));
  }

  @Contract("_ -> new") static @NotNull Doc vcat(Doc @NotNull ... docs) {
    return join(line(), docs);
  }

  @Contract("_ -> new") static @NotNull Doc vcat(@NotNull SeqLike<@NotNull Doc> docs) {
    return join(line(), docs);
  }

  /**
   * stickySep concatenates all documents {@param docs} horizontally with a space,
   * i.e. it puts a space between all entries.
   * <p>
   * stickySep does not introduce line breaks on its own, even when the page is too narrow:
   *
   * @param docs documents to separate
   * @return separated documents
   */
  @Contract("_ -> new") static @NotNull Doc stickySep(@NotNull SeqLike<@NotNull Doc> docs) {
    return join(ONE_WS, docs);
  }

  @Contract("_ -> new") static @NotNull Doc stickySep(Doc @NotNull ... docs) {
    return join(ONE_WS, docs);
  }

  /**
   * fillSep concatenates the documents {@param docs} horizontally with a space
   * as long as it fits the page, then inserts a 'line' and continues doing that
   * for all documents in {@param docs}. ('line' means that if 'group'ed, the documents
   * are separated with a 'space' instead of newlines. Use {@link Doc#cat}
   * if you do not want a 'space'.
   * <p>
   * Let's print some words to fill the line:
   *
   * @param docs documents to separate
   * @return separated documents
   */
  @Contract("_ -> new") static @NotNull Doc sep(Doc @NotNull ... docs) {
    return join(ALT_WS, docs);
  }

  @Contract("_ -> new") static @NotNull Doc sep(@NotNull SeqLike<Doc> docs) {
    return join(ALT_WS, docs);
  }

  @Contract("_ -> new") static @NotNull Doc sepNonEmpty(Doc @NotNull ... docs) {
    return sepNonEmpty(Seq.of(docs));
  }

  @Contract("_ -> new") static @NotNull Doc sepNonEmpty(@NotNull SeqLike<Doc> docs) {
    return sep(docs.view().filterNot(Doc::nonEmpty));
  }

  @Contract("_ -> new") static @NotNull Doc commaList(@NotNull SeqLike<Doc> docs) {
    return join(new Cat(ImmutableSeq.of(Doc.plain(","), ALT_WS)), docs);
  }

  @Contract("_, _ -> new") static @NotNull Doc join(@NotNull Doc delim, Doc @NotNull ... docs) {
    return join(delim, Seq.of(docs));
  }

  @Contract("_, _ -> new")
  static @NotNull Doc join(@NotNull Doc delim, @NotNull SeqLike<@NotNull Doc> docs) {
    if (docs.size() == 0) return Doc.empty();
    var first = docs.first();
    if (docs.size() == 1) return first;
    return simpleCat(docs.view().drop(1).foldLeft(Buffer.of(first), (l, r) -> {
      l.append(delim);
      l.append(r);
      return l;
    }));
  }

  /**
   * Unconditionally line break
   *
   * @return hard line document
   */
  @Contract("-> new")
  static @NotNull Doc line() {
    return Line.INSTANCE;
  }

  //endregion
  private static @NotNull Doc simpleCat(@NotNull SeqLike<@NotNull Doc> xs) {
    return new Cat(xs.view().flatMap(Doc::asSeq).toImmutableArray());
  }
}
