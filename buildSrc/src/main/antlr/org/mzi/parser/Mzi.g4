// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
grammar Mzi;

program : stmt* EOF;

// statements
stmt : decl
     | cmd
     | module
     ;

cmdModifier : PUBLIC? OPEN;
cmd : (cmdModifier | cmdModifier? IMPORT) moduleName useHide?;
useHide : use+
        | hide+;
use : USING useHideList;
hide : HIDING useHideList;
useHideList : LPAREN ids ')';

moduleName : ID ('.' ID)*;

// declarations

decl : PRIVATE?
     ( fnDecl
     | structDecl
     | dataDecl
     );

assoc : INFIX
      | INFIXL
      | INFIXR
      | FIX
      | FIXL
      | FIXR
      | TWIN
      ;

abuse : '\\abusing' (LBRACE stmt* '}' | stmt);

fnDecl : '\\def' fnModifiers* assoc? ID tele* type? fnBody abuse?;

fnBody : IMPLIES expr;

fnModifiers : ERASE
            | INLINE
            ;

structDecl : OPEN? '\\structure' ID fieldTele* ('\\extends' ids)? ('|' field)* abuse?;

fieldTeleInner : COERCE? ID+ type;
fieldTele : LPAREN fieldTeleInner ')'
          | LBRACE fieldTeleInner '}'
          ;

field : COERCE? ID tele* type   # fieldDecl
      | ID tele* IMPLIES expr   # fieldImpl
      ;

dataDecl : OPEN? '\\data' ID tele* type? dataBody abuse?;

dataBody : ('|' dataCtor)*       # dataCtors
         | elim dataCtorClause*  # dataClauses
         ;

// TODO[imkiva]: some code commented in Arend.g4
dataCtor : COERCE? ID tele* (elim? LBRACE clause? ('|' clause)* '}')?;

elim : '\\elim' ID (',' ID)*;

dataCtorClause : '|' pattern IMPLIES dataCtor;

module : '\\module' ID LBRACE stmt* '}';

// expressions
expr : atom argument*                                 # app
     | <assoc=right> expr TO expr                     # arr
     | <assoc=right> expr '.' NUMBER                  # proj
     | PI tele+ TO expr                               # pi
     | SIGMA tele+ '**' expr                          # sigma
     | LAMBDA tele+ (IMPLIES expr?)?                  # lam
     | MATCH matchArg (',' matchArg)* ('|' clause)+   # match
     ;

matchArg : elim
         | expr
         ;

typed : expr type? ;

atom : literal
     | LPAREN (typed ',')? typed? ')'
     ;

argument : atom
         | LBRACE (typed ',')? typed? '}'
         | '.' idFix
         ;

clause : patterns IMPLIES expr
       | ABSURD;

patterns : pattern (',' pattern)* ;
pattern : atomPattern+ (AS ID type?)?
        ;

atomPattern : LPAREN patterns? ')'
            | LBRACE patterns '}'
            | NUMBER
            | ID
            | CALM_FACE
            ;

literal : ID
        | PROP
        | CALM_FACE
        | idFix
        | LGOAL expr? '?}'
        | NUMBER
        | STRING
        | UNIVERSE
        | SET_UNIV
        ;

tele : literal
     | LPAREN teleMaybeTypedExpr ')'
     | LBRACE teleMaybeTypedExpr '}'
     ;

teleMaybeTypedExpr : ids type?;

// utilities
ids : (ID ',')* ID?;
type : ':' expr;

// operators
idFix : INFIX | POSTFIX | ID;
INFIX : '`' ID '`';
POSTFIX : '`' ID;

// associativities
INFIXN : '\\infix';
INFIXL : '\\infixl';
INFIXR : '\\infixr';
FIX : '\\fix';
FIXL : '\\fixl';
FIXR : '\\fixr';
TWIN : '\\twin';

// universe
UNIVERSE : '\\' (NUMBER '-' | 'oo-' | 'h' | 'h-')? 'Type' NUMBER?;
SET_UNIV : '\\Set' NUMBER?;
PROP : '\\Prop';

// other keywords
AS : '\\as';
OPEN : '\\open';
IMPORT : '\\import';
PUBLIC : '\\public';
PRIVATE : '\\private';
USING : '\\using';
HIDING : '\\hiding';
COERCE : '\\coerce';
ERASE : '\\erase';
INLINE : '\\inline';
SIGMA : '\\Sig' | '\u03A3';
LAMBDA : '\\lam' | '\u03BB';
PI : '\\Pi' | '\u03A0';
MATCH : '\\match';
ABSURD : '\\impossible';
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
fragment START_CHAR : [~!@#$%^&*\-+=<>?/|:[\u005Da-zA-Z_\u2200-\u22FF];
ID : START_CHAR (START_CHAR | [0-9'])*;

// whitespaces
WS : [ \t\r\n]+ -> channel(HIDDEN);
LINE_COMMENT : '--' '-'* (~[~!@#$%^&*\-+=<>?/|:[\u005Da-zA-Z_0-9'\u2200-\u22FF\r\n] ~[\r\n]* | ) -> skip;
COMMENT : '{-' (COMMENT|.)*? '-}' -> channel(HIDDEN);
