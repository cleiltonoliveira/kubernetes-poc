# Fase 3 - Deploy da API no Kubernetes

## Objetivo
Publicar a `auth-api` no cluster Kubernetes local, criar os manifests principais da aplicacao e validar que o deploy funciona com `Deployment` e `Service`.

## Resultado esperado
Ao final desta fase, voce deve ter:

- a imagem `auth-api:v1` disponivel para o cluster local
- um `Deployment` com 2 replicas da API
- um `Service` `ClusterIP` expondo a aplicacao internamente
- pods em estado `Running`
- acesso a API via `kubectl port-forward`
- validacao dos endpoints `/auth/health` e `/actuator/health`

## Pre-requisitos
- Fase 1 concluida
- Fase 2 concluida
- cluster `k3d` criado e ativo
- `kubectl` configurado para o cluster local
- imagem Docker `auth-api:v1` gerada com sucesso

## Antes de comecar
Confirme se o cluster esta disponivel:

```bash
kubectl cluster-info
kubectl get nodes
```

Confirme se a imagem existe localmente:

```bash
docker images | grep auth-api
```

Se a imagem ainda nao existir, gere novamente na raiz do projeto:

```bash
./gradlew clean bootJar
docker build -t auth-api:v1 .
```

## Ponto importante sobre cluster local
No `k3d`, o cluster nao usa automaticamente todas as imagens do Docker host. Para que o Kubernetes consiga iniciar seus pods com a imagem local, importe a imagem para o cluster:

```bash
k3d image import auth-api:v1 -c poc-cluster
```

Se o nome do seu cluster for diferente, ajuste o valor de `poc-cluster`.

## Estrutura sugerida

```text
k8s/
└── auth/
    ├── deployment.yaml
    └── service.yaml
```

Crie a pasta dos manifests, se ela ainda nao existir:

```bash
mkdir -p k8s/auth
```

## Passos praticos

### 1. Criar o Deployment
Crie o arquivo `k8s/auth/deployment.yaml` com este conteudo:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-api
  labels:
    app: auth-api

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
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 15
```

### 2. Entender o que esse Deployment faz
- cria 2 replicas da aplicacao
- usa a imagem `auth-api:v1`
- evita `pull` desnecessario da imagem com `IfNotPresent`
- expoe a porta `8080` do container
- usa o endpoint do Actuator para readiness e liveness

### 3. Criar o Service
Crie o arquivo `k8s/auth/service.yaml` com este conteudo:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: auth-service

spec:
  selector:
    app: auth-api
  ports:
    - name: http
      port: 80
      targetPort: 8080
  type: ClusterIP
```

### 4. Entender o que esse Service faz
- cria um endpoint estavel para a aplicacao
- balanceia o trafego entre os pods do `Deployment`
- expoe a API internamente no cluster como `auth-service`

Nesta fase, o acesso externo ainda sera feito por `port-forward`. Ingress fica para uma fase posterior.

### 5. Aplicar os manifests
Execute:

```bash
kubectl apply -f k8s/auth/deployment.yaml
kubectl apply -f k8s/auth/service.yaml
```

Confira os recursos criados:

```bash
kubectl get deployments
kubectl get pods
kubectl get services
```

### 6. Acompanhar a subida da aplicacao
Espere o rollout terminar:

```bash
kubectl rollout status deployment/auth-api
```

Se quiser acompanhar em tempo real:

```bash
kubectl get pods -w
```

### 7. Inspecionar se algo falhar
Se algum pod nao subir corretamente, use estes comandos:

```bash
kubectl describe deployment auth-api
kubectl describe pod <nome-do-pod>
kubectl logs <nome-do-pod>
kubectl get events --sort-by=.metadata.creationTimestamp
```

### 8. Testar o Service com port-forward
Como o `Service` e `ClusterIP`, faca o acesso local usando `port-forward`:

```bash
kubectl port-forward svc/auth-service 8080:80
```

Em outro terminal, teste:

```bash
curl http://localhost:8080/auth/health
curl http://localhost:8080/actuator/health
```

Respostas esperadas:

#### `/auth/health`

```json
{"status":"ok","service":"auth-api"}
```

#### `/actuator/health`

```json
{"status":"UP"}
```

### 9. Confirmar distribuicao das replicas
Veja os pods criados pelo deployment:

```bash
kubectl get pods -l app=auth-api -o wide
```

Veja o resumo do deployment:

```bash
kubectl describe deployment auth-api
```

### 10. Atualizar a imagem da aplicacao
Quando voce alterar a API e gerar uma nova imagem local, repita este fluxo:

```bash
./gradlew clean bootJar
docker build -t auth-api:v2 .
k3d image import auth-api:v2 -c poc-cluster
kubectl set image deployment/auth-api auth-api=auth-api:v2
kubectl rollout status deployment/auth-api
```

Se preferir manter a mesma tag `v1`, o mais seguro em ambiente local e:

```bash
docker build -t auth-api:v1 .
k3d image import auth-api:v1 -c poc-cluster
kubectl rollout restart deployment/auth-api
kubectl rollout status deployment/auth-api
```

### 11. Checklist de validacao
Considere a fase concluida se voce conseguir:

- importar a imagem `auth-api:v1` para o cluster
- aplicar `Deployment` e `Service` sem erro
- ver 2 pods `Running`
- validar o rollout com sucesso
- acessar `/auth/health` por `port-forward`
- acessar `/actuator/health` por `port-forward`

## Problemas comuns

### `ImagePullBackOff`
Causa provavel:
- a imagem nao foi importada para o `k3d`
- a tag da imagem no manifest nao existe

Solucao:

```bash
docker images | grep auth-api
k3d image import auth-api:v1 -c poc-cluster
kubectl describe pod <nome-do-pod>
```

### `CrashLoopBackOff`
Causa provavel:
- a aplicacao sobe localmente, mas falha dentro do container
- houve regressao na configuracao do `application.yml`

Solucao:

```bash
kubectl logs <nome-do-pod>
kubectl describe pod <nome-do-pod>
```

Se aparecer erro de datasource novamente, revise se o `application.yml` ainda contem:

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
      - org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
```

### O pod sobe, mas o `curl` local falha
Causa provavel:
- voce tentou acessar um `ClusterIP` diretamente do host

Solucao:

```bash
kubectl port-forward svc/auth-service 8080:80
```

### A nova versao da imagem nao entrou no ar
Causa provavel:
- o deployment ainda esta usando a imagem antiga
- a tag foi reutilizada sem reiniciar o deployment

Solucao:

```bash
kubectl rollout restart deployment/auth-api
kubectl rollout status deployment/auth-api
```

## Comandos uteis

```bash
kubectl get all
kubectl get pods -l app=auth-api
kubectl describe service auth-service
kubectl logs deployment/auth-api
kubectl delete -f k8s/auth/deployment.yaml
kubectl delete -f k8s/auth/service.yaml
```

## Proximo passo
Depois desta fase, voce estara pronto para a **Fase 4**, onde a aplicacao passara a se comunicar com outros servicos dentro do cluster usando DNS interno e `Service Discovery`.