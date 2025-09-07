package models.enums

import models.Valor

enum class BasicTypes(val tipo: String) {
    INTEIRO("Inteiro"),
    REAL("Real"),
    TEXTO("Texto"),
    LOGICO("Logico"),
    Nulo("Nulo");

    companion object {
        fun buscarTipo(tipo: String): BasicTypes? {
            return entries.find { it.tipo == tipo }
        }

        fun buscarTipoOuJogarException(tipo: String): BasicTypes {
            return buscarTipo(tipo) ?: throw IllegalArgumentException("Tipo não encontrado: $tipo")
        }

        fun buscarValorOuJogarException(tipo: Valor): BasicTypes {
            return when (tipo) {
                is Valor.Texto -> TEXTO
                is Valor.Inteiro -> INTEIRO
                is Valor.Real -> REAL
                is Valor.Logico -> LOGICO
                is Valor.Nulo -> Nulo
                else -> {
                    throw IllegalArgumentException("Tipo primitivo não encontrado: $tipo")
                }
            }
        }

    }
}
