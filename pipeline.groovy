pipeline {
    agent any
    environment {
        bundle_name = "${sh(returnStdout: true, script: 'echo "bundle_`date +%Y-%m-%d_%H-%m-%S`"').trim()}"
    }
    stages {
        stage('PREPARE'){
            steps {
                sh 'echo ${bundle_name}'
                git credentialsId: "git_hub_ssh", url: "git@github.com:fsergot/dss_pipeline.git"
                sh "sed -i 's|@DESIGN_URL@|${DESIGN_URL}|' requirements.txt"
                sh "cat requirements.txt"
                sh "printenv"
            }
        }
        stage('PROJECT_VALIDATION') {
            steps {
                withPythonEnv('python3') {
                    sh "pip install -U pip"
                    sh "pip install -r requirements.txt"
                    sh "pytest -s 1_project_validation/run_test.py -o junit_family=xunit1 --host='${DESIGN_URL}' --api='${DESIGN_API_KEY}' --project='${DSS_PROJECT}' --junitxml=reports/PROJECT_VALIDATION.xml"
                }
            }
        }
        stage('PACKAGE_BUNDLE') {
            steps {
                withPythonEnv('python3') {
                    sh "pip install -U pip"
                    sh "pip install -r requirements.txt"
                    sh "python 2_package_bundle/run_bundling.py '${DESIGN_URL}' '${DESIGN_API_KEY}' '${DSS_PROJECT}' ${bundle_name}"
                }
                sh "echo DSS project bundle created and downloaded in local workspace"
                sh "ls -la"
                script {
                    def server = Artifactory.server 'artifactory'
                    def uploadSpec = """{
                        "files": [{
                          "pattern": "*.zip",
                          "target": "generic-local/dss_bundle/"
                        }]
                    }"""
                    def buildInfo = server.upload spec: uploadSpec, failNoOp: true
                }
            }
        }
        stage('PREPROD_TEST') {
            steps {
                withPythonEnv('python3') {
                    sh "pip install -U pip"
                    sh "pip install -r requirements.txt"
                    sh "python 3_preprod_test/import_bundle.py ${AUTO_UAT_URL} ${AUTO_UAT_API_KEY} ${DSS_PROJECT} ${bundle_name}"
                    sh "pytest -s 3_preprod_test/run_test.py -o junit_family=xunit1 --host='${AUTO_UAT_URL}' --api='${AUTO_UAT_API_KEY}' --project='${DSS_PROJECT}' --junitxml=reports/PREPROD_TEST.xml"
                    
                }                
            }
        }
        stage('DEPLOY_TO_PROD') {
            steps {
                withPythonEnv('python3') {
                    sh "pip install -U pip"
                    sh "pip install -r requirements.txt"
                    sh "python 4_deploy_prod/import_bundle.py ${AUTO_PROD_URL} ${AUTO_PROD_API_KEY} ${DSS_PROJECT} ${bundle_name}"
                    sh "python 4_deploy_prod/prod_activation.py ${AUTO_PROD_URL} ${AUTO_PROD_API_KEY} ${DSS_PROJECT} ${bundle_name}"
                    
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
