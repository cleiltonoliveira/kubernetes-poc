# Fase 5 - Banco de dados e persistencia

## Objetivo
Subir um PostgreSQL no cluster Kubernetes, garantir persistencia dos dados com `PersistentVolumeClaim` e conectar a `auth-api` a um datasource real.

## Resultado esperado
Ao final desta fase, voce deve ter:

- um PostgreSQL rodando no cluster com `StatefulSet`
- um `Service` interno para o banco
- uma senha armazenada em `Secret`
- um `PersistentVolumeClaim` em estado `Bound`
- a `auth-api` iniciando com conexao real ao PostgreSQL
- validacao da conexao com `psql` de dentro do cluster

## Pre-requisitos
- Fase 4 concluida
- `auth-api` publicada e saudavel no cluster
- `auth-service` funcionando
- `kubectl` apontando para o cluster correto
- dependencias de JPA e PostgreSQL presentes no projeto

No estado atual do repositorio, as dependencias necessarias ja existem em `build.gradle.kts`.

## Antes de comecar
Confirme que a API continua estavel e veja se o cluster possui `StorageClass` disponivel:

```bash
kubectl get pods -l app=auth-api
kubectl get svc
kubectl get storageclass
kubectl get pvc
```

Em `k3d` e `k3s`, normalmente existe uma `StorageClass` default como `local-path`. Se nao houver nenhuma `StorageClass`, o `PVC` pode ficar preso em `Pending`.

## Conceitos principais

### 1. Por que usar `StatefulSet`
Banco de dados precisa de identidade mais estavel do que um deployment comum.

O `StatefulSet` ajuda nisso porque:

- mantem nome previsivel para o pod, como `postgres-0`
- preserva identidade do workload
- se encaixa melhor em componentes com estado

### 2. Por que usar `PersistentVolumeClaim`
Sem volume persistente, os dados do PostgreSQL seriam perdidos quando o pod fosse recriado.

O `PVC` garante que os dados sobrevivam a reinicios e recriacoes do pod.

### 3. Por que usar `Secret`
Mesmo em ambiente de estudo, senha de banco nao deve ficar hardcoded em manifesto publico.

Nesta fase, o minimo necessario e guardar a senha do PostgreSQL em um `Secret`.

### 4. O que muda na `auth-api`
Nas fases anteriores, a `auth-api` estava com a auto-configuracao de datasource desativada para conseguir subir sem banco.

Agora isso precisa mudar.

Para a API usar um banco real, voce deve:

- remover as exclusoes de datasource do `application.yml`
- configurar URL, usuario e senha do PostgreSQL
- reaplicar o deployment da `auth-api`

## Estrutura sugerida

```text
k8s/
├── auth/
└── database/
    ├── postgres-secret.yaml
    ├── postgres-pvc.yaml
    ├── postgres-service.yaml
    └── postgres-statefulset.yaml
```

Crie a pasta, se ela ainda nao existir:

```bash
mkdir -p k8s/database
```

## Passos praticos

### 1. Criar o `Secret` do PostgreSQL
Crie o arquivo `k8s/database/postgres-secret.yaml`:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: postgres-secret
type: Opaque
stringData:
  POSTGRES_PASSWORD: password
```

### Por que `stringData`
Para estudo, `stringData` e mais simples de manter. O Kubernetes converte isso internamente para o formato armazenado do secret.

### 2. Criar o `PersistentVolumeClaim`
Crie o arquivo `k8s/database/postgres-pvc.yaml`:

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
```

Se sua `StorageClass` default nao estiver configurada, voce pode precisar definir `storageClassName` explicitamente.

### 3. Criar o `Service` interno do banco
Crie o arquivo `k8s/database/postgres-service.yaml`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: postgres-service
spec:
  selector:
    app: postgres
  ports:
    - name: postgres
      port: 5432
      targetPort: 5432
  type: ClusterIP
```

Esse sera o nome que a aplicacao vai usar para localizar o banco:

```text
postgres-service
```

### 4. Criar o `StatefulSet` do PostgreSQL
Crie o arquivo `k8s/database/postgres-statefulset.yaml`:

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
spec:
  serviceName: postgres-service
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
        - name: postgres
          image: postgres:16-alpine
          ports:
            - containerPort: 5432
          env:
            - name: POSTGRES_DB
              value: authdb
            - name: POSTGRES_USER
              value: auth_user
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-secret
                  key: POSTGRES_PASSWORD
            - name: PGDATA
              value: /var/lib/postgresql/data/pgdata
          volumeMounts:
            - name: postgres-data
              mountPath: /var/lib/postgresql/data
          readinessProbe:
            exec:
              command: ["sh", "-c", "pg_isready -U auth_user -d authdb"]
            initialDelaySeconds: 10
            periodSeconds: 10
          livenessProbe:
            exec:
              command: ["sh", "-c", "pg_isready -U auth_user -d authdb"]
            initialDelaySeconds: 30
            periodSeconds: 20
      volumes:
        - name: postgres-data
          persistentVolumeClaim:
            claimName: postgres-pvc
```

### 5. Aplicar os manifests do banco
Execute:

```bash
kubectl apply -f k8s/database/postgres-secret.yaml
kubectl apply -f k8s/database/postgres-pvc.yaml
kubectl apply -f k8s/database/postgres-service.yaml
kubectl apply -f k8s/database/postgres-statefulset.yaml
```

Valide os recursos criados:

```bash
kubectl get secret postgres-secret
kubectl get pvc
kubectl get svc postgres-service
kubectl get statefulset postgres
kubectl get pods -l app=postgres
kubectl rollout status statefulset/postgres
```

O esperado e:

- `postgres-pvc` em estado `Bound`
- `postgres-0` em estado `Running`
- `postgres-service` disponivel na porta `5432`

### 6. Validar o banco de dentro do cluster
Entre no pod do PostgreSQL e teste com `psql`:

```bash
kubectl exec -it postgres-0 -- psql -U auth_user -d authdb -c '\l'
kubectl exec -it postgres-0 -- psql -U auth_user -d authdb -c '\dt'
```

Se a conexao funcionar, o banco ja esta operando corretamente.

### 7. Preparar a `auth-api` para usar o PostgreSQL real
No estado atual do projeto, o arquivo `src/main/resources/application.yml` ainda desativa a auto-configuracao de datasource para permitir startup sem banco.

Para esta fase, remova este bloco:

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
      - org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
```

Esse ajuste e obrigatorio. Se ele continuar no arquivo, a `auth-api` nao vai usar o PostgreSQL mesmo com o banco disponivel.

### 8. Configurar a conexao da `auth-api`
Voce pode fazer isso por `application.yml` ou por variaveis de ambiente no deployment. Para esta fase, o caminho mais pratico no Kubernetes e usar variaveis de ambiente no manifesto da `auth-api`.

Adicione ao container da `auth-api` em `k8s/auth/deployment.yaml` um bloco como este:

```yaml
env:
  - name: SPRING_DATASOURCE_URL
    value: jdbc:postgresql://postgres-service:5432/authdb
  - name: SPRING_DATASOURCE_USERNAME
    value: auth_user
  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: postgres-secret
        key: POSTGRES_PASSWORD
  - name: SPRING_JPA_HIBERNATE_DDL_AUTO
    value: update
```

### Por que isso funciona
O Spring Boot mapeia essas variaveis automaticamente para as propriedades:

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.jpa.hibernate.ddl-auto`

### 9. Reaplicar a `auth-api`
Depois de ajustar o `application.yml` e o deployment da API, reaplique a aplicacao:

```bash
kubectl apply -f k8s/auth/deployment.yaml
kubectl rollout restart deployment/auth-api
kubectl rollout status deployment/auth-api
kubectl logs deployment/auth-api
```

Se tudo estiver correto, a API deve iniciar sem o erro de datasource.

### 10. Validar a conexao da aplicacao com o banco
Depois do rollout da API, confirme:

```bash
kubectl get pods -l app=auth-api
kubectl logs deployment/auth-api
kubectl port-forward svc/auth-service 8080:80
```

Em outro terminal:

```bash
curl http://localhost:8080/auth/health
curl http://localhost:8080/actuator/health
```

Mesmo que a aplicacao ainda nao tenha entidades e repositories reais, esta fase ja esta valida quando:

- o PostgreSQL sobe com persistencia
- a API conecta num datasource real
- o startup ocorre sem erro de datasource

## Checklist de validacao
Considere a fase concluida se voce conseguir:

- criar `Secret`, `PVC`, `Service` e `StatefulSet` do PostgreSQL
- ver o `PVC` em estado `Bound`
- acessar o banco com `psql` dentro do cluster
- remover as exclusoes de datasource do `application.yml`
- fazer a `auth-api` subir usando `postgres-service:5432`
- validar que a API continua respondendo apos a conexao com o banco

## Problemas comuns

### `PersistentVolumeClaim` preso em `Pending`
Causa provavel:
- nao existe `StorageClass` default
- o cluster nao conseguiu provisionar volume dinamicamente

Solucao:

```bash
kubectl get storageclass
kubectl describe pvc postgres-pvc
```

Se necessario, configure `storageClassName` explicitamente no PVC.

### `CrashLoopBackOff` no PostgreSQL
Causa provavel:
- senha ausente ou secret com nome errado
- problema no volume
- imagem iniciou, mas nao conseguiu preparar o diretorio de dados

Solucao:

```bash
kubectl logs postgres-0
kubectl describe pod postgres-0
kubectl describe pvc postgres-pvc
```

### `password authentication failed`
Causa provavel:
- senha do `Secret` nao bate com a senha usada pela aplicacao
- banco foi inicializado antes com outro valor e o volume persistiu

Solucao:
- confirme o valor do secret usado pela aplicacao
- se for ambiente de estudo e o banco puder ser recriado, apague o `StatefulSet` e o `PVC` e crie de novo

### Erro de datasource continua na `auth-api`
Causa provavel:
- as exclusoes de datasource do Spring Boot 4 ainda estao no `application.yml`
- variaveis `SPRING_DATASOURCE_*` nao foram adicionadas corretamente ao deployment

Solucao:

```bash
kubectl logs deployment/auth-api
kubectl describe deployment auth-api
```

Revise se o `application.yml` nao contem mais:

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
      - org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
```

### `Connection refused` para `postgres-service:5432`
Causa provavel:
- `postgres-service` ainda nao tem endpoints
- o pod do banco nao ficou pronto
- a porta do `Service` ou do container esta errada

Solucao:

```bash
kubectl get svc postgres-service
kubectl get endpoints postgres-service
kubectl get pods -l app=postgres
kubectl describe svc postgres-service
```

### O banco sobe, mas nao ha persistencia
Causa provavel:
- o PostgreSQL foi iniciado sem volume persistente correto
- o PVC nao esta realmente montado no pod

Solucao:

```bash
kubectl describe pod postgres-0
kubectl describe pvc postgres-pvc
```

Verifique se o volume esta montado em `/var/lib/postgresql/data`.

## Comandos uteis

```bash
kubectl get pvc
kubectl get statefulset
kubectl get svc postgres-service
kubectl get endpoints postgres-service
kubectl logs postgres-0
kubectl exec -it postgres-0 -- psql -U auth_user -d authdb
kubectl logs deployment/auth-api
```

## Proximo passo
Depois desta fase, voce estara pronto para a **Fase 6**, onde a configuracao da aplicacao sera organizada com `ConfigMap` e `Secret` em vez de depender de valores inline no deployment.