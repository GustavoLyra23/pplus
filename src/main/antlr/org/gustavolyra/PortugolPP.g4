grammar PortugolPP;

programa
    : importarDeclaracao* declaracao* EOF
    ;


importarDeclaracao
    : 'importar' TEXTO_LITERAL ';'
    ;

declaracao
    : declaracaoClasse
    | declaracaoVar
    | declaracaoFuncao
    | declaracaoSe
    | chamadaFuncao ';'
    | bloco
    | expressao ';'
    | declaracaoReturn
    | declaracaoEnquanto
    | declaracaoPara
    | declaracaoFacaEnquanto
    | declaracaoQuebra
    | declaracaoContinue
    | declaracaoInterface
    ;


assinaturaMetodo
    : 'funcao' ID '(' listaParams? ')' (':' tipo)? ';'
    ;

declaracaoInterface
    : 'interface' ID '{' assinaturaMetodo* '}'
    ;

declaracaoQuebra
    : 'quebra' ';'
    ;

declaracaoContinue
    : 'continue' ';'
    ;

declaracaoFacaEnquanto
    : 'faca' declaracao 'enquanto' '(' expressao ')' ';'
    ;


declaracaoPara
    : 'para' '(' (declaracaoVar | expressao ';') expressao ';' expressao ')' declaracao
    ;

declaracaoEnquanto
    : 'enquanto' '(' expressao ')' declaracao
    ;

declaracaoSe
    : 'se' '(' expressao ')' declaracao ('senao' declaracao)?
    ;

declaracaoReturn
    : 'return' expressao? ';'
    ;

declaracaoClasse
    : 'classe' ID ('estende' ID)? ('implementa' ID (',' ID)*)? '{' (declaracaoVar | declaracaoFuncao)* '}'
    ;

declaracaoVar
    : 'var' ID (':' tipo)? ('=' expressao)? ';'
    ;

declaracaoFuncao
    : 'funcao' ID '(' listaParams? ')' (':' tipo)? bloco
    ;

listaParams
    : param (',' param)*
    ;

param
    : ID ':' tipo
    ;

tipo
    : 'Inteiro'
    | 'Real'
    | 'Texto'
    | 'Logico'
    | 'Lista'
    | 'Mapa'
    | ID
    ;

bloco
    : '{' declaracao* '}'
    ;

expressao
    : atribuicao
    ;

atribuicao
    : (ID | acesso | acessoArray) '=' expressao
    | logicaOu
    ;

acesso
    : primario '.' ID
    ;

acessoArray
    : primario '[' expressao ']'
    | primario '[' expressao ']' '[' expressao ']'
    ;

logicaOu
    : logicaE ('ou' logicaE)*
    ;

logicaE
    : igualdade ('e' igualdade)*
    ;

igualdade
    : comparacao (('==' | '!=') comparacao)*
    ;

comparacao
    : adicao (('<' | '<=' | '>' | '>=') adicao)*
    ;

adicao
    : multiplicacao (('+' | '-') multiplicacao)*
    ;

multiplicacao
    : unario (('*' | '/' | '%') unario)*
    ;

unario
    : ('!' | '-') unario
    | chamada
    ;

chamada
    : primario ('.' ID ('(' argumentos? ')')?)*
    | acessoArray
    ;

chamadaFuncao
    : ID '(' argumentos? ')'
    | primario '.' ID '(' argumentos? ')'
    ;

argumentos
    : expressao (',' expressao)*
    ;

primario
    : 'verdadeiro'
    | 'falso'
    | NUMERO
    | TEXTO_LITERAL
    | ID ('(' argumentos? ')')?
    | 'this'
    | '(' expressao ')'
    | 'nova' ID '(' argumentos? ')'
    | listaLiteral
    | mapaLiteral
    ;

listaLiteral
    : '[' ']'
    ;

mapaLiteral
    : '[[' ']' ']'
    ;


// Regras do lexer
ID: [a-zA-Z_][a-zA-Z0-9_]*;
NUMERO: [0-9]+ ('.' [0-9]*)? | '.' [0-9]+; // Ajustado para suportar 42., .5, etc.
TEXTO_LITERAL: '"' (~["\r\n] | '\\"')* '"';
ESPACO: [ \t\r\n\f]+ -> skip;
COMENTARIO: '//' ~[\r\n]* -> skip;
COMENTARIO_BLOCO: '/*' .*? '*/' -> skip;