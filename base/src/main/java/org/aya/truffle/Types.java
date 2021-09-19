// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.truffle;

import com.oracle.truffle.api.dsl.TypeSystem;

/**
 * @author zaoqi
 */
@TypeSystem({Value.Ctor.class, Value.Erased.class, Value.Fn.class, Value.Lam.class, Value.Struct.class, Value.StructDecl.class, Value.StructField.class, Value.Tup.class, Value.class})
public
class Types {
}
