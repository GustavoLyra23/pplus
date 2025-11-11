package processors

import models.Ambiente


fun retornoFuncaoInvalido(tipoRetorno: String?, global: Ambiente): Boolean {
    if (tipoRetorno == null) return false
    return tipoRetorno !in listOf(
        "Inteiro", "Real", "Texto", "Logico", "Nulo", "Lista", "Mapa"
    ) && (global.obterClasse(tipoRetorno) == null && global.obterInterface(tipoRetorno) == null)
}

