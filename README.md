## Sobre o Projeto

<mark>_P++_ é uma linguagem de programação destinada a falantes de português</mark>, facilitando o aprendizado de programação através
de uma sintaxe semelhante ao Portugol, mas com recursos modernos de linguagens orientadas a objetos.

## Características

- <mark>**Sintaxe amigável**</mark> em português baseada em Portugol
- <mark>**Programação Orientada a Objetos**</mark> com classes, herança e interfaces
- <mark>**Tipagem dinâmica**</mark> para facilitar o aprendizado
- <mark>**Estruturas de controle**</mark> (condicionais, loops)
- <mark>**Funções e métodos**</mark> com parâmetros e valores de retorno
- <mark>**Funções nativas de E/S**</mark> para operações de entrada e saída
- <mark>**Coleções**</mark> como listas e mapas
- <mark>**Suporte a threads**</mark> para execução assíncrona
- <mark>**Suporte a multiplos modulos**</mark> import de outros arquivos
-  <mark>**Suporte a closures**</mark>

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



