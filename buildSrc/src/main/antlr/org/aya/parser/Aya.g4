// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
grammar Aya;

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

importCmd : IMPORT moduleName (AS ID)?;
openCmd : PUBLIC? OPEN IMPORT? moduleName useHide?;
module : 'module' ID LBRACE stmt* '}';
bind : 'bind' bindOp (TIGHTER | LOOSER) bindOp;
bindOp : qualifiedId | 'def';

useHide : use+
        | hide+;
use : USING useHideList;
hide : HIDING useHideList;
useHideList : LPAREN idsComma ')';
moduleName : ID ('.' ID)*;

levels : (ULEVEL | HLEVEL) ids ;
generalize : 'variable' ids type ;

// declarations

decl : PRIVATE?
     ( fnDecl
     | structDecl
     | dataDecl
     | primDecl
     );

assoc : INFIX
      | INFIXL
      | INFIXR
      | FIX
      | FIXL
      | FIXR
      | TWIN
      ;

abuse : 'abusing' (LBRACE stmt* '}' | stmt);

fnDecl : 'def' fnModifiers* assoc? ID tele* type? fnBody abuse?;

fnBody : IMPLIES expr
       | ('|' clause)+ ;

fnModifiers : ERASE
            | INLINE
            ;

structDecl : 'struct' assoc? ID tele* type? ('extends' idsComma)? ('|' field)* abuse?;

primDecl : 'prim' assoc? ID tele* type? ;

field : COERCE? ID tele* type clauses? # fieldDecl
      | ID tele* type? IMPLIES expr    # fieldImpl
      ;

dataDecl : (PUBLIC? OPEN)? 'data' assoc? ID tele* type? dataBody* abuse?;

dataBody : ('|' dataCtor)       # dataCtors
         | dataCtorClause       # dataClauses
         ;

dataCtor : COERCE? assoc? ID tele* clauses?;

dataCtorClause : '|' patterns IMPLIES dataCtor;

// expressions
expr : atom                            # single
     | expr argument+                  # app
     | 'new' expr '{' newArg* '}'      # new
     | <assoc=right> expr TO expr      # arr
     | expr projFix                    # proj
     | 'lsuc' expr                     # lsuc
     | 'lmax' expr+                    # lmax
     | PI tele+ TO expr                # pi
     | SIGMA tele+ '**' expr           # sigma
     | LAMBDA tele+ (IMPLIES expr?)?   # lam
     | MATCH expr (',' expr)* clauses  # match
     ;

newArg : '|' ID ids IMPLIES expr;

atom : literal
     | LPAREN (expr ',')* expr? ')'
     ;

argument : atom projFix*
         | LBRACE (expr ',')* expr? '}'
         | LBRACE ID IMPLIES expr? '}'
         ;

projFix : '.' (NUMBER | ID);

clauses : LBRACE clause? ('|' clause)* '}' ;
clause : patterns (IMPLIES expr)? ;

patterns : pattern (',' pattern)* ;
pattern : atomPatterns
        ;

atomPatterns : atomPattern+ ;
atomPattern : LPAREN patterns ')' (AS ID)?
            | LBRACE patterns '}' (AS ID)?
            | NUMBER
            | ABSURD
            | ID
            | CALM_FACE
            ;

literal : qualifiedId
        | PROP
        | CALM_FACE
        | idFix
        | LGOAL expr? '?}'
        | NUMBER
        | STRING
        | HTYPE
        | UTYPE
        | TYPE
        | INF_TYPE
        | SET_UNIV
        ;

tele : literal
     | LPAREN teleBinder ')'
     | LBRACE teleMaybeTypedExpr '}'
     ;

// Explicit arguments may be anonymous
teleBinder : expr
           | teleMaybeTypedExpr ;

teleMaybeTypedExpr : ids type?;

// utilities
idsComma : (ID ',')* ID?;
ids : ID*;
type : ':' expr;

// operators
idFix : INFIX | POSTFIX | ID;
INFIX : '`' ID '`';
POSTFIX : '`' ID;

// bind
TIGHTER : 'tighter';
LOOSER : 'looser';

// samples
EXAMPLE : 'example';
COUNTEREXAMPLE : 'counterexample';

// associativities
INFIXN : 'infix';
INFIXL : 'infixl';
INFIXR : 'infixr';
FIX : 'fix';
FIXL : 'fixl';
FIXR : 'fixr';
TWIN : 'twin';

// universe
HTYPE : 'hType';
UTYPE : 'uType';
ULEVEL : 'ulevel';
HLEVEL : 'hlevel';
TYPE : 'Type';
INF_TYPE : 'ooType';
SET_UNIV : 'Set';
PROP : 'Prop';

// other keywords
AS : 'as';
OPEN : 'open';
IMPORT : 'import';
PUBLIC : 'public';
PRIVATE : 'private';
USING : 'using';
HIDING : 'hiding';
COERCE : 'coerce';
ERASE : 'erase';
INLINE : 'inline';
SIGMA : 'Sig' | '\u03A3';
LAMBDA : '\\' | '\u03BB';
PI : 'Pi' | '\u03A0';
MATCH : 'match';
ABSURD : 'impossible';
TO : '->' | '\u2192';
IMPLIES : '=>' | '\u21D2';

// markers
LBRACE : '{';
LPAREN : '(';
LGOAL : '{?';

// literals
NUMBER : [0-9]+;
CALM_FACE : '_';
STRING : INCOMPLETE_STRING '"';
INCOMPLETE_STRING : '"' (~["\\\r\n] | ESCAPE_SEQ | EOF)*;
fragment ESCAPE_SEQ : '\\' [btnfr"'\\] | OCT_ESCAPE | UNICODE_ESCAPE;
fragment OCT_ESCAPE : '\\' OCT_DIGIT OCT_DIGIT? | '\\' [0-3] OCT_DIGIT OCT_DIGIT;
fragment UNICODE_ESCAPE : '\\' 'u'+ HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT;
fragment HEX_DIGIT : [0-9a-fA-F];
fragment OCT_DIGIT : [0-8];

// identifier
fragment AYA_SIMPLE_LETTER : [~!@#$%^&*\-+=<>?/|[\u005Da-zA-Z_\u2200-\u22FF];
fragment AYA_UNICODE : [\u0080-\uFEFE] | [\uFF00-\u{10FFFF}]; // exclude U+FEFF which is a truly invisible char
fragment AYA_LETTER : AYA_SIMPLE_LETTER | AYA_UNICODE;
ID : AYA_LETTER (AYA_LETTER | [0-9'])*;
qualifiedId : ID ('::' ID)*;

// whitespaces
WS : [ \t\r\n]+ -> channel(HIDDEN);
LINE_COMMENT : '--' '-'* (~[~!@#$%^&*\-+=<>?/|:[\u005Da-zA-Z_0-9'\u2200-\u22FF\r\n] ~[\r\n]* | ) -> skip;
COMMENT : '{-' (COMMENT|.)*? '-}' -> channel(HIDDEN);
