package org.gustavolyra.portugolpp

import models.Ambiente
import models.Valor
import models.enums.LOOP
import models.errors.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.gustavolyra.portugolpp.PortugolPPParser.*
import processarResultado
import processors.comparar
import processors.processarAdicao
import processors.processarMultiplicacao
import processors.saoIguais
import setFuncoes
import java.io.File


@Suppress("REDUNDANT_OVERRIDE", "ABSTRACT_MEMBER_NOT_IMPLEMENTED")
class Interpretador : PortugolPPBaseVisitor<Valor>() {
    /** Ambiente global que contém todas as definições de classes, interfaces e funções globais */
    private var global = Ambiente()

    /** Ambiente atual de execução, pode ser o global ou um escopo local */
    private var ambiente = global

    /** Referência para a função atualmente em execução (usado para verificação de tipos de retorno) */
    private var funcaoAtual: Valor.Funcao? = null

    private val arquivosImportados = mutableSetOf<String>()

    //setando funcoes nativas da linguagem...
    init {
        setFuncoes(global)
    }

    override fun visitImportarDeclaracao(ctx: ImportarDeclaracaoContext): Valor {
        val nomeArquivo = ctx.TEXTO_LITERAL().text.removeSurrounding("\"")
        processarImport(nomeArquivo)
        return Valor.Nulo
    }

    private fun processarDeclaracoesDoArquivo(tree: ProgramaContext) {
        tree.declaracao().forEach { declaracao ->
            declaracao.declaracaoInterface()?.let {
                visitDeclaracaoInterface(it)
            }
        }

        tree.declaracao()?.forEach { declaracao ->
            declaracao.declaracaoClasse()?.let {
                visitDeclaracaoClasse(it)
            }
        }

        tree.declaracao()?.forEach { declaracao ->
            declaracao.declaracaoFuncao()?.let {
                visitDeclaracaoFuncao(it)
            }
        }

        tree.declaracao()?.forEach { declaracao ->
            declaracao.declaracaoVar()?.let {
                visitDeclaracaoVar(it)
            }
        }
    }


    fun processarImport(nomeArquivo: String) {
        println("caminhoCompleto -> $nomeArquivo")
        if (arquivosImportados.contains(nomeArquivo)) {
            println("Arquivo ja importado...")
            return
        }

        arquivosImportados.add(nomeArquivo)

        try {
            val conteudo = File(nomeArquivo).readText()
            val lexer = PortugolPPLexer(CharStreams.fromString(conteudo))
            val tokens = CommonTokenStream(lexer)
            val parser = PortugolPPParser(tokens)
            val arvore = parser.programa()

            arvore.importarDeclaracao().forEach { import ->
                visitImportarDeclaracao(import)
            }

            processarDeclaracoesDoArquivo(arvore)
        } catch (e: Exception) {
            throw SemanticError(e.message)
        }
    }

    fun interpretar(tree: ProgramaContext) {
        try {
            tree.importarDeclaracao()?.forEach { import ->
                visitImportarDeclaracao(import)
            }

            visitInterfaces(tree)
            visitClasses(tree)
            //visitando outras declaracoes mais genericas...
            tree.declaracao().forEach { visit(it) }
            visitFuncaoMain()
        } catch (e: Exception) {
            println(e)
        }
    }

    private fun visitInterfaces(tree: ProgramaContext) {
        tree.declaracao().forEach { decl ->
            decl.declaracaoInterface()?.let {
                val nome = it.ID().text
                global.definirInterface(nome, it)
            }
        }
    }

    //TODO: implementar static...
    private fun visitClasses(tree: ProgramaContext) {
        tree.declaracao().forEach { decl ->
            decl.declaracaoClasse()?.let {
                val nome = it.ID(0).text
                global.definirClasse(nome, it)
            }
        }
    }

    private fun visitFuncaoMain() {
        try {
            val main = global.obter("main")
            if (main is Valor.Funcao) {
                //TODO: refatorar.... os argumentos da funcao main serao ignorados...
                chamadaFuncao("main", emptyList())
            }
        } catch (_: Exception) {
            throw MainExecutionException("Falha durante a execução da função main")
        }
    }

    override fun visitDeclaracaoInterface(ctx: DeclaracaoInterfaceContext): Valor {
        val nomeInterface = ctx.ID().text
        global.definirInterface(nomeInterface, ctx)
        return Valor.Nulo
    }

    override fun visitDeclaracaoTentarCapturar(ctx: DeclaracaoTentarCapturarContext?): Valor? {
        try {
            visit(ctx?.bloco(0))
        } catch (_: Exception) {
            visit(ctx?.bloco(1))
        }
        return Valor.Nulo
    }


    override fun visitDeclaracaoClasse(ctx: DeclaracaoClasseContext): Valor {
        val nomeClasse = ctx.ID(0).text
        var superClasse: String?

        if (ctx.childCount > 3 && ctx.getChild(2).text == "estende") {
            superClasse = ctx.getChild(3).text
            global.obterClasse(superClasse)
                ?: throw SemanticError("Classe base '$superClasse' não encontrada para a classe '$nomeClasse'")
        }
        val interfaces = mutableListOf<String>()
        var implementaIndex = -1

        for (i in 0 until ctx.childCount) {
            if (ctx.getChild(i).text == "implementa") {
                implementaIndex = i
                break
            }
        }

        if (implementaIndex > -1) {
            var i = implementaIndex + 1
            while (i < ctx.childCount && ctx.getChild(i).text != "{") {
                val token = ctx.getChild(i).text
                if (token != "," && token != "implementa") {
                    interfaces.add(token)
                    global.obterInterface(token) ?: throw SemanticError("Interface '$token' não encontrada")

                    if (!verificarImplementacaoInterface(ctx, token)) {
                        throw SemanticError("A classe '$nomeClasse' não implementa todos os métodos da interface '$token'")
                    }
                }
                i++
            }
        }
        global.definirClasse(nomeClasse, ctx)
        return Valor.Nulo
    }

    override fun visitDeclaracaoVar(ctx: DeclaracaoVarContext): Valor {
        val nome = ctx.ID().text
        val tipo = ctx.tipo()?.text
        val valor = ctx.expressao()?.let { visit(it) } ?: Valor.Nulo

        if (tipo != null) {
            if (valor is Valor.Objeto) {
                val nomeClasse = valor.klass
                if (tipo != nomeClasse && valor.superClasse != tipo && !valor.interfaces.contains(tipo)) {
                    throw SemanticError("Tipo de variável '$tipo' não corresponde ao tipo do objeto '$nomeClasse'")
                }
            } else {
                if (tipo != valor.getTypeString()) throw SemanticError("Tipo da variavel nao corresponde ao tipo correto atribuido.")
            }
        }
        ambiente.definir(nome, valor)
        return Valor.Nulo
    }

    override fun visitDeclaracaoFuncao(ctx: DeclaracaoFuncaoContext): Valor {
        val nome = ctx.ID().text
        val tipoRetorno = ctx.tipo()?.text
        if (retornoFuncaoInvalido(tipoRetorno)) {
            throw SemanticError("Tipo de retorno inválido: $tipoRetorno")
        }

        val implementacao: (List<Valor>, Ambiente) -> Valor = { argumentos, ambienteGlobal ->
            val numParamsDeclarados = ctx.listaParams()?.param()?.size ?: 0
            if (argumentos.size > numParamsDeclarados) {
                throw SemanticError("Função '$nome' recebeu ${argumentos.size} parâmetros, mas espera $numParamsDeclarados")
            }

            val funcaoAmbiente = Ambiente(ambienteGlobal)

            ctx.listaParams()?.param()?.forEachIndexed { i, param ->
                if (i < argumentos.size) {
                    funcaoAmbiente.definir(param.ID().text, argumentos[i])
                }
            }

            val ambienteAnterior = ambiente
            ambiente = funcaoAmbiente

            try {
                visit(ctx.bloco())
                Valor.Nulo
            } catch (retorno: RetornoException) {
                retorno.valor
            } finally {
                ambiente = ambienteAnterior
            }
        }

        ambiente.definir(
            nome, Valor.Funcao(
                nome = nome,
                // Manter para debug/reflexão
                declaracao = ctx, tipoRetorno = tipoRetorno, implementacao = implementacao
            )
        )
        return Valor.Nulo
    }


    //TODO: refatorar validacao...
    private fun retornoFuncaoInvalido(tipoRetorno: String?): Boolean {
        if (tipoRetorno == null) return false
        return tipoRetorno !in listOf(
            "Inteiro", "Real", "Texto", "Logico", "Nulo"
        ) && (global.obterClasse(tipoRetorno) == null && global.obterInterface(tipoRetorno) == null)
    }

    //TODO: refatorar vist para declaracao de return
    override fun visitDeclaracaoRetornar(ctx: DeclaracaoRetornarContext): Valor {
        val valorRetorno = ctx.expressao()?.let { visit(it) } ?: Valor.Nulo

        if (funcaoAtual != null && funcaoAtual!!.tipoRetorno != null) {
            val tipoEsperado = funcaoAtual!!.tipoRetorno
            val tipoAtual = processarResultado(valorRetorno)
            if (tipoEsperado != tipoAtual) {
                if (valorRetorno is Valor.Objeto) {
                    //TODO: colocar verificao de superclasses e interfaces...
                    if (valorRetorno.superClasse == tipoEsperado || valorRetorno.interfaces.contains(tipoEsperado)) throw RetornoException(
                        valorRetorno
                    )
                }
                throw SemanticError("Erro de tipo: função '${funcaoAtual!!.nome}' deve retornar '$tipoEsperado', mas está retornando '$tipoAtual'")
            }
        }

        throw RetornoException(valorRetorno)
    }

    override fun visitDeclaracaoSe(ctx: DeclaracaoSeContext): Valor {
        val condicao = visit(ctx.expressao())
        if (condicao !is Valor.Logico) throw SemanticError("Condição do 'if' deve ser lógica")
        return if (condicao.valor) visit(ctx.declaracao(0)) else ctx.declaracao(1)?.let { visit(it) } ?: Valor.Nulo
    }

    override fun visitBloco(ctx: BlocoContext): Valor {
        val anterior = ambiente
        ambiente = Ambiente(anterior)
        ambiente.thisObjeto = anterior.thisObjeto
        try {
            ctx.declaracao().forEach { visit(it) }
        } finally {
            ambiente = anterior
        }
        return Valor.Nulo
    }

    override fun visitExpressao(ctx: ExpressaoContext): Valor = visit(ctx.getChild(0))

    //TODO: tipos primitivos nao tem validacao, posso associar um `var a: Inteiro = "ab"`
    override fun visitAtribuicao(ctx: AtribuicaoContext): Valor {
        if (ctx.logicaOu() != null) {
            return visit(ctx.logicaOu())
        }

        //TODO: rever uso da variavel valor...
        //val valor = visit(ctx.expressao())
        if (ctx.ID() != null) {
            val nome = ctx.ID().text
            val valor = visit(ctx.expressao())
            //TODO: validar tipo aqui
            ambiente.atualizarOuDefinir(nome, valor)
            return valor
        }
        if (ctx.acesso() != null) {
            val acesso = ctx.acesso()
            val objeto = visit(acesso.primario())

            if (objeto !is Valor.Objeto) {
                throw SemanticError("Não é possível atribuir a uma propriedade de um não-objeto")
            }
            val nomeCampo = acesso.ID().text
            val valorCampo = visit(ctx.expressao())

            objeto.campos[nomeCampo] = valorCampo
            return valorCampo
        }

        // Se for uma atribuição a um elemento de array
        if (ctx.acessoArray() != null) {
            val acessoArray = ctx.acessoArray()
            val container = visit(acessoArray.primario())
            val valor = visit(ctx.expressao())

            when (container) {
                is Valor.Lista -> {
                    val indice = visit(acessoArray.expressao(0))

                    if (indice !is Valor.Inteiro) {
                        throw SemanticError("Índice de lista deve ser um número inteiro")
                    }

                    if (indice.valor < 0) {
                        throw SemanticError("Índice negativo não permitido: ${indice.valor}")
                    }

                    // Expande a lista se necessário
                    while (indice.valor >= container.elementos.size) {
                        container.elementos.add(Valor.Nulo)
                    }

                    // Para acesso bidimensional
                    if (acessoArray.expressao().size > 1) {
                        val elemento = container.elementos[indice.valor]

                        // Se o elemento não for uma lista, cria uma
                        if (elemento !is Valor.Lista) {
                            container.elementos[indice.valor] = Valor.Lista(mutableListOf())
                        }

                        val lista = container.elementos[indice.valor] as Valor.Lista
                        val segundoIndice = visit(acessoArray.expressao(1))

                        if (segundoIndice !is Valor.Inteiro) {
                            throw SemanticError("Segundo índice deve ser um número inteiro")
                        }

                        if (segundoIndice.valor < 0) {
                            throw SemanticError("Segundo índice negativo não permitido: ${segundoIndice.valor}")
                        }

                        // Expande a sublista se necessário
                        while (segundoIndice.valor >= lista.elementos.size) {
                            lista.elementos.add(Valor.Nulo)
                        }
                        lista.elementos[segundoIndice.valor] = valor
                    } else {
                        container.elementos[indice.valor] = valor
                    }
                }

                //MAPA
                is Valor.Mapa -> {
                    val chave = visit(acessoArray.expressao(0))
                    container.elementos[chave] = valor
                }

                else -> {
                    throw SemanticError("Operação de atribuição com índice não suportada para ${container::class.simpleName}")
                }
            }
            return valor
        }

        throw SemanticError("Erro de sintaxe na atribuição")
    }


    override fun visitAcesso(ctx: AcessoContext): Valor {
        val objeto = visit(ctx.primario())

        if (objeto !is Valor.Objeto) {
            throw SemanticError("Tentativa de acessar propriedade de um não-objeto")
        }

        val propriedade = ctx.ID().text

        val valor = objeto.campos[propriedade]
        if (valor == null) {
            return Valor.Nulo
        }
        return valor
    }

    override fun visitLogicaOu(ctx: LogicaOuContext): Valor {
        var esquerda = visit(ctx.logicaE(0))
        for (i in 1 until ctx.logicaE().size) {
            if (esquerda is Valor.Logico && esquerda.valor) return Valor.Logico(true)
            val direita = visit(ctx.logicaE(i))
            if (esquerda !is Valor.Logico || direita !is Valor.Logico) throw SemanticError("Operador 'ou' requer valores lógicos")
            esquerda = Valor.Logico(esquerda.valor || direita.valor)
        }
        return esquerda
    }

    override fun visitLogicaE(ctx: LogicaEContext): Valor {
        var esquerda = visit(ctx.igualdade(0))
        for (i in 1 until ctx.igualdade().size) {
            if (esquerda is Valor.Logico && !esquerda.valor) return Valor.Logico(false)
            val direita = visit(ctx.igualdade(i))
            if (esquerda !is Valor.Logico || direita !is Valor.Logico) throw SemanticError("Operador 'e' requer valores lógicos")
            esquerda = Valor.Logico(esquerda.valor && direita.valor)
        }
        return esquerda
    }

    override fun visitIgualdade(ctx: IgualdadeContext): Valor {
        var esquerda = visit(ctx.comparacao(0))

        for (i in 1 until ctx.comparacao().size) {
            val operador = ctx.getChild(i * 2 - 1).text
            val direita = visit(ctx.comparacao(i))

            if (operador == "==") {
                val resultado = when {
                    esquerda == Valor.Nulo && direita == Valor.Nulo -> true
                    esquerda == Valor.Nulo || direita == Valor.Nulo -> false
                    else -> saoIguais(esquerda, direita)
                }
                esquerda = Valor.Logico(resultado)
            } else if (operador == "!=") {
                val resultado = when {
                    esquerda == Valor.Nulo && direita == Valor.Nulo -> false
                    esquerda == Valor.Nulo || direita == Valor.Nulo -> true
                    else -> !saoIguais(esquerda, direita)
                }
                esquerda = Valor.Logico(resultado)
            }
        }

        return esquerda
    }

    override fun visitComparacao(ctx: ComparacaoContext): Valor {
        var esquerda = visit(ctx.adicao(0))
        for (i in 1 until ctx.adicao().size) {
            val operador = ctx.getChild(i * 2 - 1).text
            val direita = visit(ctx.adicao(i))
            esquerda = when (operador) {
                "<" -> comparar("<", esquerda, direita)
                "<=" -> comparar("<=", esquerda, direita)
                ">" -> comparar(">", esquerda, direita)
                ">=" -> comparar(">=", esquerda, direita)
                else -> throw SemanticError("Operador desconhecido: $operador")
            }
        }
        return esquerda
    }


    override fun visitAdicao(ctx: AdicaoContext): Valor {
        var esquerda = visit(ctx.multiplicacao(0))
        for (i in 1 until ctx.multiplicacao().size) {
            val operador = ctx.getChild(i * 2 - 1).text
            val direita = visit(ctx.multiplicacao(i))
            esquerda = processarAdicao(operador, esquerda, direita)
        }
        return esquerda
    }

    override fun visitMultiplicacao(ctx: MultiplicacaoContext): Valor {
        var esquerda = visit(ctx.unario(0))
        for (i in 1 until ctx.unario().size) {
            val operador = ctx.getChild(i * 2 - 1).text
            val direita = visit(ctx.unario(i))
            esquerda = processarMultiplicacao(operador, esquerda, direita)
        }
        return esquerda
    }

    override fun visitUnario(ctx: UnarioContext): Valor {
        if (ctx.childCount == 2) {
            val operador = ctx.getChild(0).text
            val operando = visit(ctx.unario())
            return when (operador) {
                "!" -> if (operando is Valor.Logico) Valor.Logico(!operando.valor) else throw SemanticError("Operador '!' requer valor lógico")
                "-" -> when (operando) {
                    is Valor.Inteiro -> Valor.Inteiro(-operando.valor)
                    is Valor.Real -> Valor.Real(-operando.valor)
                    else -> throw SemanticError("Operador '-' requer valor numérico")
                }

                else -> throw SemanticError("Operador unário desconhecido: $operador")
            }
        }
        return visit(ctx.getChild(0))
    }

    private fun buscarPropriedadeNaHierarquia(objeto: Valor.Objeto, nomeCampo: String): Valor? {
        val valorCampo = objeto.campos[nomeCampo]
        if (valorCampo != null) {
            return valorCampo
        }

        if (objeto.superClasse != null) {
            val tempObjeto = criarObjetoTemporarioDaClasse(objeto.superClasse)
            return buscarPropriedadeNaHierarquia(tempObjeto, nomeCampo)
        }

        return null
    }


    private fun criarObjetoTemporarioDaClasse(nomeClasse: String): Valor.Objeto {
        val classe = global.obterClasse(nomeClasse) ?: throw SemanticError("Classe não encontrada: $nomeClasse")

        val superClasse = global.getSuperClasse(classe)
        val interfaces = global.getInterfaces(classe)

        val objeto = Valor.Objeto(nomeClasse, mutableMapOf(), superClasse, interfaces)

        classe.declaracaoVar().forEach { decl ->
            val nomeCampo = decl.ID().text
            val valor = decl.expressao()?.let {
                val oldAmbiente = ambiente
                ambiente = Ambiente(global).apply { thisObjeto = objeto }
                val result = visit(it)
                ambiente = oldAmbiente
                result
            } ?: Valor.Nulo
            objeto.campos[nomeCampo] = valor
        }

        return objeto
    }

    private fun buscarMetodoNaHierarquia(objeto: Valor.Objeto, nomeMetodo: String): DeclaracaoFuncaoContext? {
        val classe = global.obterClasse(objeto.klass) ?: return null
        val metodo = classe.declaracaoFuncao().find { it.ID().text == nomeMetodo }

        if (metodo != null) {
            return metodo
        }

        if (objeto.superClasse != null) {
            val classeBase = global.obterClasse(objeto.superClasse) ?: return null
            val metodoBase = classeBase.declaracaoFuncao().find { it.ID().text == nomeMetodo }

            if (metodoBase != null) {
                return metodoBase
            }

            val superClasseDaBase = global.getSuperClasse(classeBase)
            if (superClasseDaBase != null) {
                val objetoBase = Valor.Objeto(objeto.superClasse, mutableMapOf(), superClasseDaBase)
                return buscarMetodoNaHierarquia(objetoBase, nomeMetodo)
            }
        }
        return null
    }

    override fun visitChamada(ctx: ChamadaContext): Valor {
        if (ctx.acessoArray() != null) {
            return visit(ctx.acessoArray())
        }

        var resultado = visit(ctx.primario())
        var i = 1

        while (i < ctx.childCount) {
            if (resultado == Valor.Nulo) return Valor.Nulo
            if (ctx.getChild(i).text == ".") {
                val id = ctx.getChild(i + 1).text

                if (resultado !is Valor.Objeto) {
                    throw SemanticError("Não é possível acessar propriedades de um não-objeto: $resultado")
                }

                if (i + 2 < ctx.childCount && ctx.getChild(i + 2).text == "(") {
                    val args = mutableListOf<Valor>()

                    if (i + 3 < ctx.childCount && ctx.getChild(i + 3) is ArgumentosContext) {
                        val argsCtx = ctx.getChild(i + 3) as ArgumentosContext
                        args.addAll(argsCtx.expressao().map { visit(it) })
                        i += 5
                    } else {
                        i += 4
                    }

                    val metodo = buscarMetodoNaHierarquia(resultado, id)
                    if (metodo == null) {
                        throw SemanticError("Método não encontrado: $id em classe ${resultado.klass}")
                    }

                    resultado = executarMetodo(resultado, metodo, args)
                } else {
                    val campoValor = buscarPropriedadeNaHierarquia(resultado, id)
                    resultado = campoValor ?: Valor.Nulo
                    i += 2
                }
            } else {
                i++
            }
        }

        return resultado
    }


    override fun visitDeclaracaoEnquanto(ctx: DeclaracaoEnquantoContext): Valor {
        var iteracoes = 0
        val maxIteracoes = LOOP.VALOR_MAX_LOOP.valor

        while (iteracoes < maxIteracoes) {
            val condicao = visit(ctx.expressao())
            println("Condição do loop: $condicao")

            if (condicao !is Valor.Logico) {
                throw SemanticError("Condição do 'enquanto' deve ser um valor lógico")
            }

            if (!condicao.valor) {
                println("Condição falsa, saindo do loop")
                break
            }

            iteracoes++
            println("Iteração $iteracoes do loop")

            try {
                visit(ctx.declaracao())
            } catch (e: RetornoException) {
                throw e
            } catch (_: BreakException) {
                break
            } catch (_: ContinueException) {
                continue
            }
        }

        if (iteracoes >= maxIteracoes) {
            println("Aviso: Loop infinito detectado! Saindo do loop.")
            return Valor.Nulo
        }
        return Valor.Nulo
    }

    fun verificarImplementacaoInterface(classeContext: DeclaracaoClasseContext, nomeInterface: String): Boolean {
        val interfaceContext = global.obterInterface(nomeInterface) ?: return false
        for (assinatura in interfaceContext.assinaturaMetodo()) {
            val nomeMetodo = assinatura.ID().text
            val implementado = classeContext.declaracaoFuncao().any { it.ID().text == nomeMetodo }
            if (!implementado) {
                val superClasse = global.getSuperClasse(classeContext)
                if (superClasse != null) {
                    val classeBase = global.obterClasse(superClasse)
                    if (classeBase != null) {
                        val implementadoNaSuperClasse = classeBase.declaracaoFuncao().any { it.ID().text == nomeMetodo }
                        if (!implementadoNaSuperClasse) {
                            return false
                        }
                    } else {
                        return false
                    }
                } else {
                    return false
                }
            }
        }
        return true
    }

    override fun visitDeclaracaoPara(ctx: DeclaracaoParaContext): Valor {
        if (ctx.declaracaoVar() != null) {
            visit(ctx.declaracaoVar())
        } else if (ctx.expressao(0) != null) {
            visit(ctx.expressao(0))
        }

        while (true) {
            val condicao = visit(ctx.expressao(0))

            if (condicao !is Valor.Logico) {
                throw SemanticError("Condição do 'para' deve ser um valor lógico")
            }

            if (!condicao.valor) {
                break
            }

            try {
                visit(ctx.declaracao())
            } catch (e: RetornoException) {
                throw e
            } catch (_: BreakException) {
                break
            } catch (_: ContinueException) {
                // Isso existe para validar a logica dentro do loop antes de dar o continue...
                val condicao = visit(ctx.expressao(0))
                if (condicao !is Valor.Logico) {
                    throw SemanticError("Condição do 'para' deve ser um valor lógico")
                }
                if (!condicao.valor) {
                    break
                } else {
                    visit(ctx.expressao(1))
                    continue
                }
            }
            visit(ctx.expressao(1))
        }

        return Valor.Nulo
    }

    override fun visitDeclaracaoFacaEnquanto(ctx: DeclaracaoFacaEnquantoContext): Valor {
        var limit = 0
        do {
            try {
                visit(ctx.declaracao())
            } catch (e: RetornoException) {
                throw e
            } catch (_: BreakException) {
                break
            } catch (_: ContinueException) {
                val condicao = visit(ctx.expressao())
                if (condicao !is Valor.Logico) {
                    throw SemanticError("Condição do 'enquanto' deve ser um valor lógico")
                }
                if (!condicao.valor) {
                    break
                }
                visit(ctx.declaracao())
                continue
            }

            val condicao = visit(ctx.expressao())

            if (condicao !is Valor.Logico) {
                throw SemanticError("Condição do 'enquanto' deve ser um valor lógico")
            }

            if (!condicao.valor) {
                break
            }
            limit++
            if (limit >= 100) {
                println("Aviso: Loop infinito detectado! Saindo do loop.")
                break
            }
        } while (true)

        return Valor.Nulo
    }

    override fun visitDeclaracaoQuebra(ctx: DeclaracaoQuebraContext): Valor {
        throw BreakException()
    }

    override fun visitListaLiteral(ctx: ListaLiteralContext): Valor {
        return Valor.Lista()
    }


    override fun visitMapaLiteral(ctx: MapaLiteralContext): Valor {
        return Valor.Mapa()
    }

    override fun visitAcessoArray(ctx: AcessoArrayContext): Valor {
        val container = visit(ctx.primario())

        when (container) {
            is Valor.Lista -> {
                val indice = visit(ctx.expressao(0))

                if (indice !is Valor.Inteiro) {
                    throw SemanticError("Índice de lista deve ser um número inteiro")
                }

                if (indice.valor < 0 || indice.valor >= container.elementos.size) {
                    throw SemanticError("Índice fora dos limites da lista: ${indice.valor}")
                }

                // Para acesso bidimensional
                if (ctx.expressao().size > 1) {
                    val elemento = container.elementos[indice.valor]
                    val segundoIndice = visit(ctx.expressao(1))

                    when (elemento) {
                        is Valor.Lista -> {
                            if (segundoIndice !is Valor.Inteiro) {
                                throw SemanticError("Segundo índice deve ser um número inteiro para acessar uma lista")
                            }

                            if (segundoIndice.valor < 0 || segundoIndice.valor >= elemento.elementos.size) {
                                throw SemanticError("Segundo índice fora dos limites da lista: ${segundoIndice.valor}")
                            }

                            return elemento.elementos[segundoIndice.valor]
                        }

                        is Valor.Mapa -> {
                            return elemento.elementos[segundoIndice] ?: Valor.Nulo
                        }

                        is Valor.Objeto -> {
                            if (segundoIndice !is Valor.Texto) {
                                throw SemanticError("Chave para acessar campo de objeto deve ser texto")
                            }
                            return elemento.campos[segundoIndice.valor] ?: Valor.Nulo
                        }

                        else -> {
                            throw SemanticError("Elemento no índice ${indice.valor} não suporta acesso indexado")
                        }
                    }
                }

                return container.elementos[indice.valor]
            }

            is Valor.Mapa -> {
                val chave = visit(ctx.expressao(0))

                // Para acesso bidimensional em mapas
                if (ctx.expressao().size > 1) {
                    val primeiroElemento = container.elementos[chave] ?: Valor.Nulo
                    val segundoIndice = visit(ctx.expressao(1))

                    when (primeiroElemento) {
                        is Valor.Lista -> {
                            when {
                                segundoIndice !is Valor.Inteiro -> {
                                    throw SemanticError("Segundo índice deve ser um número inteiro para acessar uma lista")
                                }

                                segundoIndice.valor < 0 || segundoIndice.valor >= primeiroElemento.elementos.size -> {
                                    throw SemanticError("Segundo índice fora dos limites da lista: ${segundoIndice.valor}")
                                }

                                else -> return primeiroElemento.elementos[segundoIndice.valor]
                            }
                        }

                        is Valor.Mapa -> {
                            return primeiroElemento.elementos[segundoIndice] ?: Valor.Nulo
                        }

                        is Valor.Objeto -> {
                            if (segundoIndice !is Valor.Texto) {
                                throw SemanticError("Chave para acessar campo de objeto deve ser texto")
                            }
                            return primeiroElemento.campos[segundoIndice.valor] ?: Valor.Nulo
                        }

                        else -> {
                            throw SemanticError("Elemento com chave $chave não suporta acesso indexado")
                        }
                    }
                }

                return container.elementos[chave] ?: Valor.Nulo
            }

            else -> {
                throw SemanticError("Operação de acesso com índice não suportada para ${container::class.simpleName}")
            }
        }
    }

    override fun visitDeclaracaoContinue(ctx: DeclaracaoContinueContext): Valor {
        throw ContinueException()
    }

    override fun visitChamadaFuncao(ctx: ChamadaFuncaoContext): Valor {
        val argumentos = ctx.argumentos()?.expressao()?.map { visit(it) } ?: emptyList()

        return if (ctx.primario() != null) {
            val objeto = visit(ctx.primario())

            if (objeto !is Valor.Objeto) {
                throw SemanticError("Chamada de método em não-objeto")
            }

            val metodoNome = ctx.ID().text

            val classe =
                global.obterClasse(objeto.klass) ?: throw SemanticError("Classe não encontrada: ${objeto.klass}")
            val metodo = classe.declaracaoFuncao().find { it.ID().text == metodoNome }
                ?: throw SemanticError("Método não encontrado: $metodoNome")

            executarMetodo(objeto, metodo, argumentos)
        } else {
            val funcaoNome = ctx.ID().text
            chamadaFuncao(funcaoNome, argumentos)
        }
    }

    private fun chamadaFuncao(nome: String, argumentos: List<Valor>): Valor {
        ambiente.thisObjeto?.let { obj ->
            buscarMetodoNaHierarquia(obj, nome)?.let { ctx ->
                return executarMetodo(obj, ctx, argumentos)
            }
        }

        val funcao = try {
            val value = ambiente.obter(nome)
            value as? Valor.Funcao ?: throw SemanticError("'$nome' não é uma função")
        } catch (e: Exception) {
            throw SemanticError("Função não encontrada: $nome + ${e.message}")
        }

        if (funcao.metodoCallback == null) {
            val numParamsDeclarados = funcao.declaracao?.listaParams()?.param()?.size ?: 0
            if (argumentos.size > numParamsDeclarados) {
                throw SemanticError("Função '$nome' recebeu ${argumentos.size} parâmetros, mas espera $numParamsDeclarados")
            }
        }

        val funcaoAnterior = funcaoAtual
        funcaoAtual = funcao

        //TODO: verificar tipo de retorno antes de executar a funcao..???
        try {
            if (funcao.metodoCallback != null) {
                val resultado = funcao.metodoCallback.invoke(argumentos)
                if (funcao.tipoRetorno != null) {
                    val tipoEsperado = funcao.tipoRetorno
                    val tipoAtual = processarResultado(resultado)
                    if (tipoEsperado != tipoAtual) {
                        if (resultado is Valor.Objeto) {
                            if (resultado.superClasse == tipoEsperado || resultado.interfaces.contains(tipoEsperado)) return resultado
                        }
                        throw SemanticError("Erro de tipo: função '$nome' deve retornar '$tipoEsperado', mas está retornando '$tipoAtual'")
                    }
                }
                return resultado
            } else {
                val decl = funcao.declaracao ?: throw SemanticError("Declaração de função não disponível: $nome")
                val funcaoAmbiente = Ambiente(global)
                decl.listaParams()?.param()?.forEachIndexed { i, param ->
                    if (i < argumentos.size) funcaoAmbiente.definir(param.ID().text, argumentos[i])
                }
                val oldAmbiente = ambiente
                ambiente = funcaoAmbiente
                try {
                    visit(decl.bloco())
                    return Valor.Nulo
                } catch (retorno: RetornoException) {
                    return retorno.valor
                } finally {
                    ambiente = oldAmbiente
                }
            }
        } finally {
            funcaoAtual = funcaoAnterior
        }
    }


    private fun executarMetodo(objeto: Valor.Objeto, metodo: DeclaracaoFuncaoContext, argumentos: List<Valor>): Valor {
        val metodoAmbiente = Ambiente(global)
        metodoAmbiente.thisObjeto = objeto

        val funcao = Valor.Funcao(
            metodo.ID().text, metodo, metodo.tipo()?.text
        )

        val funcaoAnterior = funcaoAtual
        funcaoAtual = funcao

        val params = metodo.listaParams()?.param() ?: listOf()

        for (i in params.indices) {
            val paramNome = params[i].ID().text

            if (i < argumentos.size) {
                val valorArg = argumentos[i]
                metodoAmbiente.definir(paramNome, valorArg)
            } else {
                metodoAmbiente.definir(paramNome, Valor.Nulo)
            }
        }

        val oldAmbiente = ambiente
        ambiente = metodoAmbiente

        try {
            visit(metodo.bloco())
            return Valor.Nulo
        } catch (retorno: RetornoException) {
            return retorno.valor
        } finally {
            ambiente = oldAmbiente
            funcaoAtual = funcaoAnterior
        }
    }

    override fun visitPrimario(ctx: PrimarioContext): Valor {
        return when {
            ctx.listaLiteral() != null -> visit(ctx.listaLiteral())
            ctx.mapaLiteral() != null -> visit(ctx.mapaLiteral())
            ctx.NUMERO() != null -> ctx.NUMERO().text.let {
                if (it.contains(".")) Valor.Real(it.toDouble()) else Valor.Inteiro(
                    it.toInt()
                )
            }

            ctx.TEXTO_LITERAL() != null -> Valor.Texto(ctx.TEXTO_LITERAL().text.removeSurrounding("\""))
            ctx.ID() != null && !ctx.text.startsWith("nova") -> {
                val nome = ctx.ID().text
                if (ctx.childCount > 1 && ctx.getChild(1).text == "(") {
                    val argumentos = if (ctx.childCount > 2 && ctx.getChild(2) is ArgumentosContext) {
                        val argsCtx = ctx.getChild(2) as ArgumentosContext
                        argsCtx.expressao().map { visit(it) }
                    } else {
                        emptyList()
                    }

                    chamadaFuncao(nome, argumentos)
                } else {
                    try {
                        ambiente.obter(nome)
                    } catch (e: Exception) {
                        throw e
                    }
                }
            }

            ctx.expressao() != null -> visit(ctx.expressao())
            ctx.text == "verdadeiro" -> Valor.Logico(true)
            ctx.text == "falso" -> Valor.Logico(false)
            ctx.text == "this" -> ambiente.thisObjeto ?: throw SemanticError("'this' fora de contexto de objeto")
            ctx.text.startsWith("nova") -> {
                val match = Regex("nova([A-Za-z0-9_]+)\\(.*\\)").find(ctx.text)
                if (match != null) {
                    val nomeClasse = match.groupValues[1]

                    val classe =
                        global.obterClasse(nomeClasse) ?: throw SemanticError("Classe não encontrada: $nomeClasse")
                    return criarObjetoClasse(nomeClasse, ctx, classe)
                } else {
                    throw SemanticError("Sintaxe inválida para criação de objeto")
                }
            }

            else -> {
                Valor.Nulo
            }
        }
    }

    //TODO: testar mais essa funcao de extracao argumentos do constructor..., ja testei com tipos simples e com objetos
    private fun extrairArgumentosDoConstructor(ctx: PrimarioContext): List<Valor> {
        val args = mutableListOf<Valor>()
        if (!ctx.argumentos().isEmpty) {
            ctx.argumentos().expressao().forEach { expr ->
                args.add(visit(expr))
            }
        }
        return args
    }

    //TODO: rever uso de recursao...
    private fun inicializarCamposDaClasseBase(objeto: Valor.Objeto, nomeClasseBase: String) {
        val classeBase = global.obterClasse(nomeClasseBase) ?: return

        val superClasseDaBase = global.getSuperClasse(classeBase)
        if (superClasseDaBase != null) {
            inicializarCamposDaClasseBase(objeto, superClasseDaBase)
        }

        classeBase.declaracaoVar().forEach { decl ->
            val nomeCampo = decl.ID().text
            if (!objeto.campos.containsKey(nomeCampo)) {
                val oldAmbiente = ambiente
                ambiente = Ambiente(global).apply { thisObjeto = objeto }
                val valor = decl.expressao()?.let { visit(it) } ?: Valor.Nulo
                objeto.campos[nomeCampo] = valor
                ambiente = oldAmbiente
            }
        }
    }

    private fun criarObjetoClasse(nomeClasse: String, ctx: PrimarioContext, classe: DeclaracaoClasseContext): Valor {
        val superClasse = global.getSuperClasse(classe)
        val interfaces = global.getInterfaces(classe)

        val objeto = Valor.Objeto(nomeClasse, mutableMapOf(), superClasse, interfaces)

        if (superClasse != null) {
            inicializarCamposDaClasseBase(objeto, superClasse)
        }

        classe.declaracaoVar().forEach { decl ->
            val nomeCampo = decl.ID().text
            val oldAmbiente = ambiente
            ambiente = Ambiente(global).apply { thisObjeto = objeto }
            val valor = decl.expressao()?.let { visit(it) } ?: Valor.Nulo
            objeto.campos[nomeCampo] = valor
            ambiente = oldAmbiente
        }

        val inicializarMetodo = classe.declaracaoFuncao().find { it.ID().text == "inicializar" }
        if (inicializarMetodo != null) {
            val argumentos = extrairArgumentosDoConstructor(ctx)
            executarMetodo(objeto, inicializarMetodo, argumentos)
        }
        return objeto
    }
}
