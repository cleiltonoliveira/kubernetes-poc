# POC Kubernetes Local

## Guia Completo de Estudo com Kotlin/Java

## Objetivo

Este documento tem como objetivo estruturar uma POC local para aprendizado prático de Kubernetes utilizando:

- Kotlin
- Java
- Microsserviços
- Observabilidade
- Banco de dados
- CI/CD
- Escalabilidade

A ideia é simular um ambiente próximo de produção, mas rodando localmente, permitindo entender na prática como Kubernetes funciona.

## Objetivos de aprendizado

Ao concluir esta POC, você deverá entender:

- Containers
- Orquestração
- Pods
- Deployments
- Services
- Ingress
- Volumes
- ConfigMaps
- Secrets
- Observabilidade
- Networking interno
- Balanceamento
- Escalabilidade
- CI/CD
- Helm
- Troubleshooting
- Deploy de microsserviços

## Arquitetura da POC

### Estrutura geral

```text
                   [Frontend]
                        |
                        v
               [Ingress Controller]
                        |
        ---------------------------------
        |               |               |
        v               v               v
    Auth API       Users API      Orders API
        |                               |
        |-------------------------------|
                        |
                        v
                  PostgreSQL

                + Redis
                + Prometheus
                + Grafana
                + Loki
```

## Tecnologias recomendadas

### Cluster Kubernetes local

**Recomendado:** `k3d`

**Motivos:**

- Muito leve
- Fácil setup
- Rápido
- Ideal para notebook pessoal
- Usa Docker internamente

**Alternativa:** `Minikube`

### Stack backend

- **Linguagem:** Kotlin
- **Framework:** Spring Boot
- **Build tool:** escolha entre Gradle e Maven
- **Recomendação:** Gradle Kotlin DSL

### Banco de dados e cache

- **Relacional:** PostgreSQL
- **Cache:** Redis

### Observabilidade

- **Logs:** Loki e Promtail
- **Métricas:** Prometheus
- **Dashboards:** Grafana

## Estrutura do projeto

```text
poc-k8s/
│
├── apps/
│   ├── auth-api/
│   ├── users-api/
│   ├── orders-api/
│   └── frontend/
│
├── infra/
│   ├── postgres/
│   ├── redis/
│   ├── monitoring/
│   └── ingress/
│
├── k8s/
│   ├── auth/
│   ├── users/
│   ├── orders/
│   ├── database/
│   ├── ingress/
│   └── observability/
│
├── scripts/
│
└── docs/
```

## Roadmap completo

### Fase 1 - Fundamentos Kubernetes

**Objetivo:** entender os conceitos básicos.

#### Conceitos para aprender

- Cluster
- Node
- Pod
- Deployment
- ReplicaSet
- Service
- Namespace

#### Instalação

1. Instalar Docker.
2. Instalar `k3d`.
3. Criar o cluster:

```bash
k3d cluster create poc-cluster
```

4. Instalar `kubectl`.

#### Exercício 1 - Deploy simples

**Subir nginx:**

```bash
kubectl create deployment nginx --image=nginx
```

**Expor serviço:**

```bash
kubectl expose deployment nginx --port=80 --type=NodePort
```

**Comandos importantes:**

```bash
kubectl get pods
kubectl get services
kubectl describe pod <pod-name>
kubectl logs <pod-name>
kubectl exec -it <pod-name> -- sh
```

### Fase 2 - Dockerização da API Kotlin

**Objetivo:** criar uma API real.

#### Criar API Spring Boot

**Dependências:**

- Spring Web
- Spring Actuator
- Spring Data JPA
- PostgreSQL Driver

#### Estrutura simples

```text
auth-api/
├── src/
├── Dockerfile
├── build.gradle.kts
└── settings.gradle.kts
```

#### Dockerfile exemplo

```dockerfile
FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY build/libs/auth-api.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### Build da imagem

```bash
docker build -t auth-api:v1 .
```

#### Rodar localmente

```bash
docker run -p 8080:8080 auth-api:v1
```

### Fase 3 - Deploy da API no Kubernetes

**Objetivo:** subir sua primeira aplicação real.

#### Deployment YAML

`deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-api

spec:
  replicas: 2

  selector:
    matchLabels:
      app: auth-api

  template:
    metadata:
      labels:
        app: auth-api

    spec:
      containers:
        - name: auth-api
          image: auth-api:v1

          ports:
            - containerPort: 8080
```

#### Service YAML

`service.yaml`

```yaml
apiVersion: v1
kind: Service

metadata:
  name: auth-service

spec:
  selector:
    app: auth-api

  ports:
    - port: 80
      targetPort: 8080

  type: ClusterIP
```

#### Aplicar recursos

```bash
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
```

### Fase 4 - Comunicação entre microsserviços

**Objetivo:** entender DNS interno do Kubernetes.

#### Cenário

- Orders API chama Auth API
- `http://auth-service`

#### Aprendizados

- DNS interno
- Service Discovery
- Balanceamento automático
- Comunicação interna

### Fase 5 - Banco de Dados

**Objetivo:** persistência.

#### Conceitos importantes

- PersistentVolume
- PersistentVolumeClaim
- StatefulSet

#### Deploy PostgreSQL

Utilizar:

- StatefulSet
- PVC
- Secret para senha

```yaml
apiVersion: v1
kind: Secret

metadata:
  name: postgres-secret

type: Opaque

data:
  POSTGRES_PASSWORD: cGFzc3dvcmQ=
```

### Fase 6 - ConfigMaps e Secrets

**Objetivo:** gerenciar configurações.

#### ConfigMap exemplo

```yaml
apiVersion: v1
kind: ConfigMap

metadata:
  name: auth-config

data:
  SPRING_PROFILES_ACTIVE: dev
```

#### Uso em deployment

```yaml
envFrom:
  - configMapRef:
      name: auth-config
```

### Fase 7 - Ingress

**Objetivo:** simular ambiente real.

#### Instalar

- NGINX Ingress Controller

#### Rotas

- auth.local
- orders.local
- grafana.local

#### Exemplo de Ingress

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress

metadata:
  name: api-ingress

spec:
  rules:
    - host: auth.local

      http:
        paths:
          - path: /
            pathType: Prefix

            backend:
              service:
                name: auth-service
                port:
                  number: 80
```

### Fase 8 - Observabilidade

**Objetivo:** monitoramento completo.

#### Logs

- Loki
- Promtail

#### Métricas

- Prometheus
- Grafana

#### O que monitorar

- CPU
- Memória
- Latência
- Requests
- Erros
- Reinício de Pods

### Fase 9 - Escalabilidade

**Objetivo:** entender o poder do Kubernetes.

#### Escalar aplicação

```bash
kubectl scale deployment auth-api --replicas=5
```

#### Teste de carga

Ferramentas:

- k6
- hey
- wrk

#### Aprendizados

- Load balancing
- Self healing
- Replicação
- Alta disponibilidade

### Fase 10 - CI/CD

**Objetivo:** automatizar deploy.

**Recomendação:** GitHub Actions

#### Fluxo

```text
Push GitHub
   ↓
Build Gradle
   ↓
Docker Build
   ↓
Push Docker Registry
   ↓
kubectl apply
```

### Fase 11 - Helm

**Objetivo:** gerenciar aplicações complexas.

**Ferramenta:** Helm

#### O que aprender

- charts
- templates
- values.yaml
- releases

### Fase 12 - Troubleshooting

**Objetivo:** aprender debug real.

#### Problemas para simular

Faça propositalmente:

- Pod crashando
- Falta de memória
- Serviço indisponível
- Banco fora do ar
- DNS quebrado
- Configuração errada

#### Ferramentas úteis

- Interface gráfica: Lens
- Muito importante aprender os comandos abaixo:

```bash
kubectl describe
kubectl logs
kubectl exec
kubectl get events
```

## Roadmap semanal

### Semana 1

- Docker
- Containers
- k3d
- kubectl
- nginx

### Semana 2

- Spring Boot
- Dockerização
- Deployments
- Services

### Semana 3

- PostgreSQL
- Volumes
- Secrets
- ConfigMaps

### Semana 4

- Ingress
- DNS
- Comunicação interna

### Semana 5

- Prometheus
- Grafana
- Loki

### Semana 6

- Escalabilidade
- HPA
- Teste de carga

### Semana 7

- GitHub Actions
- CI/CD

### Semana 8

- Helm
- Organização da infraestrutura

## Resultado esperado

Ao finalizar a POC você terá:

- [x] Cluster Kubernetes local
- [x] Microsserviços Kotlin/Spring Boot
- [x] Banco PostgreSQL
- [x] Redis
- [x] Observabilidade completa
- [x] Ingress
- [x] Logs centralizados
- [x] Escalabilidade
- [x] CI/CD
- [x] Helm
- [x] Ambiente semelhante à produção

## Próximos passos futuros

Depois dessa POC local, você poderá evoluir facilmente para:

- Google Cloud GKE
- Amazon Web Services EKS
- Microsoft AKS

## Recomendação final

O segredo para aprender Kubernetes não é teoria.

É:

- Deployar
- Quebrar
- Investigar
- Corrigir
- Automatizar

Kubernetes fica muito mais simples quando você começa a enxergar:

- Rede
- Containers
- Infraestrutura
- Observabilidade
- Serviços

como um único ecossistema integrado.