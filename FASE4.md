# Fase 4 - Comunicacao entre microsservicos

## Objetivo
Entender como um microsservico se comunica com outro dentro do cluster Kubernetes usando `Service`, DNS interno e `Service Discovery`.

Nesta fase, o cenario principal e:

- `orders-api` chama `auth-api`
- a chamada acontece pelo `Service` `auth-service`
- o endereco usado dentro do cluster e `http://auth-service`

## Resultado esperado
Ao final desta fase, voce deve ser capaz de:

- explicar por que pods nao se comunicam por IP fixo
- entender o papel do `Service` como endpoint estavel
- resolver `auth-service` via DNS dentro do cluster
- chamar `http://auth-service/auth/health` de dentro de outro pod
- entender a diferenca entre `localhost`, nome do pod e nome do `Service`

## Pre-requisitos
- Fase 3 concluida
- `auth-api` publicado no cluster
- `auth-service` criado e funcionando
- pelo menos um pod da `auth-api` em estado `Running`

## Antes de comecar
Confirme que os recursos da Fase 3 continuam saudaveis:

```bash
kubectl get deployment auth-api
kubectl get pods -l app=auth-api
kubectl get svc auth-service
kubectl get endpoints auth-service
kubectl describe svc auth-service
```

Se o `Service` nao tiver endpoints, a comunicacao interna nao vai funcionar.

## Conceitos principais

### 1. Pods sao efemeros
Pods podem ser recriados e seus IPs podem mudar. Por isso, microsservicos nao devem depender do IP direto de outro pod.

### 2. O `Service` cria um endpoint estavel
No seu projeto atual, o nome estavel para acessar a API de autenticacao e:

```text
auth-service
```

Esse nome aponta para os pods da `auth-api` por meio do seletor definido no `Service`.

### 3. O DNS interno do Kubernetes resolve o nome do servico
Dentro do cluster, os formatos mais comuns sao:

- mesmo namespace: `http://auth-service`
- com namespace: `http://auth-service.default`
- FQDN completo: `http://auth-service.default.svc.cluster.local`

Se voce estiver no mesmo namespace do servico, o nome curto `auth-service` ja e suficiente.

### 4. Porta do `Service` x porta do container
No seu projeto atual:

- o container da `auth-api` escuta na porta `8080`
- o `auth-service` expoe a aplicacao na porta `80`

Por isso, de dentro do cluster, a URL esperada e:

```text
http://auth-service/auth/health
```

Nao e necessario informar `:80` na URL, porque essa ja e a porta padrao HTTP do `Service`.

### 5. `localhost` nao representa outro microsservico
Dentro de um pod, `localhost` sempre aponta para o proprio container.

Isso significa que, dentro de um futuro `orders-api`, estas duas chamadas tem comportamentos bem diferentes:

- correto: `http://auth-service/auth/health`
- incorreto: `http://localhost:8080/auth/health`

No segundo caso, o `orders-api` estaria tentando chamar ele mesmo, e nao a `auth-api`.

## Arquitetura desta fase

```text
[orders-api ou pod de teste]
          |
          v
   http://auth-service
          |
          v
      [Service]
          |
          v
   [Pods da auth-api]
```

## Passos praticos

### 1. Validar DNS interno com um pod temporario
Suba um pod temporario com `busybox` para testar resolucao de nome e acesso HTTP de dentro do cluster:

```bash
kubectl run dns-test --rm -it --image=busybox:1.36 --restart=Never -- sh
```

Dentro do shell do pod, execute:

```sh
nslookup auth-service
nslookup auth-service.default.svc.cluster.local
wget -qO- http://auth-service/auth/health
wget -qO- http://auth-service.default.svc.cluster.local/auth/health
wget -qO- http://auth-service/actuator/health
exit
```

### O que voce deve observar
- `nslookup auth-service` deve retornar um IP interno do cluster
- a chamada HTTP deve retornar o JSON da `auth-api`

Resposta esperada para `/auth/health`:

```json
{"status":"ok","service":"auth-api"}
```

Resposta esperada para `/actuator/health`:

```json
{"status":"UP"}
```

Se isso funcionar, voce ja confirmou que o cluster consegue resolver o servico e encaminhar a requisicao corretamente.

### 2. Simular o comportamento do `orders-api`
Voce ainda nao precisa ter o `orders-api` pronto para entender a comunicacao. Um pod de teste ja permite provar o fluxo.

Se quiser manter um cliente persistente para experimentos, crie um pod simples como este:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: orders-client
spec:
  containers:
    - name: orders-client
      image: busybox:1.36
      command: ["sh", "-c", "sleep 3600"]
  restartPolicy: Never
```

Aplicar:

```bash
kubectl apply -f orders-client.yaml
```

Entrar no pod:

```bash
kubectl exec -it orders-client -- sh
```

Testar a chamada:

```sh
wget -qO- http://auth-service/auth/health
```

Remover quando terminar:

```bash
kubectl delete pod orders-client
```

### 3. Como o `orders-api` deve ser configurado no futuro
Quando voce criar o `orders-api`, o ideal e nao fixar a URL no codigo. Prefira configuracao externa.

Exemplo de propriedade em `application.yml`:

```yaml
services:
  auth:
    base-url: http://auth-service
```

Exemplo de variavel de ambiente no `Deployment`:

```yaml
env:
  - name: AUTH_API_BASE_URL
    value: http://auth-service
```

Exemplo conceitual da chamada no cliente:

```text
GET http://auth-service/auth/health
```

### 4. Observar o balanceamento do `Service`
O `Service` distribui trafego entre os pods da `auth-api`.

Com a implementacao atual, a resposta do endpoint e sempre igual, entao voce comprova conectividade, mas nao enxerga facilmente qual replica respondeu.

Mesmo sem alterar o codigo, voce ja pode observar que o `Service` possui varios backends:

```bash
kubectl get endpoints auth-service
kubectl get pods -l app=auth-api -o wide
```

Se quiser reforcar o experimento, escale a API:

```bash
kubectl scale deployment auth-api --replicas=4
kubectl rollout status deployment/auth-api
kubectl get pods -l app=auth-api
kubectl get endpoints auth-service
```

## Checklist de validacao
Considere a fase concluida se voce conseguir:

- resolver `auth-service` dentro do cluster com `nslookup`
- chamar `http://auth-service/auth/health` de dentro de um pod
- explicar por que `localhost` nao serve para comunicacao entre microsservicos
- identificar o `Service` como ponto estavel de acesso
- confirmar que `auth-service` aponta para os pods corretos da `auth-api`

## Problemas comuns

### `nslookup auth-service` falha
Causa provavel:
- o `Service` nao existe
- o nome usado esta errado
- voce esta em outro namespace

Solucao:

```bash
kubectl get svc
kubectl get svc auth-service
kubectl get svc -A | grep auth-service
```

Se estiver em outro namespace, use o nome com namespace:

```text
auth-service.<namespace>.svc.cluster.local
```

### O DNS resolve, mas a chamada HTTP falha
Causa provavel:
- os pods da `auth-api` nao estao prontos
- o `Service` nao tem endpoints ativos

Solucao:

```bash
kubectl get pods -l app=auth-api
kubectl get endpoints auth-service
kubectl logs deployment/auth-api
```

### Uso de `localhost` no microsservico cliente
Causa provavel:
- o cliente foi configurado como se o outro servico estivesse no mesmo processo

Solucao:
- troque `localhost` pelo nome do `Service`
- use `http://auth-service`

### Nome do `Service` confundido com nome do `Deployment`
No seu projeto atual:

- `Deployment`: `auth-api`
- `Service`: `auth-service`

Para comunicacao interna, use o nome do `Service`, nao o nome do `Deployment`.

### `no endpoints available for service`
Causa provavel:
- labels do pod e selector do `Service` nao combinam
- pods ainda nao ficaram `Ready`

Solucao:

```bash
kubectl get pods -l app=auth-api
kubectl describe svc auth-service
kubectl get endpoints auth-service
```

## Comandos uteis

```bash
kubectl get svc
kubectl get endpoints auth-service
kubectl get pods -l app=auth-api
kubectl describe svc auth-service
kubectl run dns-test --rm -it --image=busybox:1.36 --restart=Never -- sh
kubectl exec -it orders-client -- sh
```

## Proximo passo
Depois desta fase, voce estara pronto para a **Fase 5**, onde a aplicacao passara a depender de persistencia com PostgreSQL, `PersistentVolume`, `PersistentVolumeClaim` e `StatefulSet`.