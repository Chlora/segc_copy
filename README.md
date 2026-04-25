SegC-18
==============================

## Requisitos

* JDK & java 21
* Maven (para compilação; opcional)


## Como executar

**Observação**: O servidor deve ser iniciado antes do cliente.

### Usando o JAR (Recomendado)
Se o ficheiro JAR já estiver disponível na pasta `target`, não é necessário usar o Maven e com. 
*(Nota: É necessário utilizar o nome completo da classe, incluindo o package).*

**1. Executar o Servidor:**
java -jar server/target/sperta-server.jar [port] [password-cifra] [keystore] [password-keystore]
java -jar server/target/sperta-server.jar 22345 password ./security/server.keystore.jks password

**2. Executar o Cliente:**
java -jar client/target/sperta-client.jar [IP:port] [truststore] [keystore] [password-keystore] [username] [password]
java -jar client/target/sperta-client.jar localhost:22345 ./security/client.truststore.jks password ./security/alan.keystore.jks password alan password


## Como compilar

Caso seja necessário gerar os ficheiros JAR novamente, execute na raiz do projeto:

``mvn install``

Para limpar os ficheiros, executar na raiz do projeto:

``mvn clean``


## Funcionalidades

* CREATE <hm> # Criar casa <hm> - utilizador é Owner
* ADD <user1> <hm> <s> # Adicionar utilizador <user1> à casa <hm>, seção <s>
* RD <hm> <s> # Registar um Dispositivo na casa <hm>, na seção <s>
* EC <hm> <d> <int> # Enviar valor <int> de estado/temporização, do dispositivo <d> da casa <hm>, para o servidor
* RT <hm># Receber a informação sobre o último comando (estados/temporizações) enviado a cada dispositivo da casa <hm>, desde  que o utilizador tenha permissões
* RH <hm> <d># Receber o Histórico (ficheiro de log .csv) de comandos enviados ao dispositivo <d> da casa <hm>, desde que o utilizador tenha permissões


Ficheiros do servidor são guardados na pasta ``/ficheiros/``
Ficheiros recebidos pelo cliente são guardados na pasta ``/ficheirosRecebidos/``


## Limitações do projeto

O projeto foi desenvolvido em Windows; os ficheiros criados pelo servidor podem apresentar problemas de permissões quando o programa é executado em sistemas Linux.


## Autores

* Jin Zhengrong FC61808
* Rodrigo Santos FC61825
* Gonçalo Wang FC61828