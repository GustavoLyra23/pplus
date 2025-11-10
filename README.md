## Sobre o Projeto

_P++_ é uma linguagem de programação destinada a falantes de português, facilitando o aprendizado de programação através
de uma sintaxe semelhante ao Portugol, mas com recursos modernos de linguagens orientadas a objetos.

## Características

- **Sintaxe amigável** em português baseada em Portugol
- **Programação Orientada a Objetos** com classes, herança e interfaces
- **Tipagem dinâmica** para facilitar o aprendizado
- **Estruturas de controle** (condicionais, loops)
- **Funções e métodos** com parâmetros e valores de retorno
- **Funções nativas de E/S** para operações de entrada e saída
- **Coleções** como listas e mapas
- **Suporte a threads** para execução assíncrona
- **Suporte a multiplos modulos** import de outros arquivos

## Requisitos do Sistema

- Java JDK 21 ou superior
- Gradle 8.0 ou superior

## Documentação

- **Documentação da Engine do Interpretador**: [P++ Docs](https://deepwiki.com/GustavoLyra23/PPlus)

## Instalação

### Clonar o repositório

```bash
git clone <url-do-repositorio>
cd pplus
``` 

## Compilar o projeto

#### Compilar projeto principal

```bash
./gradlew build
```

#### Criar JAR executável

```bash
./gradlew shadowJar
```

#### Gerar executável Windows (opcional)

```bash
./gradlew launch4j 
```

#### Executar um programa P++

Usando JAR

```bash
java -jar build/libs/portugolpp.jar programa.ppp
```

#### Usando executável Windows

```bash
./portugolpp.exe programa.ppp
```

<div>
<img src="https://img.shields.io/badge/status-desenvolvimento-green.svg" alt="Status" />
</div>



