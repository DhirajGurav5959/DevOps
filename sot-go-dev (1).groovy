/*
    This script is the pipeline for the developers to 
    build server binaries for the code of any feature
    branch branch they are working on.

    They'd just need to send the branch name as a param
    for this pipeline, and it'll build and send them the 
    server binaries in email.

    Note : This pipeline performs sonarqube analysis, but 
    does not fail the build if quality gate is failure.
*/

import groovy.json.JsonSlurperClassic

boolean checkSonarqubeStatus(String projectKey){
    sleep 15
    sh "curl -u admin:admin -X GET -H 'Accept: application/json' https://code.perennialsys.com/sonar/api/qualitygates/project_status\\?projectKey\\="+projectKey+" > status.json"
    def json = readJSON file:'status.json'
    echo "${json.projectStatus.status}"
    if ("${json.projectStatus.status}" == "ERROR") {
        print "SonarQube quality gate status of the project "+projectKey+" is failing."
        return false
    }
    else
    {
        print projectKey+" passed the sonarqube quality gate."
        return true
    }
}
def imageTag = ""

//GitBranch = ${branch}
//EmailIDs = ${email}

node {
    stage('SCM') {
        deleteDir()
        git branch: '${branch}', credentialsId: 'DevOpsBitbucket', url: 'https://bitbucket.org/perennialsys/sot-phase-2-_go.git'
        sh "mv $JENKINS_HOME/workspace/$JOB_NAME/* /tmp/sot/"
        sh "mkdir -p src/bitbucket.org/perennialsys/sot && mv /tmp/sot/* src/bitbucket.org/perennialsys/sot/"
        sh "cd src/bitbucket.org/perennialsys/sot/ && rm Dockerfile"
        sh "cd $JENKINS_HOME/workspace/$JOB_NAME/src/bitbucket.org/perennialsys/sot/ && mkdir binary_artifacts"
        
        sh "mkdir devops && cd devops && git clone git@bitbucket.org:perennialsys/sot-devsops.git"
        sh "mv devops/sot-devsops/go $JENKINS_HOME/workspace/$JOB_NAME/devops/ && mv devops/sot-devsops/deploy . && rm -rf devops/sot-devsops"

        sh "cp $JENKINS_HOME/workspace/$JOB_NAME/devops/go/Dockerfile $JENKINS_HOME/workspace/$JOB_NAME/src/bitbucket.org/perennialsys/sot/Dockerfile"
        sh "cp $JENKINS_HOME/workspace/$JOB_NAME/devops/go/client/client.sh $JENKINS_HOME/workspace/$JOB_NAME/src/bitbucket.org/perennialsys/sot/client.sh"
        sh "cp $JENKINS_HOME/workspace/$JOB_NAME/devops/go/server/server.sh $JENKINS_HOME/workspace/$JOB_NAME/src/bitbucket.org/perennialsys/sot/server.sh"
        sh "cp $JENKINS_HOME/workspace/$JOB_NAME/devops/go/sonar-project.properties $JENKINS_HOME/workspace/$JOB_NAME/src/bitbucket.org/perennialsys/sot/"
        sh "cd $JENKINS_HOME/workspace/$JOB_NAME/src/bitbucket.org/perennialsys/sot/ && chmod +x compile_client.sh"
    }
    
    stage('SonarQube Analysis') {
        sh "cd $JENKINS_HOME/workspace/$JOB_NAME/src/bitbucket.org/perennialsys/sot/ && docker container run --rm -v /home/ubuntu/SonarScannerProperties/sonar-scanner.properties:/root/sonar-scanner/conf/sonar-scanner.properties --volumes-from jenkinsci ajitemsahasrabuddhe/sonar-scanner:13 -Dsonar.projectBaseDir=$JENKINS_HOME/workspace/SOT-Go/src/bitbucket.org/perennialsys/sot/"
    }
    
    if(checkSonarqubeStatus("sot-go")){
        
        stage('Build Generator Image'){
            try{
                sh "cd $JENKINS_HOME/workspace/$JOB_NAME/src/bitbucket.org/perennialsys/sot && docker build -t sot/sot-generator:latest -f Dockerfile ."
            }
            catch(Error){
                currentBuild.result = 'FAILURE'
                print "Error in building Generator Image"
            }
        }
        stage('Build Server Binaries'){
            if(currentBuild.result != 'FAILURE'){
                try{
                    sh "cd $JENKINS_HOME/workspace/$JOB_NAME/src/bitbucket.org/perennialsys/sot && chmod +x server.sh && ./server.sh"
                }
                catch(Error){
                    currentBuild.result = 'FAILURE'
                    print "Error in extracting server binaries"
                }
            }
            else{
                print "Extracting server binaries skipped due to failure in previous stages."
            }
        }

        stage('Build Client Binaries'){
            if(currentBuild.result != 'FAILURE'){
                try{
                    sh "cd $JENKINS_HOME/workspace/$JOB_NAME/src/bitbucket.org/perennialsys/sot && chmod +x client.sh && ./client.sh"
                }
                catch(Error){
                    print "Client Binaries not found"
                    currentBuild.result = 'FAILURE'
                }
            }
            else{
                print "Extracting Client Binaries skipped due to failure in previous stages."
            }
        }
        stage('Archive Binaries'){
            try{
                    archiveArtifacts 'src/bitbucket.org/perennialsys/sot/binary_artifacts/*/*'
                }
                catch(Error){
                    print "Error archiving binaries"
                    currentBuild.result = 'FAILURE'
                }
        }
    }
    else{
        print "Failure in Sonarqube Quality Gate."
    }
    
   stage('Notify'){
        //def mailRecipients = "husain.mithaiwala@perennialsys.com, soni.raju@perennialsys.com, babulal.choudhary@perennialsys.com, mohasin.kazi@perennialsys.com, santosh.thakur@perennialsys.com, sanket.kedar@perennialsys.com, ajit.khot@perennialsys.com"
        def mailRecipients = "${email}"
        def jobName = currentBuild.fullDisplayName
            
        if(currentBuild.result == 'FAILURE'){
            emailext body: '''${JELLY_SCRIPT, template="email.jelly"}
&nbsp;<h2><b> SONARQUBE ANALYSIS </b></h2><br>
&nbsp;<a href="https://code.perennialsys.com/sonar/dashboard?id=sot-go" >View SONARQUBE Analysis Reports from here.</a>
<br><br>''',
            mimeType: 'text/html',
            subject: "[PS-Jenkins] ${jobName} Failure",
            to: "${mailRecipients}",
            replyTo: "husain.mithaiwala@perennialsys.com",
            recipientProviders: [[$class: 'CulpritsRecipientProvider']]
        }
        else{
            emailext body: '''${JELLY_SCRIPT, template="email.jelly"}
&nbsp;<h2><b> SONARQUBE ANALYSIS </b></h2><br>
&nbsp;<a href="https://code.perennialsys.com/sonar/dashboard?id=sot-go" >View SONARQUBE Analysis Reports from here.</a>
<br><br>''',
            mimeType: 'text/html',
            subject: "[PS-Jenkins] ${jobName} Success",
            to: "${mailRecipients}",
            replyTo: "husain.mithaiwala@perennialsys.com",
            recipientProviders: [[$class: 'CulpritsRecipientProvider']]
        }
    }
}
