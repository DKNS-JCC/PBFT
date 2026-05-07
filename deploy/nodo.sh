#!/bin/bash
TOMCAT=/apache-tomcat-9.0.117
WAR=$(dirname "$0")/PBFT.war

$TOMCAT/bin/shutdown.sh 2>/dev/null; sleep 2
rm -rf $TOMCAT/webapps/PBFT $TOMCAT/webapps/PBFT.war
cp "$WAR" $TOMCAT/webapps/
$TOMCAT/bin/startup.sh