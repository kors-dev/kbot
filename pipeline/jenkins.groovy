pipeline {
  agent any

  options {
    timestamps()
    ansiColor('xterm')
    skipDefaultCheckout(true) 
  }

  parameters {
    choice(name: 'OS',   choices: ['linux', 'apple', 'windows'], description: 'Pick OS')
    choice(name: 'ARCH', choices: ['amd64', 'arm64'],            description: 'Pick ARCH')
  }

  environment {
    // Jenkins credentials: Username with password (GitHub login + PAT з write:packages)
    GITHUB_TOKEN = credentials('github')
    REPO   = 'https://github.com/kors-dev/kbot.git'
    BRANCH = 'develop'
  }

  stages {

    stage('Checkout') {
      steps {
        echo 'Clone Repository'
        git branch: "${BRANCH}", url: "${REPO}"
      }
    }

    stage('Test') {
      steps {
        echo 'Testing started'
        sh "make test"
      }
    }

    stage('Build') {
      steps {
        script {
          def goos = (params.OS == 'apple') ? 'darwin' : params.OS
          echo "Building binary for platform ${goos} on ${params.ARCH} started"
          sh "make build TARGETOS=${goos} TARGETARCH=${params.ARCH}"
        }
      }
    }

    stage('Image') {
      steps {
        script {
          def goos = (params.OS == 'apple') ? 'darwin' : params.OS
          echo "Building image for platform ${goos} on ${params.ARCH} started"
          sh "make image TARGETOS=${goos} TARGETARCH=${params.ARCH}"
        }
      }
    }

    stage('Login to GHCR') {
      steps {
        sh 'echo "$GITHUB_TOKEN_PSW" | docker login ghcr.io -u "$GITHUB_TOKEN_USR" --password-stdin'
      }
    }

    stage('Push image') {
      steps {
        script {
          def goos = (params.OS == 'apple') ? 'darwin' : params.OS
          sh "make push TARGETOS=${goos} TARGETARCH=${params.ARCH}"
        }
      }
    }

    // ⬇️ New stage: updating helm/values.yaml and committing with [skip ci]
    stage('Bump Helm tag (skip GH Actions)') {
      steps {
        script {
          def goos = (params.OS == 'apple') ? 'darwin' : params.OS
          sh """
            set -euo pipefail

            # 1) Get the build tag
            # If there is a make target print-image-tag — use it; otherwise — fallback.
            NEW_TAG=\$(make -s print-image-tag TARGETOS='${goos}' TARGETARCH='${params.ARCH}' || true)
            if [ -z "\$NEW_TAG" ]; then
              APP_VERSION=\$(git describe --tags --always --dirty)
              NEW_TAG="\${APP_VERSION}-${params.ARCH}"
            fi
            echo "Resolved NEW_TAG=\$NEW_TAG"

            # 2) Update .image.tag in helm/values.yaml
            if command -v yq >/dev/null 2>&1; then
              yq -i ".image.tag = \\"\${NEW_TAG}\\"" helm/values.yaml
            else
              sed -i -E "s|(^[[:space:]]*tag:[[:space:]]*).*|\\1\\\"\${NEW_TAG}\\\"|" helm/values.yaml
            fi

            echo "Updated helm/values.yaml with tag: \$NEW_TAG"
            git status --porcelain

            # 3) Commit with skip for GitHub Actions
            git config --global --add safe.directory "\$PWD"
            git config user.name  "Jenkins CI"
            git config user.email "ci@jenkins"
            git add helm/values.yaml
            git commit -m "chore(helm): bump image tag to \$NEW_TAG [skip ci]" || echo "Nothing to commit"

            # 4) Push back to the same branch (authentication via Jenkins credentials)
            git remote set-url origin "https://${GITHUB_TOKEN_USR}:${GITHUB_TOKEN_PSW}@github.com/kors-dev/kbot.git"
            git push origin HEAD:${BRANCH} || echo "Nothing pushed"
          """
        }
      }
    }
  }

  post {
    always {
      sh 'docker logout || true'
    }
  }
}
