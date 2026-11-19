@Library('gbif-common-jenkins-pipelines') _

pipeline {
  agent any
  tools {
    maven 'Maven 3.9.9'
    jdk 'OpenJDK17'
  }
  options {
    buildDiscarder(logRotator(numToKeepStr: '5'))
    skipStagesAfterUnstable()
    timestamps()
  }
  triggers {
    snapshotDependencies()
  }
  parameters {
    separator(name: "release_separator", sectionHeader: "Release Parameters")
    booleanParam(name: 'RELEASE', defaultValue: false, description: 'Do a Maven release')
    string(name: 'RELEASE_VERSION', defaultValue: '', description: 'Release version (optional)')
    string(name: 'DEVELOPMENT_VERSION', defaultValue: '', description: 'Development version (optional)')
    booleanParam(name: 'DRY_RUN_RELEASE', defaultValue: false, description: 'Dry Run Maven release')
  }
  stages {
    stage('Setup') {
      steps {
        script {
          // Branch-qualified version, workspace only, never committed.
          // Example: feature/foo -> 1.0.0-FEATURE-FOO-SNAPSHOT
          if (env.BRANCH_NAME != 'dev' && env.BRANCH_NAME != 'main' && !params.RELEASE) {
            def suffix = env.BRANCH_NAME.replaceAll('[^a-zA-Z0-9.-]', '-').toUpperCase()
            sh """
              BASE_VERSION=\$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout | sed 's/-SNAPSHOT//')
              mvn versions:set -DnewVersion=\${BASE_VERSION}-${suffix}-SNAPSHOT -DgenerateBackupPoms=false
            """
          }
        }
      }
    }

    stage('Maven build') {
      when {
        not { expression { params.RELEASE } }
      }
      steps {
        withMaven(globalMavenSettingsConfig: 'org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig1387378707709',
                  mavenSettingsConfig: 'org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig1396361652540',
                  traceability: true) {
          sh 'mvn -B clean verify install deploy -U'
        }
      }
    }

    stage('Maven release') {
      when {
        allOf {
          expression { params.RELEASE };
          branch 'main';
        }
      }
      environment {
        RELEASE_ARGS = utils.createReleaseArgs(params.RELEASE_VERSION, params.DEVELOPMENT_VERSION, params.DRY_RUN_RELEASE)
      }
      steps {
        withMaven(globalMavenSettingsConfig: 'org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig1387378707709',
                  mavenSettingsConfig: 'org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig1396361652540',
                  traceability: true) {
          git branch: 'main', url: 'https://github.com/gbif/gbif-eml-validation.git'
          sh 'mvn -B -Dresume=false release:prepare release:perform -U $RELEASE_ARGS'
        }
      }
    }
  }
}
