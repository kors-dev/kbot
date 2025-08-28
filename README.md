# kbot

**kbot** is a Telegram bot created as part of the Prometheus course (week 2).  
It is implemented in Go using the Cobra library for CLI creation and Telebot for Telegram integration.

---

## Features
- Responds to text messages in Telegram  
- `version` command prints the application version  
- Easily extendable for new commands and features  

---

## CI/CD Automation (GitHub Actions → GHCR → ArgoCD → Kubernetes)

This repository is equipped with a **fully automated deployment pipeline**:

1. A **push to the `develop` branch** triggers the GitHub Actions workflow.
2. The workflow builds a `linux/amd64` Docker image and publishes it to **GHCR**.
3. The workflow automatically updates `helm/values.yaml` (`image.tag`, `registry`, `repository`, `os`, `arch`) and commits changes back to `develop`.
4. **ArgoCD** tracks the `develop` branch (path: `helm`) and **synchronizes** changes into the Kubernetes cluster.
5. Kubernetes deploys the new version of the bot.

**Example image tag**:
```
ghcr.io/kors-dev/kbot:v1.16.0-<shortsha>-linux-amd64
```

---

## Requirements
- Kubernetes cluster with ArgoCD installed  
- Namespace `kbot`  
- A Kubernetes Secret with the Telegram bot token  

Create the secret:
```bash
kubectl create ns kbot || true

kubectl -n kbot create secret generic kbot   --from-literal=token="<YOUR_TELEGRAM_BOT_TOKEN>"
```

The Helm chart expects a secret named **`kbot`** with key `token`.  
This will be mapped into the container as environment variable `TELE_TOKEN`.

---

## Helm values (main fields)

`helm/values.yaml`:
```yaml
image:
  registry: "ghcr.io"
  repository: "kors-dev/kbot"
  tag: "vX.Y.Z-<shortsha>"
  os: linux
  arch: amd64

secret:
  name: "kbot"
  tokenKey: "token"
  tokenName: "TELE_TOKEN"
```

The deployment template constructs the image as:
```
{{ .Values.image.registry }}/{{ .Values.image.repository }}:{{ .Values.image.tag }}-{{ .Values.image.os }}-{{ .Values.image.arch }}
```

---

## Workflow Diagram

```mermaid
flowchart LR
  A[Developer push to <code>develop</code>] --> B[GitHub Actions CI/CD]

  subgraph CI[CI on GitHub Actions]
    B1[Lint & Build]
    B2[Docker build linux/amd64]
    B3[Push image to GHCR]
    B4[Update helm/values.yaml]
    B1 --> B2 --> B3 --> B4
  end
  B --> CI

  CI --> G[Commit back to <code>develop</code>]

  subgraph Git[Git repository]
    G
  end

  G --> H[Argo CD (targetRevision: develop)]
  H --> I[Sync & Deploy to Kubernetes]

  subgraph K8s[Kubernetes cluster]
    I --> J[Deployment kbot]
    J --> K[Pod running]
  end

  K -.-> T[Telegram Bot ready]
```

---

## Running locally

```bash
go mod tidy
export TELE_TOKEN="<YOUR_TELEGRAM_BOT_TOKEN>"
go run main.go kbot
```

---

## Deployment validation

```bash
# check deployed image
kubectl -n kbot get deploy kbot -o jsonpath='{.spec.template.spec.containers[0].image}'; echo

# rollout status
kubectl -n kbot rollout status deploy/kbot

# logs
kubectl -n kbot logs deploy/kbot --tail=200
```

---

## ArgoCD Application (GitOps)

`argocd/application.yaml`:
```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: kbot
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/kors-dev/kbot.git
    targetRevision: develop
    path: helm
  destination:
    server: https://kubernetes.default.svc
    namespace: kbot
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

---

## Common Issues

- **`ImagePullBackOff` from `docker.io/...`**  
  Ensure `helm/templates/deployment.yaml` uses `image.registry: ghcr.io`.

- **`pull access denied` from GHCR**  
  Ensure the package is public or configure `imagePullSecrets`.

- **`secret "kbot" not found`**  
  Create the secret manually with your Telegram token.

- **ArgoCD syncs wrong branch**  
  Confirm `targetRevision: develop` in the Application.

---

## License
MIT
