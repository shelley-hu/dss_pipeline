pipeline {
    agent any
    environment {
        bundle_name = "${sh(returnStdout: true, script: 'echo "bundle_`date +%Y-%m-%d_%H-%m-%S`"').trim()}"
    }
    stages {
        stage('PREPARE'){
           steps {
                cleanWs()
                sh 'echo ${bundle_name}'
                git credentialsId: "dataiku", url: "git@github.com:shelley-hu/dss_pipeline.git"
                sh "cat requirements.txt"
                withPythonEnv('python3') {
                    sh "pip install -U pip"
                    sh "pip install -r requirements.txt"
                }
           }
        }
        stage('PROJECT_VALIDATION') {
            steps {
                withPythonEnv('python3') {
                    sh "pytest -s 1_project_validation/run_test.py -o junit_family=xunit1 --host='${DESIGN_URL}' --api='${DESIGN_API_KEY}' --project='${DSS_PROJECT}' --junitxml=reports/PROJECT_VALIDATION.xml"
                }
            }
        }
        stage('PACKAGE_BUNDLE') {
            steps {
                withPythonEnv('python3') {
                    sh "python 2_package_bundle/run_bundling.py '${DESIGN_URL}' '${DESIGN_API_KEY}' '${DSS_PROJECT}' ${bundle_name}"
                }
                sh "echo DSS project bundle created and downloaded in local workspace"
                sh "ls -la"
                script {
                    def server = Artifactory.server 'artifactory'
                    def uploadSpec = """{
                        "files": [{
                          "pattern": "*.zip",
                          "target": "test/dss_bundle"
                        }]
                    }"""
                    def buildInfo = server.upload spec: uploadSpec, failNoOp: true
                }
            }
        }
        stage('PREPROD_DEPLOY') {
            steps {
                withPythonEnv('python3') {
                    sh "python 3_preprod_test/import_bundle.py '${AUTO_PREPROD_URL}' '${AUTO_PREPROD_API_KEY}' '${DSS_PROJECT}' ${bundle_name} '${AUTO_PREPROD_ID}'"
                    //sh "pytest -s 3_preprod_test/run_test.py -o junit_family=xunit1 --host='${AUTO_PREPROD_URL}' --api='${AUTO_PREPROD_API_KEY}' --project='${DSS_PROJECT}' --junitxml=reports/PREPROD_TEST.xml"
                }                
            }
        }
    }
    post{
        always {
            fileOperations ([fileDeleteOperation(includes: '*.zip')])
            junit 'reports/**/*.xml'
        }
    }
}
