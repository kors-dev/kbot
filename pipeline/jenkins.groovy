pipeline {
  agent any

  options {
    timestamps()
    ansiColor('xterm')
    skipDefaultCheckout(true) // щоб не було дубльованого checkout
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
  }

  post {
    always {
      sh 'docker logout || true'
    }
  }
}
