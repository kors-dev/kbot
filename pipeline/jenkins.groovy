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
            sh(script: 'set -euo pipefail; make test', shell: '/bin/bash')
    }
    }
    stage('Build') {
        steps {
            script {
                def goos = (params.OS == 'apple') ? 'darwin' : params.OS
                sh(script: "set -euo pipefail; make build TARGETOS=${goos} TARGETARCH=${params.ARCH}", shell: '/bin/bash')
        }
    }
    }
    stage('Image') {
        steps {
            script {
                def goos = (params.OS == 'apple') ? 'darwin' : params.OS
                sh(script: "set -euo pipefail; make image TARGETOS=${goos} TARGETARCH=${params.ARCH}", shell: '/bin/bash')
        }
    }
    }
    stage('Push image') {
        steps {
            script {
                def goos = (params.OS == 'apple') ? 'darwin' : params.OS
                sh(script: "set -euo pipefail; make push TARGETOS=${goos} TARGETARCH=${params.ARCH}", shell: '/bin/bash')
        }
    }
    }
    post {
        always { sh(script: 'docker logout || true', shell: '/bin/bash') }
    }

}
