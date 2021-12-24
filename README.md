# tomcat
tomcat源码分析

本项目主要为分析tomcat源代码，以tomcat-8为基础。

在运行Bootstrap的main方法时，将下面代码拷入到vm options 中

-Dcatalina.home=launch -Dcatalina.base=launch -Djava.endorsed.dirs=launch/endorsed -Djava.io.tmpdir=launch/temp -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager -Djava.util.logging.config.file=launch/conf/logging.properties

运行成功后，访问http://localhost:8080 即可。
