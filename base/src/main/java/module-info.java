module org.aya {
  requires static org.jetbrains.annotations;
  requires static org.antlr.antlr4.runtime;
  requires org.commonmark;
  requires org.graalvm.truffle;

  requires transitive org.aya.api;
  requires transitive org.aya.parser;
  requires transitive org.aya.pretty;
  requires transitive kala.base;
  requires transitive kala.collection;

  exports org.aya.concrete.desugar;
  exports org.aya.core.ops;
  exports org.aya.concrete.desugar.error;
  exports org.aya.concrete.parse;
  exports org.aya.concrete.remark;
  exports org.aya.concrete.resolve.context;
  exports org.aya.concrete.resolve.module;
  exports org.aya.concrete.stmt;
  exports org.aya.concrete.visitor;
  exports org.aya.concrete;
  exports org.aya.core.def;
  exports org.aya.core.pat;
  exports org.aya.core.serde;
  exports org.aya.core.sort;
  exports org.aya.core.term;
  exports org.aya.core.visitor;
  exports org.aya.core;
  exports org.aya.distill;
  exports org.aya.generic;
  exports org.aya.prelude;
  exports org.aya.tyck.pat;
  exports org.aya.tyck.trace;
  exports org.aya.tyck.unify.level;
  exports org.aya.tyck.unify;
  exports org.aya.tyck;
  exports org.aya.util.cancel;
  exports org.aya.util;
}
