(copia do versao markdown caso esse nao seja considerado valido)

SegC-18
==============================

## Requisitos

* JDK & java 21
* Maven (para compilação; opcional)


## Como executar

**Observações**: 
- O servidor deve ser iniciado antes do cliente
- Devem existir os certificados, keystores e truststores
- Comportamento indefinido se o servidor for terminado antes do cliente, durante execução, ou se forem causados erros de propósito

### Usando o JAR (Recomendado)
Se o ficheiro JAR já estiver disponível na pasta `target`, não é necessário usar o Maven e com. 
*(Nota: É necessário utilizar o nome completo da classe, incluindo o package).*

**1. Executar o Servidor:**
java -jar server/target/sperta-server.jar [port] [password-cifra] [keystore] [password-keystore]

Exemplo:
``java -jar server/target/sperta-server.jar 22345 password ./security/server.keystore.jks password``

**2. Executar o Cliente:**
java -jar client/target/sperta-client.jar [IP:port] [truststore] [keystore] [password-keystore] [username] [password]

Exemplos:
``java -jar client/target/sperta-client.jar localhost:22345 ./security/alan.truststore.jks password ./security/alan.keystore.jks password alan password``
``java -jar client/target/sperta-client.jar localhost:22345 ./security/nuno.truststore.jks password ./security/nuno.keystore.jks password nuno password``

**3. Uso keytool**
(Aqui para não me esquecer; dentro da pasta security)

Gerar server keystore
``keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 365 -keystore server.keystore.jks -storetype JKS -dname "CN=SpertaServer, OU=DI, O=FCUL, L=Lisboa, ST=Lisboa, C=PT"``

Exportar certificado do servidor
``keytool -exportcert -alias server -keystore server.keystore.jks -file server.cer``

Gerar truststore cliente
``keytool -importcert -alias server -file server.cer -keystore <nome>.truststore.jks -storetype JKS``

Gerar keystore cliente
``keytool -genkeypair -alias <nome> -keyalg RSA -keysize 2048 -validity 365 -keystore <nome>.keystore.jks -storetype JKS -dname "CN=<nome>, OU=DI, O=FCUL, L=Lisboa, ST=Lisboa, C=PT"``

Exportar certificado cliente
``keytool -exportcert -alias <nome> -keystore <nome>.keystore.jks -file <nome>.cer``

**4. Passwords**
Para efeito de simplicidade, todas as passwords dos ficheiros providenciados são 'password'.

## Como compilar

Caso seja necessário gerar os ficheiros JAR novamente, execute na raiz do projeto:

``mvn install``

Para limpar os ficheiros de output, executar na raiz do projeto:

``mvn clean``

Ficheiros como trust/key stores, certificados, e ficheiros recebidos devem ser limpos à mão.


## Funcionalidades

* CREATE 'hm' 
    * Criar casa 'hm' - utilizador é Owner
* ADD 'user1' 'hm' 's' 
    * Adicionar utilizador 'user1' à casa 'hm', seção 's'
* RD 'hm' 's' 
    * Registar um Dispositivo na casa 'hm', na seção 's'
* EC 'hm' 'd' 'int' 
    * Enviar valor 'int' de estado/temporização, do dispositivo 'd' da casa 'hm', para o servidor
* RT 'hm' 
    * Receber a informação sobre o último comando (estados/temporizações) enviado a cada dispositivo da casa 'hm', desde  que o utilizador tenha permissões
* RH 'hm' 'd'
    * Receber o Histórico (ficheiro de log .csv) de comandos enviados ao dispositivo 'd' da casa 'hm', desde que o utilizador tenha permissões


Ficheiros do servidor são guardados na pasta ``/ficheiros/``
Ficheiros recebidos pelo cliente são guardados na pasta ``/ficheirosRecebidos/``


## Limitações do projeto

O projeto foi desenvolvido em Windows; os ficheiros criados pelo servidor podem apresentar problemas de permissões quando o programa é executado em sistemas Linux.

## Autor

* Rodrigo Santos FC61825