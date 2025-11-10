import constants.EXTENSAO
import org.antlr.v4.runtime.*
import org.gustavolyra.portugolpp.Interpretador
import org.gustavolyra.portugolpp.PortugolPPLexer
import org.gustavolyra.portugolpp.PortugolPPParser
import java.io.File
import kotlin.system.exitProcess

val interpretador = Interpretador()
fun main() {
    println("Iniciando Portugol++")
    when {
//       args.getOrNull(0) == "run" && args.size > 1 -> executarArquivo(args[1])
        //REPL direto...
        else -> modoInterativo()
    }
}

fun modoInterativo() {
    println("Digite 'exit' para sair")
    println("Digite 'run <caminho>' para executar um arquivo")
    while (true) {
        print("> ")
        val input = readlnOrNull()?.trim() ?: continue
        when {
            input == "exit" -> exitProcess(0)
            input.startsWith("run ") -> {
                val caminho = input.substring(4).trim()
                executarArquivo(caminho)
            }
            else -> executarPortugolPP(input)
        }
    }
}

fun executarArquivo(caminho: String) {
    try {
        val arquivo = File(caminho)
        if (!validarArquivo(arquivo)) return
        val fileData = arquivo.readText()
        executarPortugolPP(fileData)
    } catch (e: Exception) {
        println("Erro ao ler/executar o arquivo: ${e.message}")
    }
}


fun executarPortugolPP(codigo: String) {
    try {
        val input = CharStreams.fromString(codigo)
        val lexer = PortugolPPLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = PortugolPPParser(tokens)

        parser.removeErrorListeners()
        parser.addErrorListener(object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String?,
                e: RecognitionException?
            ) {
                println("Erro de sintaxe na linha $line:$charPositionInLine - $msg")
            }
        })

        val tree = parser.programa()
        if (tree == null) {
            println("ERRO: Análise sintática falhou árvore sintática nula...!")
            return
        }
        interpretador.interpretar(tree)
    } catch (e: Exception) {
        println("Erro ao executar o programa: ${e.message}")
    }
}

fun validarArquivo(arquivo: File): Boolean {
    if (!arquivo.exists()) {
        println("Erro: Arquivo não encontrado!")
        return false
    }
    if (!arquivo.name.endsWith(EXTENSAO)) {
        println("Formato do arquivo inválido! Use arquivos .pplus")
        return false
    }
    return true
}

