package org.distributeme.generator.ws;

import com.sun.mirror.apt.Filer;
import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.declaration.ParameterDeclaration;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.type.ReferenceType;
import net.anotheria.anoprise.metafactory.Extension;
import net.anotheria.anoprise.metafactory.MetaFactory;
import net.anotheria.moskito.core.dynamic.MoskitoInvokationProxy;
import net.anotheria.moskito.core.logging.DefaultStatsLogger;
import net.anotheria.moskito.core.logging.IntervalStatsLogger;
import net.anotheria.moskito.core.logging.Log4JOutput;
import net.anotheria.moskito.core.predefined.ServiceStatsCallHandler;
import net.anotheria.moskito.core.predefined.ServiceStatsFactory;
import net.anotheria.moskito.core.stats.DefaultIntervals;
import org.distributeme.annotation.WebServiceMe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.Collection;

public class ServiceProxyGenerator extends WSStructureGenerator implements WebServiceMeGenerator {

	public ServiceProxyGenerator(Filer filer) {
		super(filer);
	}

	@Override
	public void generate(TypeDeclaration type) {
		PrintWriter writer = createSourceFile(type.getSimpleName(), getWSProxyPackage(type), getWSProxySimpleName(type));
		setWriter(writer);

		WebServiceMe ann = type.getAnnotation(WebServiceMe.class);

		// package
		writeString("package " + getWSProxyPackage(type) + ";");
		emptyline();
		// imports
		writeImport(type.getPackage().getQualifiedName(), type.getSimpleName());
		writeImport(Logger.class);
		writeImport(LoggerFactory.class);
		if (ann.moskitoSupport()) {
			writeImport(MoskitoInvokationProxy.class);
			writeImport(DefaultStatsLogger.class);
			writeImport(IntervalStatsLogger.class);
			writeImport(Log4JOutput.class);
			writeImport(DefaultIntervals.class);
			writeImport(ServiceStatsCallHandler.class);
			writeImport(ServiceStatsFactory.class);
			writeImport(MetaFactory.class);
			writeImport(Extension.class);
			writeImport("javax.jws.WebService");
		}
		emptyline();
		// class
		writeString("@WebService");
		writeString("public class " + getWSProxySimpleName(type) + " implements " + type.getSimpleName() + " {");
		increaseIdent();
		emptyline();
		// variables
		writeStatement("private static final Logger LOGGER = Logger.getLogger(" + getWSProxySimpleName(type) + ".class)");
		emptyline();
		writeStatement("private " + type.getQualifiedName() + " implementation");
		emptyline();
		// constructor
		writeString("public " + getWSProxySimpleName(type) + "() {");
		increaseIdent();
		writeStatement("init()");
		if (ann.moskitoSupport()) {
			writeString("MoskitoInvokationProxy proxy = new MoskitoInvokationProxy(");
			writeIncreasedString("implementation,");
			writeIncreasedString("new ServiceStatsCallHandler(),");
			writeIncreasedString("new ServiceStatsFactory(),");
			writeIncreasedString(quote(type.getSimpleName()) + ", ");
			writeIncreasedString(quote("service") + ",");
			writeIncreasedString(quote("default") + ",");
			writeIncreasedString(getImplementedInterfacesAsString(type));
			writeString(");");
			emptyline();
			writeStatement("implementation = (" + type.getQualifiedName() + ") proxy.createProxy()");
			emptyline();
			writeStatement("new DefaultStatsLogger(proxy.getProducer(), new Log4JOutput(Logger.getLogger(\"MoskitoDefault\")))");
			writeStatement("new IntervalStatsLogger(proxy.getProducer(), DefaultIntervals.FIVE_MINUTES, new Log4JOutput(Logger.getLogger(\"Moskito5m\")))");
			writeStatement("new IntervalStatsLogger(proxy.getProducer(), DefaultIntervals.FIFTEEN_MINUTES, new Log4JOutput(Logger.getLogger(\"Moskito15m\")))");
			writeStatement("new IntervalStatsLogger(proxy.getProducer(), DefaultIntervals.ONE_HOUR, new Log4JOutput(Logger.getLogger(\"Moskito1h\")))");
			writeStatement("new IntervalStatsLogger(proxy.getProducer(), DefaultIntervals.ONE_DAY, new Log4JOutput(Logger.getLogger(\"Moskito1d\")))");
		}
		closeBlock();
		emptyline();
		// custom methods
		writeString("private void init() {");
		increaseIdent();
		writeString("try {");
		increaseIdent();
		String[] initCode = ann.initcode();
		for (String s : initCode) {
			writeString(s);
		}
		decreaseIdent();
		writeIncreasedStatement("implementation = MetaFactory.get(" + type.getQualifiedName() + ".class);");
		writeString("} catch (Exception e) {");
		writeIncreasedStatement("LOGGER.error(\"init()\", e)");
		writeIncreasedStatement("throw new RuntimeException(e)");
		writeString("}");
		closeBlock();
		// methods
		Collection<? extends MethodDeclaration> methods = getAllDeclaredMethods(type);
		for (MethodDeclaration method : methods) {
			String methodDecl = getStubMethodDeclaration(method);
			Collection<ReferenceType> exceptions = method.getThrownTypes();
			writeString("@Override");
			writeString("public " + methodDecl + " {");
			increaseIdent();
			if (exceptions.size() > 0) {
				writeString("try{");
				increaseIdent();
			}
			String call = "";
			if (!method.getReturnType().toString().equals("void"))
				call += "return ";
			call += "implementation." + method.getSimpleName();
			Collection<? extends ParameterDeclaration> parameters = method.getParameters();
			String paramCall = "";
			for (ParameterDeclaration p : parameters) {
				if (paramCall.length() != 0)
					paramCall += ", ";
				paramCall += p.getSimpleName();
			}
			call += "(" + paramCall + ");";
			writeString(call);
			if (exceptions.size() > 0) {
				decreaseIdent();

				for (ReferenceType exc : exceptions) {
					writeString("} catch (" + exc.toString() + " e) {");
					writeIncreasedStatement("LOGGER.error(" + quote(method.getSimpleName() + "()") + ", e)");
					writeIncreasedStatement("throw(e)");
				}
				writeString("}");
			}

			closeBlock();
			emptyline();
		}
		// end
		closeBlock();
		closeWriter(writer);
	}

}
