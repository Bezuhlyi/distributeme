package org.distributeme.generator;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import com.sun.mirror.apt.Filer;
import com.sun.mirror.apt.Filer.Location;
import com.sun.mirror.declaration.TypeDeclaration;

/**
 * Generator for server start script. 
 * @author lrosenberg
 */
public class ServerScriptGenerator extends AbstractGenerator implements Generator{

	@Override
	public void generate(TypeDeclaration type, Filer filer, Map<String,String> options) throws IOException{
		//System.out.println("#####  ServerScriptGenerator: "+getServerName(type)+"  #####");
		PrintWriter writer = filer.createTextFile(Location.SOURCE_TREE, "", new File("../service/"+ type.getSimpleName() +"/" + type.getSimpleName() + "-server.sh"), "UTF-8");
		setWriter(writer);
		
		writeString("#!/bin/bash");
		emptyline();
		if(options.get("includescript") != null){
			writeString("source " + options.get("includescript"));
			emptyline();
		}
		writeString("#jpdaOpts=\"-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=localhost:8101\"");
		emptyline();		
		writeString("#release_path=<absolute_path_to_release>");
		writeString("#dist=<dist_jar>");
		writeString("#lib=<lib_folder>");
		writeString("#etc=<etc_folder>");
		writeString("#initialMemory=<initial_memory_size>");
		writeString("#maxMemory=<max_memory_size>");
		writeString("#configuremeEnvironment=<configureme_environment>");
		writeString("#user=<restricted_user_name>");
		writeString("#outlog=<nohup_stdout_log_file_name>");
		writeString("#errlog=<nohup_stdout_log_file_name>");
		writeString("#logdir=<log_directory>");
		writeString("#tmpdir=<temporary_files_directory>");
		writeString("#pidfile=<log_files_directory>");
		emptyline();		 
		writeString("if [ -z \"$release_path\" ]; then");
		increaseIdent();
		writeString("echo 'release_path environment is not set!'");
		writeString("exit 1");
		decreaseIdent();
		writeString("fi");
		emptyline();
		writeString("if [ -z \"$dist\" ]; then");
		increaseIdent();
		writeString("echo 'dist environment is not set!'");
		writeString("exit 1");
		decreaseIdent();
		writeString("fi");
		emptyline();
		
		writeString("dist=$release_path/$dist");
		writeString("lib=$release_path/${lib:=lib}");
		writeString("etc=$release_path/${etc:=etc}");
		writeString("initialMemory=${initialMemory:=512M}");
		writeString("maxMemory=${maxMemory:=512M}");
		writeString("configuremeEnvironment=${configuremeEnvironment:=dev}");
		writeString("user=${user:=$USER}");
		writeString("logdir=$release_path/${logdir:=.}");
		writeString("outlog=${outlog:=$logdir/nohup-" + getServerName(type) + "-out.log}");
		writeString("errlog=${errlog:=$logdir/nohup-" + getServerName(type) + "-err.log}");
		writeString("tmpdir=$release_path/${tmpdir:=/tmp}");
		writeString("pidfile=${pidfile:=$tmpdir/"+getServerName(type)+".pid}");
		
		emptyline();
		writeString("server='"+getPackageName(type) + "." + getServerName(type) +"'");
		emptyline();
		
		writeString("isServerUp() {");
		increaseIdent();
		writeString("if [ -f \"$pidfile\" ]; then");
		increaseIdent();
		writeString("pid=`cat \"$pidfile\"`");
		writeString("alive=`ps --no-heading $pid 2>/dev/null | wc -l`");
		writeString("if [ $alive == 0 ]; then");
		increaseIdent();
		writeString("rm -f \"$pidfile\"");
		writeString("return 0");
		decreaseIdent();
		writeString("else");
		writeIncreasedString("return 1");
		writeString("fi");
		decreaseIdent();
		writeString("else");
		writeIncreasedString("return 0");
		writeString("fi");
		decreaseIdent();
		writeString("}");
		
		//start method
		emptyline();
		writeString("start() {");
		increaseIdent();
		writeString("isServerUp");
		writeString("running=$?");
		writeString("if [ $running == 0 ]; then");
		increaseIdent();
		writeString("echo -n 'Starting "+getServerName(type)+"... '");
		writeString("for file in $(ls $lib); do");
		increaseIdent();
		writeString("CLASSPATH=$CLASSPATH:$lib/$file");
		writeString("rmicodebase=\"$rmicodebase file:$lib/$file\"");
		decreaseIdent();
		writeString("done");
		writeString("CLASSPATH=$etc:$dist:$CLASSPATH");
		writeString("rmicodebase=\"file:$etc file:$dist $rmicodebase\"");
		writeString("work_dir=`pwd`");
		writeString("cd \"$release_path\"");
		writeString("sudo -u \"$user\" /bin/bash -c \"nohup java $jpdaOpts -Xmx$maxMemory -Xms$initialMemory -classpath $CLASSPATH -Djava.rmi.server.codebase=\\\"$rmicodebase\\\" -Dpidfile=\\\"$pidfile\\\" -Dconfigureme.defaultEnvironment=$configuremeEnvironment $server 1>>\\\"$outlog\\\" 2>>\\\"$errlog\\\" &\"");
		writeString("cd \"$work_dir\"");
		writeString("#Wait for possible starting error");
		writeString("sleep 2");
		writeString("isServerUp");
		writeString("running=$?");
		writeString("if [ $running == 1 ]; then");
		writeIncreasedString("echo Done");
		writeString("else");
		writeIncreasedString("echo Failed");
		writeString("fi");
		decreaseIdent();
		writeString("else");
		writeIncreasedString("echo "+getServerName(type)+" is already running");
		writeString("fi");
		decreaseIdent();
		writeString("}");
		
		//stop method
		emptyline();
		writeString("stop() {");
		increaseIdent();
		writeString("isServerUp");
		writeString("running=$?");
		writeString("if [ $running == 0 ]; then");
		writeIncreasedString("echo '"+getServerName(type)+" is not running'");
		writeString("else");
		increaseIdent();
		writeString("echo -n 'Stopping "+getServerName(type)+"... '");
		writeString("pid=`cat \"$pidfile\"`");
		writeString("kill $pid");
		writeString("sleep 2");
		writeString("isServerUp");
		writeString("running=$?");
		writeString("if [ $running == 0 ]; then");
		increaseIdent();
		writeString("echo Done");
		writeString("rm -f \"$pidfile\"");
		decreaseIdent();
		writeString("else");
		writeIncreasedString("echo Failed");
		writeString("fi");
		decreaseIdent();
		writeString("fi");
		decreaseIdent();
		writeString("}");
		
		//status method
		emptyline();
		writeString("status() {");
		increaseIdent();
		writeString("isServerUp");
		writeString("running=$?");
		writeString("if [ $running == 1 ]; then");
		writeIncreasedString("echo '"+getServerName(type)+" is running'");
		writeString("else");
		writeIncreasedString("echo '"+getServerName(type)+" is not running'");
		writeString("fi");
		decreaseIdent();
		writeString("}");
		
		//main selector
		emptyline();
		writeString("case $1 in");
		increaseIdent();
		writeString("start)");
		increaseIdent();
		writeString("start");
		writeString(";;");
		decreaseIdent();
		writeString("stop)");
		increaseIdent();
		writeString("stop");
		writeString(";;");
		decreaseIdent();
		writeString("restart)");
		increaseIdent();
		writeString("stop");
		writeString("sleep 1");
		writeString("start");
		writeString(";;");
		decreaseIdent();
		writeString("status)");
		increaseIdent();
		writeString("status");
		writeString(";;");
		decreaseIdent();
		writeString("*)");
		writeString("echo $\"Usage: $0 {start|stop|restart|status}\"");
		writeString("exit 1");
		decreaseIdent();
		writeString("esac");
		
		emptyline();
		writeString("exit 0");
		
		writer.flush();
		writer.close();
	}

}
