package models.errors

import models.Valor

class BreakException : RuntimeException()
class ContinueException : RuntimeException()
class RetornoException(val valor: Valor) : RuntimeException()
class MainExecutionException(msg: String) : RuntimeException(msg)
class ArquivoException(msg: String) : RuntimeException(msg)
class SemanticError(msg: String?) : RuntimeException(msg)
class MemoryError(msg: String?) : RuntimeException(msg)
