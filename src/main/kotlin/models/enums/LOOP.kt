package models.enums

enum class LOOP {
    VALOR_MAX_LOOP(10000);
    val valor: Int
    constructor(valor: Int) {
        this.valor = valor
    }
}


