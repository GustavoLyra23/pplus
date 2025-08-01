package models

import org.gustavolyra.portugolpp.PortugolPPParser

sealed class Valor {

    data class Inteiro(val valor: Int) : Valor()

    data class Real(val valor: Double) : Valor()

    data class Texto(val valor: String) : Valor()

    data class Logico(val valor: Boolean) : Valor()

    data class Lista(val elementos: MutableList<Valor> = mutableListOf()) : Valor()

    data class Mapa(val elementos: MutableMap<Valor, Valor> = mutableMapOf()) : Valor()

    data class Objeto(
        val klass: String,
        val campos: MutableMap<String, Valor>,
        val superClasse: String? = null,
        val interfaces: List<String> = listOf()
    ) : Valor()


    data class Param(val nome: String, val tipo: String)

    data class Interface(val nome: String, val assinaturas: Map<String, AssinaturaMetodo>) : Valor()

    class AssinaturaMetodo(val nome: String, val parametros: List<Param>, val tipoRetorno: String? = null)

    data class Funcao(
        val nome: String,
        //TODO: repensar estrategia de uso desta variavel...
        val declaracao: PortugolPPParser.DeclaracaoFuncaoContext? = null,
        val tipoRetorno: String? = null,
        //novo campo..
        val implementacao: ((List<Valor>, Ambiente) -> Valor)? = null,
        // campo para funcoes nativas
        val metodoCallback: ((List<Valor>) -> Valor)? = null
    ) : Valor()

    object Nulo : Valor()

    override fun toString(): String = when (this) {
        is Inteiro -> valor.toString()
        is Real -> valor.toString()
        is Texto -> valor
        is Logico -> if (valor) "verdadeiro" else "falso"
        is Objeto -> "[Objeto $klass]"
        is Funcao -> "[função $nome]"
        Nulo -> "nulo"
        is Interface -> "[Interface]"
        else -> super.toString()
    }
}