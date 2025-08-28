pipeline {
  agent any

  options {
    timestamps()
    ansiColor('xterm')

  }

  parameters {
    choice(name: 'OS',   choices: ['linux', 'apple', 'windows'], description: 'Pick OS')
    choice(name: 'ARCH', choices: ['amd64', 'arm64'],            description: 'Pick ARCH')
  }

  environment {
    GITHUB_TOKEN = credentials('github') // Username with password (GitHub + PAT)
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
        sh 'set -euo pipefail; make test'
      }
    }

    stage('Build') {
      steps {
        script {
          
          def goos = (params.OS == 'apple') ? 'darwin' : params.OS
          echo "Building binary for platform ${goos}/${params.ARCH}"
          sh "set -euo pipefail; make build TARGETOS=${goos} TARGETARCH=${params.ARCH}"
        }
      }
    }

    stage('Image') {
      steps {
        script {
          def goos = (params.OS == 'apple') ? 'darwin' : params.OS
          sh "set -euo pipefail; make image TARGETOS=${goos} TARGETARCH=${params.ARCH}"
        }
      }
    }

    stage('Login to GHCR') {
      steps {
        // credentials уже в env: GITHUB_TOKEN_USR / GITHUB_TOKEN_PSW
        sh 'set -euo pipefail; echo "$GITHUB_TOKEN_PSW" | docker login ghcr.io -u "$GITHUB_TOKEN_USR" --password-stdin'
      }
    }

    stage('Push image') {
      steps {
        script {
          def goos = (params.OS == 'apple') ? 'darwin' : params.OS
          sh "set -euo pipefail; make push TARGETOS=${goos} TARGETARCH=${params.ARCH}"
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
