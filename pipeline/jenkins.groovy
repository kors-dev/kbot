pipeline {
  agent { label 'built-in' }
  options { timestamps(); ansiColor('xterm') }

  parameters {
    string(name: 'BRANCH',       defaultValue: 'develop',   description: 'Git branch to build')
    string(name: 'APP_VERSION',  defaultValue: 'v1.0.0',    description: 'Base app version (fallback if Chart.yaml has none)')
    booleanParam(name: 'RUN_TESTS',   defaultValue: false,  description: 'Run go tests')
    booleanParam(name: 'DO_PUSH',     defaultValue: true,   description: 'Push image to registry')
    booleanParam(name: 'HELM_BUMP',   defaultValue: true,   description: 'Update helm/values.yaml and push')
    string(name: 'REGISTRY',     defaultValue: 'ghcr.io',   description: 'Container registry')
    string(name: 'IMAGE_REPO',   defaultValue: 'kors-dev/kbot', description: 'Repository in registry')
    string(name: 'TARGETOS',     defaultValue: 'linux',     description: 'Target OS')
    string(name: 'TARGETARCH',   defaultValue: 'amd64',     description: 'Target arch')
  }

  environment {
    CGO_ENABLED     = '0'
    GIT_CREDENTIALS = 'github-pat'   // <-- заміни на свій ID
    REG_CREDENTIALS = 'github-pat'     // <-- заміни на свій ID
  }

  stages {

    stage('Checkout') {
      steps {
        checkout([$class: 'GitSCM',
          branches: [[name: "*/${params.BRANCH}"]],
          userRemoteConfigs: [[url: 'https://github.com/kors-dev/kbot.git', credentialsId: env.GIT_CREDENTIALS]]
        ])
      }
    }

    stage('Derive versions') {
      steps {
        script {
          def appVersion = sh(script: "grep -E '^appVersion:' helm/Chart.yaml | awk '{print \$2}' | tr -d '\"' || true", returnStdout: true).trim()
          if (!appVersion) appVersion = params.APP_VERSION
          def shortSha = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
          env.IMAGE_TAG = "${appVersion}-${shortSha}"
          env.FULL_TAG  = "${env.IMAGE_TAG}-${params.TARGETOS}-${params.TARGETARCH}"
          echo "Using tag: ${params.REGISTRY}/${params.IMAGE_REPO}:${env.FULL_TAG}"
        }
      }
    }

    stage('Build (Go)') {
      steps {
        sh "go version || true"
        sh "make build TARGETOS=${params.TARGETOS} TARGETARCH=${params.TARGETARCH}"
        script { if (params.RUN_TESTS) { sh "make test" } }
      }
    }

    stage('Docker build') {
      steps {
        sh """
          docker build \
            --platform ${params.TARGETOS}/${params.TARGETARCH} \
            -t ${params.REGISTRY}/${params.IMAGE_REPO}:${env.FULL_TAG} \
            --build-arg APP_VERSION=${env.IMAGE_TAG} .
        """
      }
    }

    stage('Docker push') {
      when { expression { params.DO_PUSH } }
      steps {
        withCredentials([usernamePassword(credentialsId: env.REG_CREDENTIALS, usernameVariable: 'CR_USER', passwordVariable: 'CR_PAT')]) {
          sh """
            echo "\$CR_PAT" | docker login ${params.REGISTRY} -u "\$CR_USER" --password-stdin
            docker push ${params.REGISTRY}/${params.IMAGE_REPO}:${env.FULL_TAG}
          """
        }
      }
    }

    stage('Helm values bump & push') {
      when { expression { params.HELM_BUMP } }
      steps {
        sh """
          # update helm/values.yaml via sed (без залежності від yq)
          sed -i 's#^\\(\\s*registry:\\s*\\).*#\\1\"${params.REGISTRY}\"#' helm/values.yaml || true
          sed -i 's#^\\(\\s*repository:\\s*\\).*#\\1\"${params.IMAGE_REPO}\"#' helm/values.yaml || true
          sed -i 's#^\\(\\s*tag:\\s*\\).*#\\1\"${env.IMAGE_TAG}\"#' helm/values.yaml || true
          sed -i 's#^\\(\\s*os:\\s*\\).*#\\1${params.TARGETOS}#' helm/values.yaml || true
          sed -i 's#^\\(\\s*arch:\\s*\\).*#\\1${params.TARGETARCH}#' helm/values.yaml || true
        """
        withCredentials([usernamePassword(credentialsId: env.GIT_CREDENTIALS, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
          sh """
            git config user.email "jenkins@ci.local"
            git config user.name "Jenkins"
            git add helm/values.yaml
            git commit -m "ci(jenkins): bump image to ${env.FULL_TAG}" || true
            git push https://\${GIT_USER}:\${GIT_PASS}@github.com/kors-dev/kbot.git HEAD:${params.BRANCH}
          """
        }
      }
    }
  }

  post {
    always {
      sh 'docker logout ${params.REGISTRY} || true'
      cleanWs()
    }
  }
}
