import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import models.Ambiente
import models.Valor
import models.errors.ArquivoException
import processors.FileIOProcessor.escreverArquivo
import processors.FileIOProcessor.lerArquivo
import java.util.*

fun setFuncoesDefault(global: Ambiente) {
    registrarFuncoesIO(global)
    registarFuncoesThread(global)
    registrarFuncoesExecptions(global)
    registrarFuncoesColecoes(global)
}

fun registrarFuncoesIO(global: Ambiente) {
    global.definir("ler", Valor.Funcao("ler", null) { args ->
        Scanner(System.`in`).nextLine().let { Valor.Texto(it) }
    })
    global.definir("lerArquivo", Valor.Funcao("lerArquivo", null) { args ->
        if (args.isEmpty()) throw RuntimeException("Função lerArquivo requer um argumento (caminho do arquivo)")
        if (args.size > 1) throw RuntimeException("Função lerArquivo aceita apenas um argumento")

        val argVal = args[0]
        if (argVal !is Valor.Texto) {
            throw RuntimeException("Argumento deve ser um texto (caminho do arquivo)")
        }

        try {
            Valor.Texto(lerArquivo(argVal.valor))
        } catch (e: Exception) {
            throw ArquivoException("Erro ao ler arquivo '${argVal.valor}': ${e.message}")
        }
    })
    global.definir("escreverArquivo", Valor.Funcao("escreverArquivo", null) { args ->
        require(args.size in 2..3) { "Função escreverArquivo requer 2 ou 3 argumentos" }
        val (path, data) = args.take(2)
        val append = args.getOrNull(2)

        require(path is Valor.Texto && data is Valor.Texto) {
            "Os dois primeiros argumentos devem ser do tipo Texto"
        }
        when (append) {
            null -> escreverArquivo(path.valor, data.valor)
            is Valor.Logico -> escreverArquivo(
                path.valor, data.valor, append.valor
            )

            else -> throw RuntimeException("O terceiro argumento deve ser do tipo Logico")
        }
        Valor.Nulo
    })
    global.definir("escrever", Valor.Funcao("escrever", null) { args ->
        val valores = args.map { extrairValorParaImpressao(it) }
        println(valores.joinToString(" "))
        Valor.Nulo
    })
    global.definir("imprimir", Valor.Funcao("imprimir", null) { args ->
        val valores = args.map { extrairValorParaImpressao(it) }
        println(valores.joinToString(" "))
        Valor.Nulo
    })
}

fun registarFuncoesThread(global: Ambiente) {
    global.definir("executar", Valor.Funcao("executar", null) { args ->
        if (args.isEmpty() || args[0] !is Valor.Funcao) {
            throw RuntimeException("Argumento invalido para a funcao.")
        }
        val funcaoParaExecutar = args[0] as Valor.Funcao
        val argumentosReais = args.drop(1)
        runBlocking {
            launch {
                try {
                    when {
                        funcaoParaExecutar.implementacao != null -> funcaoParaExecutar.implementacao.invoke(
                            argumentosReais,
                            global
                        )

                        funcaoParaExecutar.metodoCallback != null -> funcaoParaExecutar.metodoCallback.invoke(
                            argumentosReais
                        )
                        else -> {
                            throw RuntimeException("Função não tem implementação executável")
                        }
                    }
                } catch (e: Exception) {
                    println("Erro na execucao da thread: ${e.message}")
                }
            }.join()
        }
        Valor.Nulo
    })
    global.definir("dormir", Valor.Funcao("aguardar", null, null, null) { args ->
        if (args.isEmpty()) {
            throw RuntimeException("Função aguardar requer um argumento (milissegundos)")
        }

        val tempo = args[0]

        if (tempo !is Valor.Inteiro) {
            throw RuntimeException("Argumento deve ser um número inteiro (milissegundos)")
        }

        runBlocking {
            delay(tempo.valor.toLong())
        }
        Valor.Nulo
    })
}

fun registrarFuncoesExecptions(global: Ambiente) {
    global.definir("jogarError", Valor.Funcao("jogarError", null) { args ->
        if (args.isEmpty()) {
            throw RuntimeException("Função jogarError requer um argumento (mensagem de erro)")
        }
        val mensagem = args[0]
        if (mensagem !is Valor.Texto) {
            throw RuntimeException("Argumento deve ser um texto (mensagem de erro)")
        }
        throw RuntimeException(mensagem.valor)
    })
}

fun registrarFuncoesColecoes(global: Ambiente) {
    global.definir("tamanho", Valor.Funcao("tamanho", null) { args ->
        if (args.isEmpty()) {
            throw RuntimeException("Função tamanho requer um argumento (lista, mapa ou texto)")
        }

        when (val arg = args[0]) {
            is Valor.Lista -> Valor.Inteiro(arg.elementos.size)
            is Valor.Mapa -> Valor.Inteiro(arg.elementos.size)
            is Valor.Texto -> Valor.Inteiro(arg.valor.length)
            else -> throw RuntimeException("Função tamanho só funciona com listas, mapas ou textos")
        }
    })
    global.definir("adicionar", Valor.Funcao("adicionar", null) { args ->
        if (args.size < 2) {
            throw RuntimeException("Função adicionar requer pelo menos 2 argumentos: lista e elemento")
        }

        val lista = args[0]
        if (lista !is Valor.Lista) {
            throw RuntimeException("Primeiro argumento deve ser uma lista")
        }

        for (i in 1 until args.size) {
            lista.elementos.add(args[i])
        }
        lista
    })
    global.definir("remover", Valor.Funcao("remover", null) { args ->
        if (args.size != 2) {
            throw RuntimeException("Função remover requer 2 argumentos: lista e índice")
        }

        val lista = args[0]
        val indice = args[1]


        if (lista !is Valor.Lista) {
            throw RuntimeException("Primeiro argumento deve ser uma lista")
        }

        if (indice !is Valor.Inteiro) {
            throw RuntimeException("Segundo argumento deve ser um número inteiro")
        }

        if (indice.valor < 0 || indice.valor >= lista.elementos.size) {
            throw RuntimeException("Índice fora dos limites da lista: ${indice.valor}")
        }

        lista.elementos.removeAt(indice.valor)
    })
    global.definir("chaves", Valor.Funcao("chaves", null) { args ->
        if (args.isEmpty()) {
            throw RuntimeException("Função chaves requer um argumento (mapa)")
        }

        val mapa = args[0]
        if (mapa !is Valor.Mapa) {
            throw RuntimeException("Argumento deve ser um mapa")
        }

        Valor.Lista(mapa.elementos.keys.toMutableList())
    })
    global.definir("valores", Valor.Funcao("valores", null) { args ->
        if (args.isEmpty()) {
            throw RuntimeException("Função valores requer um argumento (mapa)")
        }

        val mapa = args[0]
        if (mapa !is Valor.Mapa) {
            throw RuntimeException("Argumento deve ser um mapa")
        }

        Valor.Lista(mapa.elementos.values.toMutableList())
    })
    global.definir("contemChave", Valor.Funcao("contemChave", null) { args ->
        if (args.size != 2) {
            throw RuntimeException("Função contemChave requer 2 argumentos: mapa e chave")
        }

        val mapa = args[0]
        val chave = args[1]

        if (mapa !is Valor.Mapa) {
            throw RuntimeException("Primeiro argumento deve ser um mapa")
        }

        Valor.Logico(mapa.elementos.containsKey(chave))
    })
}



