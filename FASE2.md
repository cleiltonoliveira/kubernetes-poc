# Fase 2 - Dockerização da API Kotlin

## Objetivo
Criar uma API Spring Boot com Kotlin, empacotar a aplicação em JAR, gerar uma imagem Docker e validar a execução local antes do deploy no Kubernetes.

## Resultado esperado
Ao final desta fase, você deve ter:

- um projeto `auth-api` criado com Spring Boot e Kotlin
- um endpoint HTTP simples respondendo localmente
- um arquivo JAR gerado em `build/libs/`
- uma imagem Docker `auth-api:v1` criada com sucesso
- um container rodando em `localhost:8080`

## Pré-requisitos
- Fase 1 concluída
- Docker instalado e funcionando
- JDK 21 instalado
- acesso ao Spring Initializr ou a uma IDE com suporte a Spring Boot

## Estrutura sugerida

```text
auth-api/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/example/auth/
│   │   │       ├── AuthApiApplication.kt
│   │   │       └── HealthController.kt
│   │   └── resources/
│   │       └── application.yml
├── Dockerfile
├── build.gradle.kts
└── settings.gradle.kts
```

## Passos práticos

### 1. Criar o projeto base
Use o Spring Initializr com esta configuração:

- **Project:** Gradle - Kotlin
- **Language:** Kotlin
- **Spring Boot:** versão estável atual
- **Group:** `com.example`
- **Artifact:** `auth-api`
- **Name:** `auth-api`
- **Packaging:** Jar
- **Java:** 21

### 2. Adicionar as dependências
Inclua no projeto as dependências abaixo:

- Spring Web
- Spring Actuator
- Spring Data JPA
- PostgreSQL Driver

Se quiser conferir manualmente no `build.gradle.kts`, valide se há entradas equivalentes a estas:

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

## Observação importante sobre banco de dados
Nesta fase, o foco ainda não é conectar no PostgreSQL. Se você já adicionar JPA e o driver do PostgreSQL, a aplicação pode falhar ao iniciar por falta de configuração de datasource.

Para evitar isso agora, use uma destas abordagens:

1. manter somente `Spring Web` e `Actuator` nesta fase, adicionando JPA/PostgreSQL mais tarde
2. manter todas as dependências e desativar temporariamente a autoconfiguração de datasource

Se optar pela segunda abordagem, adicione isto ao `application.yml`:

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
      - org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
```

## 3. Criar um endpoint simples para validação
Crie um controller mínimo para confirmar que a API sobe corretamente.

### Exemplo de controller

```kotlin
package com.example.auth

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class HealthController {
    @GetMapping("/health")
    fun health(): Map<String, String> {
        return mapOf(
            "status" to "ok",
            "service" to "auth-api"
        )
    }
}
```

## 4. Configurar a aplicação
Use uma configuração mínima em `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: auth-api
  autoconfigure:
    exclude:
      - org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
      - org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration

management:
  endpoints:
    web:
      exposure:
        include: health,info

server:
  port: 8080
```

## 5. Rodar a API localmente sem Docker
Antes de containerizar, valide que a aplicação sobe normalmente:

```bash
./gradlew bootRun
```

Teste os endpoints:

```bash
curl http://localhost:8080/auth/health
curl http://localhost:8080/actuator/health
```

Resposta esperada para `/auth/health`:

```json
{"status":"ok","service":"auth-api"}
```

## 6. Gerar o JAR da aplicação
Depois que a API funcionar localmente, gere o pacote:

```bash
./gradlew clean bootJar
```

Verifique se o arquivo foi criado:

```bash
ls build/libs/
```

## 7. Criar o Dockerfile
No diretório raiz do projeto, crie um `Dockerfile` com este conteúdo:

```dockerfile
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Por que usar esse formato?
- `21-jre` reduz o tamanho da imagem em relação ao `jdk`
- `COPY build/libs/*.jar app.jar` evita depender do nome exato do artefato gerado pelo Gradle

## 8. Build da imagem Docker
Com o JAR gerado, crie a imagem:

```bash
docker build -t auth-api:v1 .
```

Confira se a imagem existe:

```bash
docker images | grep auth-api
```

## 9. Rodar o container localmente
Suba o container expondo a porta 8080:

```bash
docker run --rm -p 8080:8080 auth-api:v1
```

Em outro terminal, teste novamente:

```bash
curl http://localhost:8080/auth/health
curl http://localhost:8080/actuator/health
```

## 10. Checklist de validação
Considere a fase concluída se você conseguir:

- criar o projeto Kotlin com Spring Boot
- subir a aplicação localmente com `./gradlew bootRun`
- acessar o endpoint `/auth/health`
- gerar o JAR com `./gradlew clean bootJar`
- construir a imagem `auth-api:v1`
- rodar o container e acessar a API pela porta `8080`

## Problemas comuns

### Erro de datasource ao iniciar a aplicação
Causa provável: JPA e PostgreSQL foram adicionados sem configuração de banco.

Solução:
- desative temporariamente `DataSourceAutoConfiguration` e `HibernateJpaAutoConfiguration`
- ou adie JPA/PostgreSQL para a fase de banco de dados

### Erro no `COPY build/libs/*.jar`
Causa provável: o JAR ainda não foi gerado.

Solução:

```bash
./gradlew clean bootJar
ls build/libs/
```

### Porta 8080 já está em uso
Solução:

```bash
docker run --rm -p 8081:8080 auth-api:v1
```

Nesse caso, teste em:

```bash
curl http://localhost:8081/auth/health
```

### A imagem foi criada, mas a aplicação não sobe
Verifique os logs do container:

```bash
docker run --rm -p 8080:8080 auth-api:v1
```

Leia a mensagem de erro e corrija o problema antes de avançar para a próxima fase.

## Próximo passo
Depois desta fase, você estará pronto para a **Fase 3**, onde a API será publicada no Kubernetes com `Deployment` e `Service`.
