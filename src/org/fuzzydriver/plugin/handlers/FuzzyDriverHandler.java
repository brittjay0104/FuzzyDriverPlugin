package org.fuzzydriver.plugin.handlers;

import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.xml.transform.stax.StAXSource;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.text.similarity.LevenshteinDetailedDistance;
import org.apache.commons.text.similarity.LevenshteinResults;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.ui.jarpackager.IJarExportRunnable;
import org.eclipse.jdt.ui.jarpackager.JarPackageData;
import org.eclipse.jdt.ui.jarpackager.JarWriter3;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.progress.UIJob;
import org.fuzzydriver.plugin.nodevisitor.TestMethodVisitor;
import org.fuzzydriver.plugin.util.Test;
import org.fuzzydriver.plugin.util.Util;
import org.fuzzydriver.plugin.views.FuzzyDriverView;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;


import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class FuzzyDriverHandler extends AbstractHandler {
	
	public File inputFile;
	public String input;
	public File workingDirectory = new File ("/Users/bjohnson/eclipse-workspace/");
	
	File binInstrumentedTestDir;
	File binInstrumentedDepDir;
	
	IEditorPart editor = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
	
	IFile testFile;
	Document testDocument;
	ICompilationUnit icu;
	AST ast;
	CompilationUnit cu;
	ASTParser parser;
	
	JarWriter3 jarWriter;
	
	Object currentParam;
	
	Test targetTest;
	IProject targetProject;
	
	List<IFile> filesToExport;
	
	List<String> passingTests;
	List<String> failingTests;
	
	List<String> fuzzedValues;
	List<String> distanceResults;

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		filesToExport = new ArrayList<>();
		passingTests = new ArrayList<>();
		failingTests = new ArrayList<>();
		fuzzedValues = new ArrayList<>();
		
		distanceResults = new ArrayList<String>();
		
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		IWorkbenchPage page = window.getActivePage();
		
		// File of interest
		IEditorInput input = editor.getEditorInput();
		testFile = ((IFileEditorInput)input).getFile();
		
		// project of interest
		
		
		// create test object
		targetTest = new Test(testFile.getName());
		
		icu = JavaCore.createCompilationUnitFrom(testFile);
		
		try {
			// creation of document containing source code			
			String source = icu.getSource();			 
			testDocument = new Document(source);
			
			// create and set up ASTParser
			updateASTParser();
			
			// Find method of interest based on file and project
			String targetTestMethod = "";
			
			if (testFile.getName().contains("NumberUtilsTest")) {
				if (testFile.getFullPath().toString().contains("lang_16")) {
					targetTestMethod = "testCreateNumber"; 
					targetTest.setTestMethod(targetTestMethod);
					targetTest.setProjectName("lang_16_buggy");
				}
				else if (testFile.getFullPath().toString().contains("lang_1_1")) {
					targetTestMethod = "TestLang747"; 
					targetTest.setTestMethod(targetTestMethod);
					targetTest.setProjectName("lang_1_1_buggy");
				} 
				else if (testFile.getFullPath().toString().contains("lang_1_2")) {
					targetTestMethod = "TestLang747"; 
					targetTest.setTestMethod(targetTestMethod);
					targetTest.setProjectName("lang_1_2_buggy");
				}
				else if (testFile.getFullPath().toString().contains("lang_7")) {
					targetTestMethod = "testCreateNumber"; 
					targetTest.setTestMethod(targetTestMethod);
					targetTest.setProjectName("lang_7_buggy");
				}
			} 
			else if (testFile.getName().contains("WordUtilsTest")) {
				targetTestMethod = "testAbbreviate"; 
				targetTest.setTestMethod(targetTestMethod);
				targetTest.setProjectName("lang_45_buggy");
			}
			else if (testFile.getName().contains("StringEscapeUtilsTest")) {
				targetTestMethod = "testEscapeJavaWithSlash"; 
				targetTest.setTestMethod(targetTestMethod);
				targetTest.setProjectName("lang_46_buggy");				
			}
			
			// get parameter of interest
			boolean first = true;
			getMethodParameter(source, targetTestMethod, first);
						
			// Always try "" and null as input
			fuzzedValues.add("");
			fuzzedValues.add(null);
			
			try {
								
				/*
				 * RUN INPUT FUZZERS
				 */
				
				String cmdLineArg = targetTest.getOriginalParameter().replaceAll("\"", "");
				
				// Python fuzzer
				runPythonFuzzer(cmdLineArg);
				
				// JS fuzzer
				runJSFuzzer(cmdLineArg);
				
				// parse case mutations
				parseCaseMutations();
				
				// parse length mutations
				parseLengthMutations();
				
				// parse other mutations
				parseOtherMutations();
				
				
				/*
				 * CHECK FUZZED VALUES FOR DISTANCE
				 */
				calculateEditDistanceResults(cmdLineArg);
				
				/*
				 * RUN TESTS
				 */
				
				System.out.println("\nTest file: " + targetTest.getFilename());
				System.out.println("Test method: " + targetTest.getTestMethod());
				System.out.println("Original test parameter: " + targetTest.getOriginalParameter());
				System.out.println("Full test: " + targetTest.getFullTest() + "\n");
				
				File executorDirectory = new File(workingDirectory.getPath() + "/" + testFile.getProject().getName());
				
				
				// **** Run test with "" ****	
				this.input = fuzzedValues.get(0);
				runTests(page, executorDirectory);
				
				TimeUnit.SECONDS.sleep(2);
				
//				 Run remaining tests from distance results
				for (int i=0; i<= distanceResults.size();i++) {
					
					if (passingTests.size() <1 || failingTests.size()<3) {
						this.input = distanceResults.get(i);
						runTests(page, executorDirectory);
						TimeUnit.SECONDS.sleep(2);						
						System.out.println("Done!");
					} 
					
				}
				
				System.out.println("PASSING TESTS");
				for (String test: passingTests) {
					System.out.println(test);
				}
				
				System.out.println("FAILING TESTS");
				for (String test: failingTests) {
					System.out.println(test);
				}
				
				// write passing and failing tests to file (for view to read from)
				File outputFile = new File(workingDirectory.getPath()+"/fuzzy-output.txt");
				if (outputFile.exists()) {
					outputFile.delete();					
				}
				
				writeOutputFile(workingDirectory.getPath()+"/fuzzy-output.txt");
				
				TimeUnit.SECONDS.sleep(1);
				
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView("org.fuzzydriver.plugin.views.FuzzyDriverView");
				
			}catch (Exception e) {
				// TODO: handle exception
			}
			
										


		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedTreeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
		return null;
	}
	
	private void writeOutputFile(String filename) {
		BufferedWriter bw = null;
		FileWriter fw = null;
		
		try {
			fw = new FileWriter(filename);
			bw = new BufferedWriter(fw);
			
			bw.write("PASSING TESTS\n");
			for (String test: passingTests) {
				bw.write("P: " +test);
				bw.write("\n");
			}
			
			bw.write("FAILING TESTS\n");
			for (String test: failingTests) {
				bw.write("F: " + test);
				bw.write("\n");
			}
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				if (bw != null)
					bw.close();

				if (fw != null)
					fw.close();

			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		
	}

	private void runTests(IWorkbenchPage page, File executorDirectory)
			throws BadLocationException, JavaModelException, InterruptedException, ExecuteException, IOException {
		
		// update and save page
		updateTestInput();
		savePage(page);
		
		// wait for build to finish before running test
		TimeUnit.SECONDS.sleep(2);
		
		// update AST parser
		updateASTParser();
		getMethodParameter(testDocument.get(), targetTest.getTestMethod(), false);
				
		// D4J compile
		d4jCompile(executorDirectory); 
		
		// D4J test
		d4jTest(executorDirectory);
				
	}

	private void d4jCompile(File executorDirectory) throws ExecuteException, IOException {
		CommandLine d4j_compile_cmdLine = new CommandLine("/Users/bjohnson/Documents/Research_2017-2018/defects4j/framework/bin/defects4j");
		d4j_compile_cmdLine.addArgument("compile");
		
		DefaultExecutor d4j_compile_executor = new DefaultExecutor();		
		d4j_compile_executor.setWorkingDirectory(executorDirectory);		
		
		try {
			d4j_compile_executor.execute(d4j_compile_cmdLine);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	private void d4jTest(File executorDirectory) throws ExecuteException, IOException {
		// Store output to know if test passed or failed
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
		
		CommandLine d4j_test_cmdLine = new CommandLine("/Users/bjohnson/Documents/Research_2017-2018/defects4j/framework/bin/defects4j");
		d4j_test_cmdLine.addArgument("test");
		d4j_test_cmdLine.addArgument("-t");
		
		// get package name
		String path = testFile.getFullPath().toString();
		String fullPackage = path.replaceAll("\\/", ".");
		String targetPackage = fullPackage.substring(fullPackage.indexOf("org"), fullPackage.length()-5);
		String singleTest = targetPackage + "::" + targetTest.getTestMethod();
		
		d4j_test_cmdLine.addArgument(singleTest); 		
		
		DefaultExecutor d4j_test_executor = new DefaultExecutor();		
		d4j_test_executor.setWorkingDirectory(executorDirectory);	
		d4j_test_executor.setStreamHandler(streamHandler);
		try {
			d4j_test_executor.execute(d4j_test_cmdLine);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println(outputStream.toString());
		
		// Store test in appropriate list
		if (outputStream.toString().contains("Failing tests: 1")) {
		
			failingTests.add(targetTest.getFullTest());				

		} else {
			System.out.println(targetTest.getFullTest());
			
			passingTests.add(targetTest.getFullTest());				
			
		}
	
	}	

	private void updateASTParser() {
		parser = createParser(testDocument.get());
		
		cu = (CompilationUnit) parser.createAST(null);
		ast = cu.getAST();
	}

	private void savePage(IWorkbenchPage page) {
		
		for (IEditorPart dirtyPage: page.getDirtyEditors()) {
			dirtyPage.doSave(null);
			System.out.println("Editor saved!");
		}

	}

	private void getMethodParameter(String source, String targetTestMethod, boolean first) {
		TestMethodVisitor visitor = new TestMethodVisitor(source.toCharArray(), targetTestMethod);
		cu.accept(visitor);
		
		MethodInvocation testMethodInvoc = visitor.getFullMethod();
		if (visitor.getTestStatements() == null) {			
			targetTest.setFullTest(visitor.getFullTest());
		} else {
			targetTest.setFullTest(visitor.getTestStatements());
		}
		
		// set up old and new parameters for modification
		if (visitor.getIsNotStringLiteral()) {
			// if not hard coded string, get the variable declaration with value
			currentParam = visitor.getDeclOfInterest();	
		} else {
			currentParam = (StringLiteral) testMethodInvoc.arguments().get(0);							
		}
		
		System.out.println("Current parameter = " + currentParam.toString());
		
		if (first) {
			if (visitor.getIsNotStringLiteral()) {
				String param = currentParam.toString();
				String originalParam = param.substring(param.indexOf("\""),param.length()-1);
								
				targetTest.setOriginalParameter(originalParam);
			} else {
				targetTest.setOriginalParameter(currentParam.toString());				
			}
		} 
		
	}

	private String updateTestInput()
			throws BadLocationException, JavaModelException {
		
		// Creation of ASTRewrite
		ASTRewrite rewrite = ASTRewrite.create(ast);
		String newSource = null;
		StringLiteral newParam = ast.newStringLiteral();
					
//		if (this.input == null) {
//			// TODO handle null value for this.input	(Null Literal)
//			NullLiteral nullParam = ast.newNullLiteral();
//			targetTest.setNewParameter(null);
//		}
		
		if (currentParam instanceof StringLiteral) {
			// create component with new param value 
			newSource = replaceStringLiteral(rewrite, newParam);			
		} else {
			// parameter is a variable declaration
			newSource = replaceVariableDeclaration(rewrite, newParam);
		}
		
		return newSource;

	}

	private String replaceVariableDeclaration(ASTRewrite rewrite, StringLiteral newParam)
			throws BadLocationException, JavaModelException {
		String newSource;
		newParam.setLiteralValue(this.input);
		
		VariableDeclarationFragment newVarFrag = ast.newVariableDeclarationFragment();
		SimpleName newVarName = ast.newSimpleName("input");
		newVarFrag.setName(newVarName);
		newVarFrag.setInitializer(newParam);
		
		targetTest.setNewParameter(newParam.toString());
		
		VariableDeclarationStatement oldVarDec = (VariableDeclarationStatement) currentParam;

		VariableDeclarationStatement newVarDec = ast.newVariableDeclarationStatement(newVarFrag);
		newVarDec.setType(ast.newSimpleType(ast.newSimpleName("String")));
		
		rewrite.replace(oldVarDec, newVarDec, null);
		
		TextEdit edits = rewrite.rewriteAST(testDocument, JavaCore.getOptions());
		edits.apply(testDocument);
		
		newSource = testDocument.get();
		icu.getBuffer().setContents(newSource);
		return newSource;
	}

	private String replaceStringLiteral(ASTRewrite rewrite, StringLiteral newParam)
			throws BadLocationException, JavaModelException {
		String newSource;
		newParam.setLiteralValue(this.input);
		
		targetTest.setNewParameter(newParam.toString());
		
		// rewrite AST with new param 
		StringLiteral oldParam = (StringLiteral) currentParam;
		rewrite.replace(oldParam, newParam, null);
		
		TextEdit edits = rewrite.rewriteAST(testDocument, JavaCore.getOptions());
		edits.apply(testDocument);
		
		newSource = testDocument.get();
		icu.getBuffer().setContents(newSource);
		return newSource;
	}

	private void calculateEditDistanceResults(String cmdLineArg) {
		LevenshteinDetailedDistance distanceStrategy = new LevenshteinDetailedDistance();
		String original = cmdLineArg;
		
		LevenshteinResults result;
		
		for (int i=2; i<fuzzedValues.size(); i++) {
			String s = fuzzedValues.get(i);
			 result = distanceStrategy.apply(original, s);
			 
			 // only add result if == 1 (1 edit)
			 if (result.getDistance() ==1) {	
				 distanceResults.add(s);
			 }	 
			 
			 // add some variety to options (in case passing not found with 1 edit
			 if (result.getDistance() > 5 && result.getDistance() <=8)	{
				 distanceResults.add(s);
			 }
		}
		
		System.out.println("There are " + distanceResults.size() + " fuzzer results!");
	}

	private void runJSFuzzer(String cmdLineArg) throws ExecuteException, IOException {
		CommandLine js_cmdLine = new CommandLine("./fuzzer-test.js");
		js_cmdLine.addArgument(cmdLineArg);
		
		DefaultExecutor js_executor = new DefaultExecutor();				
		
		js_executor.execute(js_cmdLine);
	}

	private void runPythonFuzzer(String cmdLineArg) throws ExecuteException, IOException {
		CommandLine py_cmdLine = new CommandLine("./peach-master/fuzz.py");
		py_cmdLine.addArgument(cmdLineArg);
		
		DefaultExecutor py_executor = new DefaultExecutor();
		
		py_executor.execute(py_cmdLine);
	}

	private void parseOtherMutations() throws FileNotFoundException, IOException {
		File otherMutationsFile = new File("other-mutations.txt");
		FileReader otherFileReader = new FileReader(otherMutationsFile);
		BufferedReader otherBR = new BufferedReader(otherFileReader);
		
		String otherLine;
		
		while ((otherLine = otherBR.readLine()) != null) {
			fuzzedValues.add(otherLine);
		}
		
		otherBR.close();
	}

	private void parseLengthMutations() throws FileNotFoundException, IOException {
		File lengthMutationFile = new File("length-mutations.txt");
		FileReader lengthFileReader = new FileReader(lengthMutationFile);
		BufferedReader lengthBR = new BufferedReader(lengthFileReader);
		
		String lengthLine;
		
		while ((lengthLine = lengthBR.readLine()) != null	) {
			fuzzedValues.add(lengthLine);
		}
		
		lengthFileReader.close();
	}

	private void parseCaseMutations() throws FileNotFoundException, IOException {
		File caseMutationFile = new File("case-mutations.txt");
		FileReader caseFileReader = new FileReader(caseMutationFile);
		BufferedReader caseBR = new BufferedReader(caseFileReader);
		
		String caseLine;
		
		while ((caseLine = caseBR.readLine()) != null) {
			fuzzedValues.add(caseLine);
		}
		
		caseFileReader.close();
	}

	private ASTParser createParser(String source) {
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		Map options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_6, options);
		parser.setCompilerOptions(options);
		 
		parser.setResolveBindings(true);
		parser.setStatementsRecovery(true);
		parser.setSource(source.toCharArray());
//			parser.setKind(ASTParser.K_COMPILATION_UNIT);
		return parser;
	}
	
}
