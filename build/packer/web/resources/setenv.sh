JAVA_OPTS="-Xms256m -Xmx1024m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/media/rspace/logs-audit"
CATALINA_OPTS="-DpropertyFileDir=file:/etc/rspace/ -DRS_FILE_BASE=/media/rspace/file_store -Djava.awt.headless=true -Dliquibase.context=run -Dspring.profiles.active=prod -Djmelody.dir=/media/rspace/jmelody/"
