# Fase 1 — Fundamentos Kubernetes

## Objetivo
Entender os conceitos básicos do Kubernetes e praticar comandos essenciais em um cluster local.

## Conceitos para aprender
- Cluster
- Node
- Pod
- Deployment
- ReplicaSet
- Service
- Namespace

## Pré-requisitos
- Docker instalado
- k3d instalado ([Guia oficial](https://k3d.io/#installation))
- kubectl instalado ([Guia oficial](https://kubernetes.io/docs/tasks/tools/))

## Passos práticos

### 1. Criar um cluster local com k3d
```bash
k3d cluster create poc-cluster
```

### 2. Verificar o cluster
```bash
kubectl get nodes
kubectl cluster-info
```

### 3. Deploy de um serviço simples (nginx)
```bash
kubectl create deployment nginx --image=nginx
```

### 4. Expor o serviço nginx
```bash
kubectl expose deployment nginx --port=80 --type=NodePort
```

### 5. Listar recursos
```bash
kubectl get pods
kubectl get services
kubectl get deployments
```

### 6. Inspecionar recursos
```bash
kubectl describe pod <nome-do-pod>
kubectl logs <nome-do-pod>
kubectl exec -it <nome-do-pod> -- sh
```

> Dica: Use `kubectl get all` para ver todos os recursos do namespace atual.

## Exercício extra
- Delete o deployment do nginx e crie novamente.
- Altere o número de réplicas do deployment:
  ```bash
  kubectl scale deployment nginx --replicas=3
  ```
- Observe como os pods são criados automaticamente.

## Recursos úteis
- [Documentação oficial do Kubernetes](https://kubernetes.io/pt/docs/concepts/)
- [Cheat Sheet kubectl](https://kubernetes.io/docs/reference/kubectl/cheatsheet/)

---

Pronto! Após concluir esta fase, você terá um cluster local funcional e domínio dos comandos básicos do Kubernetes.