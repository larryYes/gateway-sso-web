/*
    语法文档：https://www.w3cschool.cn/jenkins/jenkins-qc8a28op.html
*/

/*
=======================================================================================
                                 以下变量需要自行配置
=======================================================================================
*/

//设定harbor地址
def harbor = 'ccr.ccs.tencentyun.com'

//设定harbor中的命名空间
def harbor_project_name = 'beiming'

//指定maven配置文件
def mavenSetting = 'settings-sm-nexus.xml'


//docker credentialsId 用于登录harbor
def docker_credentialsId = 'wenlvama-af221134-feab-4783-9991-dadccc1667ce'

//rancher credentialsId 用于登录rancher
/*def rancher_credentialsId = 'xiaocpa-055f89eb-5c40-415a-a642-73764e294534'
def namespace = 'c-wsxm5'
def projectId = 'p-w8x5h'*/

/*
=======================================================================================
                                 以下变量自动获取
=======================================================================================
*/

//项目名
def project_name
//版本号
def version
//镜像名
def image_name
//tag
def tag
//rancer context
//def context
//harbor地址
def harbor_url

/*
=======================================================================================
                                 以下是工作流脚本
=======================================================================================
*/

podTemplate(inheritFrom: 'pod1',
        containers:[
                //基础容器 不可删除
                containerTemplate(name:'jnlp',image:'harbor.dev.wh.digitalchina.com/devops/jenkins-slave-dev:latest')
                //添加需要的镜像容器
                ,containerTemplate(name:'maven',image:'harbor.dev.wh.digitalchina.com/library/maven:3-jdk-8-whca-repsitory',ttyEnabled:true,command:'cat')

        ],
        volumes:[
                hostPathVolume(hostPath:'/var/run/docker.sock',mountPath:'/var/run/docker.sock')
        ]
){
    node(POD_LABEL){
        properties([gitLabConnection('newgitlab.digitalchina.com')])
        if(BRANCH_NAME == 'test'){
            gitlabBuilds(builds: ['Checkout','Package','Prepare','Push Image']){
                stage 'Checkout'
                gitlabCommitStatus('Checkout'){
                    checkout scm
                }

                stage 'Package'
                gitlabCommitStatus('Package'){
                    container('maven'){
                        sh 'mvn clean package install -Dmaven.test.skip=true --settings ' + mavenSetting
                    }
                }

                stage 'Prepare'
                gitlabCommitStatus('Prepare'){
                    sh label: '获取jar包名和版本号', script: '''
                    cd $WORKSPACE
                    cd target
                    rm -rf *jar.original
                    rm -rf *-javadoc.jar
                    rm -rf *-sources.jar
 
                    jar_name=`ls | grep jar$ | awk \'{print $1}\'`
                    jar=${jar_name##*web-}

                    project_name=gateway-sso-web
                    version=${jar%.*}
 
                    cd $WORKSPACE
                    touch project_nameFile
                    touch versionFile
 
                    echo -n "$project_name" > project_nameFile
                    echo -n "$version" > versionFile
                    '''

                    //获取jar包名
                    project_name = readFile 'project_nameFile'
                    echo "project_name: " + project_name

                    //获取版本号
                    version = readFile 'versionFile'
                    echo "version: " + version

                    //加载镜像名
                    image_name = project_name + ':' + version
                    echo "image_name: " + image_name

                    //加载tag
                    tag = harbor + "/" + harbor_project_name + "/"+ image_name
                    echo "tag: " + tag

                }


                stage 'Push Image'
                gitlabCommitStatus('Push Image'){
                    def IMAGE = docker.build(tag,'.')
                    harbor_url = "https://" + harbor
                    withDockerRegistry(credentialsId: docker_credentialsId,url: harbor_url){
                        IMAGE.push()
                        sh 'docker rmi ' + tag
                    }
                }

                /*stage 'Deploy'
                //input(message:"部署到Rancher?", ok:"确定", submitter:"your itcode")
                gitlabCommitStatus('Deploy'){
                    withCredentials([string(credentialsId: rancher_credentialsId, variable: 'token')]) {
                        //根据代码库中的deploy.yaml文件部署镜像
                        context = namespace + ":" + projectId

                        echo "context: " + context
                        echo "image_name: " + image_name
                        sh 'export PROJECT_IMAGE='+image_name + """
                            env
                            rancher login https://rancher.wh.digitalchina.com/ --token $token --skip-verify --context $context
                            envsubst < Deploy.yaml | rancher kubectl apply -f -
                        """
                    }
                }*/
            }
        }
    }
}
