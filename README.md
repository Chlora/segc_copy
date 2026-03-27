# SegC-18

## 📋 Requisitos

* Java: Versão 21 ou superior.
* Maven(Opcional):  Apache Maven para compilação.

==============================
▶️ Como executar
==============================

**Observação**: O servidor deve ser iniciado antes do cliente.

### Usando o JAR (Recomendado)
Se o ficheiro JAR já estiver disponível na pasta `target`, não é necessário usar o Maven. 
*(Nota: É necessário utilizar o nome completo da classe, incluindo o package).*

**1. Executar o Servidor:**
``bash
java -cp target/sperta-project-1.0-SNAPSHOT.jar server.SpertaServer [port]

**2. Executar o Cliente:**
``bash
java -cp target/sperta-project-1.0-SNAPSHOT.jar client.SpertaClient [IP:port] [username] [password]


==============================
🛠️ Como Compilar o Projeto com Maven
==============================

Caso seja necessário gerar o ficheiro JAR novamente, execute na raiz do projeto:

``bash
# Limpa ficheiros de compilações anteriores (opcional, mas recomendado)
mvn clean
# Compila o código e empacota-o num ficheiro JAR
mvn package


==============================
📌 Funcionalidades
==============================

* CREATE <hm> # Criar casa <hm> - utilizador é Owner
* ADD <user1> <hm> <s> # Adicionar utilizador <user1> à casa <hm>, seção <s>
* RD <hm> <s> # Registar um Dispositivo na casa <hm>, na seção <s>
* EC <hm> <d> <int> # Enviar valor <int> de estado/temporização, do dispositivo <d> da casa <hm>, para o servidor
* RT <hm># Receber a informação sobre o último comando (estados/temporizações) enviado a cada dispositivo da casa <hm>, desde  que o utilizador tenha permissões
* RH <hm> <d># Receber o Histórico (ficheiro de log .csv) de comandos enviados ao dispositivo <d> da casa <hm>, desde que o utilizador tenha permissões


==============================
⚠️ Limitações do projeto
==============================

O projeto foi desenvolvido em Windows; os ficheiros criados pelo servidor podem apresentar problemas de permissões quando o programa é executado em sistemas Linux.


==============================
👥 Autores
==============================

* Jin Zhengrong FC61808
* Rodrigo Santos FC61825
* Gonçalo Wang FC61828