// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.MaterializedFrame;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple2;
import org.aya.api.ref.Var;
import org.aya.core.def.Def;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author zaoqi
 */
@TruffleLanguage.Registration(id = "aya", name = "Aya", characterMimeTypes = AyaTruffleLanguage.MIME_TYPE, defaultMimeType = AyaTruffleLanguage.MIME_TYPE)
public final class AyaTruffleLanguage extends TruffleLanguage<Void> {
  public static final String MIME_TYPE = "application/x-aya";

  // TODO[zaoqi]: Creating a TruffleLanguage instance is not necessary? I am not sure about this.

  @Override
  protected @Nullable Void createContext(Env env) {
    return null;
  }
}
