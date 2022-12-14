pipeline
{
	agent { label 'master' }
	environment
	{
		// variables start 
		// DEV_CLONE_URL = "https://github.com/hyrglobalsource/optjobs-ui.git"
		// DEPLOY_ENV = "staging"
		// DEV_BRANCH = "stage"
		// APP_NAME = "optjobs-frontend"
		// variables end
		DEPLOY_ENV = "staging"
		DEV_DIR = "$WORKSPACE" + "/devCode"
		DEVOPS_DIR = "$WORKSPACE" + "/devopsCode"
		SKIP_TLS = true
		DOCKER_REGISTRY_PATH = "http://ec2-18-144-27-149.us-west-1.compute.amazonaws.com"
		DOCKER_IMAGE_PREFIX = "ec2-18-144-27-149.us-west-1.compute.amazonaws.com/optjobs/"
		DOCKER_FILE_PATH = "$DEVOPS_DIR"+"/docker-files/${DEPLOY_ENV}/"+"$APP_NAME"+"/Dockerfile"
		DOCKER_REGISTRY = "$DOCKER_IMAGE_PREFIX"+"$APP_NAME"+":"+"latest-"+"$DEPLOY_ENV"
		def DEV_CLONE_URL = ""
		BUILT_DOCKER_IMAGE = ''
		def APP_PORT = null
		def deployment_app_name = null
		def BACKUP_TAG = null
		def harbor_image_url = "ec2-18-144-27-149.us-west-1.compute.amazonaws.com/optjobs/${APP_NAME}"
		def current_running_tag = "latest-"+"$DEPLOY_ENV"


	}
	options
	{
		timeout(time:2, unit:'HOURS')
		timestamps()
	}
	stages
	{
		stage('INITIATE')
		{
			steps
			{
				script
				{
					stage('Setup Params')
					{
						// these lines needs to be reopened...

						if (DEV_BRANCH == "stage" || DEV_BRANCH == 'secrets-keys-integration'){
							DEPLOY_ENV = 'staging'

						}
						else if(DEV_BRANCH == "master" || DEV_BRANCH == 'prod-migration' || DEV_BRANCH == 'master-to-deploy'){
							DEPLOY_ENV = 'prod'
						}
						DOCKER_FILE_PATH = "$DEVOPS_DIR"+"/docker-files/${DEPLOY_ENV}/"+"$APP_NAME"+"/Dockerfile"
						DOCKER_REGISTRY = "$DOCKER_IMAGE_PREFIX"+"$APP_NAME"+":"+"latest-"+"$DEPLOY_ENV"
						BACKUP_TAG = "${DEPLOY_ENV}_BACKUP"
 						if(APP_NAME == "optjobs_frontend")
						{
							DEV_CLONE_URL = "https://github.com/hyrglobalsource/optjobs-ui.git"
							if ( DEPLOY_ENV == 'staging' )
							{
								APP_PORT = 3001
							}
							else
							{
								APP_PORT = 3000
							}
							
						}
						else if (APP_NAME == "optjobs_backend")
						{
							DEV_CLONE_URL = "https://github.com/hyrglobalsource/optjobs.git"
							APP_PORT = 4546
						}
						if ( DEPLOY_ENV == 'staging')
						{
							deployment_app_name = "${APP_NAME}_staging"
						}
						else
						{
							deployment_app_name = "${APP_NAME}_prod"
						}

					}
					stage('CHECKOUT DEVOPS CODE')
					{
						dir(DEVOPS_DIR)
						{
							checkout scm
						}

					}
					stage('CHECKOUT DEV CODE')
					{
						dir(DEV_DIR)
						{
							println('Cloning.....')
							println(DEV_CLONE_URL)
							checkout([$class: 'GitSCM', branches: [[name: DEV_BRANCH]], extensions: [], userRemoteConfigs: [[credentialsId: 'github-user-token', url: DEV_CLONE_URL]]])
							sh "cp $DOCKER_FILE_PATH ."
						}

					}
				}
			}
		}
		stage('DOCKER PROCESSING')
		{
			steps
			{
				script
				{
					stage('IMAGE BUILD')
					{
						dir(DEV_DIR)
						{
							
							BUILT_DOCKER_IMAGE = docker.build DOCKER_REGISTRY

						}

					}
					stage('IMAGE PUSH')
					{
						dir(DEV_DIR)
						{
							docker.withRegistry(DOCKER_REGISTRY_PATH,'harbor_creds')
							{
								BUILT_DOCKER_IMAGE.push()


							}
						}
					}
					stage('Deploy application')
					{
						ansiblePlaybook become: true, credentialsId: 'ubuntu-private', disableHostKeyChecking: true, extras: "-e \"host=$deployment_app_name back_up_tag=$BACKUP_TAG harbor_image_url=$harbor_image_url current_running_tag=$current_running_tag docker_image=$DOCKER_REGISTRY app_name=$APP_NAME app_port=$APP_PORT\"", installation: 'ansible-new', inventory: 'inventory', playbook: 'deploy.yaml'
					}
				}
			}
			

		}
		stage('POST DEPLOYMENT FUNCTIONS'){
			steps{
				script{
					stage('Deployment Communication'){
						send_deployment_confirmation()
					}
				}
			}
		}
	}
}


def send_deployment_confirmation(){
	println("Pipleine completed...")
	
}