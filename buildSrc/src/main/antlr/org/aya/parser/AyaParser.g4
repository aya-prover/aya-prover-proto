// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
parser grammar AyaParser;

options { tokenVocab = AyaLexer; }

program : stmt* EOF;

// statements
stmt : decl
     | importCmd
     | openCmd
     | module
     | levels
     | generalize
     | bind
     | sample
     ;

sample : (EXAMPLE | COUNTEREXAMPLE) decl ;

importCmd : IMPORT qualifiedId (AS ID)?;
openCmd : PUBLIC? OPEN IMPORT? qualifiedId useHide?;
module : MODULE_KW ID LBRACE stmt* RBRACE;
bind : BIND_KW bindOp (TIGHTER | LOOSER) bindOp;
bindOp : qualifiedId | OP_APP;

useHide : use+
        | hide+;
use : USING useHideList;
hide : HIDING useHideList;
useHideList : LPAREN idsComma RPAREN;

levels : (ULEVEL | HLEVEL) ids ;
generalize : VARIABLE ids type ;

// declarations

decl : PRIVATE?
     ( fnDecl
     | structDecl
     | dataDecl
     | primDecl
     );

assoc : infix
      | INFIXL
      | INFIXR
      | FIX
      | FIXL
      | FIXR
      | TWIN
      ;

abuse : ABUSING (LBRACE stmt* RBRACE | stmt);

fnDecl : DEF fnModifiers* assoc? ID tele* type? fnBody abuse?;

fnBody : IMPLIES expr
       | (BAR clause)+ ;

fnModifiers : ERASE
            | INLINE
            ;

structDecl : STRUCT assoc? ID tele* type? (EXTENDS idsComma)? (BAR field)* abuse?;

primDecl : PRIM assoc? ID tele* type? ;

field : COERCE? ID tele* type clauses? # fieldDecl
      | ID tele* type? IMPLIES expr    # fieldImpl
      ;

dataDecl : (PUBLIC? OPEN)? DATA assoc? ID tele* type? dataBody* abuse?;

dataBody : (BAR dataCtor)       # dataCtors
         | dataCtorClause       # dataClauses
         ;

dataCtor : COERCE? assoc? ID tele* clauses?;

dataCtorClause : BAR patterns IMPLIES dataCtor;

// expressions
expr : atom                            # single
     | expr argument+                  # app
     | NEW_KW expr LBRACE newArg* RBRACE # new
     | <assoc=right> expr TO expr      # arr
     | expr projFix                    # proj
     | LSUC_KW expr                    # lsuc
     | LMAX_KW expr+                   # lmax
     | PI tele+ TO expr                # pi
     | SIGMA tele+ SUCHTHAT expr       # sigma
     | LAMBDA tele+ (IMPLIES expr?)?   # lam
     | MATCH expr (COMMA expr)* clauses  # match
     ;

newArg : BAR ID ids IMPLIES expr;

atom : literal
     | LPAREN (expr COMMA)* expr? RPAREN
     ;

argument : atom projFix*
         | LBRACE (expr COMMA)* expr? RBRACE
         | LBRACE ID IMPLIES expr? RBRACE
         ;

projFix : DOT (NUMBER | ID);

clauses : LBRACE clause? (BAR clause)* RBRACE ;
clause : patterns (IMPLIES expr)? ;

patterns : pattern (COMMA pattern)* ;
pattern : atomPatterns
        ;

atomPatterns : atomPattern+ ;
atomPattern : LPAREN patterns RPAREN (AS ID)?
            | LBRACE patterns RBRACE (AS ID)?
            | NUMBER
            | ABSURD
            | ID
            | CALM_FACE
            ;

literal : qualifiedId
        | PROP
        | CALM_FACE
        | idFix
        | LGOAL expr? RGOAL
        | NUMBER
        | STRING
        | HTYPE
        | UTYPE
        | TYPE
        | INF_TYPE
        | SET_UNIV
        ;

tele : literal
     | LPAREN teleBinder RPAREN
     | LBRACE teleMaybeTypedExpr RBRACE
     ;

// Explicit arguments may be anonymous
teleBinder : expr
           | teleMaybeTypedExpr ;

teleMaybeTypedExpr : ids type?;

// utilities
idsComma : (ID COMMA)* ID?;
ids : ID*;
type : COLON expr;

// operators
idFix : infix | BACKTICK ID | ID;
infix : BACKTICK ID BACKTICK;

qualifiedId : ID (COLON2 ID)*;
