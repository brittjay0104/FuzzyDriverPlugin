package org.fuzzydriver.plugin.handlers;

import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
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
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.ui.jarpackager.IJarExportRunnable;
import org.eclipse.jdt.ui.jarpackager.JarPackageData;
import org.eclipse.jdt.ui.jarpackager.JarWriter3;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.fuzzydriver.plugin.nodevisitor.TestMethodVisitor;
import org.fuzzydriver.plugin.util.Test;
import org.fuzzydriver.plugin.util.Util;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;


import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
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
	private IWorkspace workspace = ResourcesPlugin.getWorkspace();
	
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
	
	StringLiteral oldParam;
	
	Test targetTest;
	IProject targetProject;
	
	List<IFile> filesToExport;
	
	List<String> passingTests;
	List<String> failingTests;
	
	List<String> fuzzedValues;
//	ListMultimap<String, Integer> distanceResults;
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
			updateASTParser(source);
			
			// Find method of interest
			String targetTestMethod = "";
			
			if (testFile.getName().contains("NumberUtilsTest")) {
				if (testFile.getFullPath().toString().contains("lang_16")) {
					targetTestMethod = "testCreateNumber"; 
					targetTest.setTestMethod(targetTestMethod);
					targetTest.setProjectName("lang_16_buggy");
				}
				else if (testFile.getFullPath().toString().contains("lang_1_1")) {
					// TODO 
				} else {
					// TODO
				}
			} 
			// TODO other file name
			
			// get parameter of interest
			boolean first = true;
			getMethodParameter(source, targetTestMethod, first);
						
			// Always try "" and null as input
			fuzzedValues.add("");
			fuzzedValues.add(null);
			
			try {
				
				// TODO present results as comments? In view?
				// TODO add annotations that automate determining what test method/method invocation we care about (Ask Yuriy?)
				 
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
							
				// update and save page
				updateTestInput();			
				savePage(page);
				
				// wait for build to finish before running test
				TimeUnit.SECONDS.sleep(2);
				
				// D4J compile
				d4jCompile(executorDirectory); 
				
				// D4J test (see Terminal for how to run single test)
				d4jTest(executorDirectory);
				
				// TODO check if passed or failed (command output after ":")
				
//				String pathToJar = targetDirectory + "/" + targetTest.getProjectName()+".jar";
//				
//				runTest(pathToJar);	
				
				
				// **** Run test with null (only save if passes?) ****
				
				// TODO: see if can get this working -- maybe with NullLiteral?
				
//				// Update current "old" method param from current document source 
//				this.input = fuzzedValues.get(1);
//				
//				updateASTParser(testDocument.get());
//				getMethodParameter(testDocument.get(), targetTestMethod, false);
//				
//				// update and save page
//				updateTestInput();
//				savePage(page);
//				
//				// wait for build to finish before running test
//				TimeUnit.SECONDS.sleep(2);
//				runTest(testFile);
				
//				System.out.println(distanceResults.size());
				
				// Iterate over "closest" fuzzed values to see if any pass
				
//				this.input = distanceResults.get(0);
//				System.out.println(this.input);
//				
//				updateASTParser(testDocument.get());
//				getMethodParameter(testDocument.get(), targetTestMethod, false);
//				
//				updateTestInput();
//				savePage(page);
//				
//				TimeUnit.SECONDS.sleep(2);
//				
//				// delete existing jar before making new one
//				deleteOldJar(targetDirectory, targetTest.getProjectName()+".jar");
//				// TODO add identifier (number from loop?) to then come back and loop through/load for running tests
//				jarTargetProject(targetDirectory, targetTest.getProjectName()+".jar");
//				refreshWorkspace();
//				
//				runTest(testFile, targetDirectory + "/" + targetTest.getProjectName()+".jar");
				
				
//				for (String fuzzedValue : distanceResults) {
//					System.out.println(fuzzedValue);
//					this.input = fuzzedValue;
//					
//					updateASTParser(testDocument.get());
//					getMethodParameter(testDocument.get(), targetTestMethod, false);
//					
//					updateTestInput();
//					savePage(page);
//					
//					TimeUnit.SECONDS.sleep(2);
//					
////					// delete existing jar before making new one
//					deleteOldJar(targetDirectory, targetTest.getProjectName()+".jar");
//					refreshWorkspace();
//					System.out.println("Workspace refreshed!");
//					
//					jarTargetProject(targetDirectory, targetTest.getProjectName()+".jar");
//					refreshWorkspace();
//					System.out.println("Workspace refreshed!");
//					
//					runTest(testFile);
//													
//				}
				
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
		d4j_test_executor.execute(d4j_test_cmdLine);
		
		System.out.println("Output = " + outputStream.toString());
		
		// Store test in appropriate list
		if (outputStream.toString().contains("Failing Tests: 0")) {
			passingTests.add(targetTest.getFullTest());
		} else {
			failingTests.add(targetTest.getFullTest());
		}
		
		
	}

	private void d4jCompile(File workingDirectory) throws ExecuteException, IOException {
		CommandLine d4j_compile_cmdLine = new CommandLine("/Users/bjohnson/Documents/Research_2017-2018/defects4j/framework/bin/defects4j");
		d4j_compile_cmdLine.addArgument("compile");
		
		DefaultExecutor d4j_compile_executor = new DefaultExecutor();		
		d4j_compile_executor.setWorkingDirectory(workingDirectory);		
		
		d4j_compile_executor.execute(d4j_compile_cmdLine);
	}
	
	private void addFiles(IResource[] resources) throws CoreException{
		
		for (IResource resource : resources) {
			if (resource instanceof IFile) {
				filesToExport.add((IFile)resource);
			}
			else {
				IFolder folder = (IFolder) resource;
				IResource[] nestedResources = folder.members();
				addFiles(nestedResources);				
			}
		}
	}
	

	private void updateASTParser(String source) {
		parser = createParser(source);
		
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
		targetTest.setFullTest(visitor.getFullTest());
		
		// set up old and new parameters for modification
		oldParam = (StringLiteral) testMethodInvoc.arguments().get(0);
		System.out.println("Old parameter: " +  oldParam.toString());
		
		if (first) {
			targetTest.setOriginalParameter(oldParam.toString());
		} 
	}

	private String updateTestInput()
			throws BadLocationException, JavaModelException {
		
		// create component with new param value 
		StringLiteral newParam = ast.newStringLiteral();
		newParam.setLiteralValue(this.input);
		
		targetTest.setNewParameter(newParam.toString());
	
		System.out.println("New parameter: " + targetTest.getNewParameter());
		
		// Creation of ASTRewrite
		ASTRewrite rewrite = ASTRewrite.create(ast);
		
		// rewrite AST with new param 
		rewrite.replace(oldParam, newParam, null);
		
		TextEdit edits = rewrite.rewriteAST(testDocument, JavaCore.getOptions());
		edits.apply(testDocument);
		
		String newSource = testDocument.get();
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
//			 System.out.println("Fuzzed value: " + s + "     " + "Levenshtein score: " + result.getDistance());
			 
			 // only add result if > 0 (not the same string) and <= 4 (no more than 4 edits)
			 if (result.getDistance() ==1) {	
//				 System.out.println(fuzzedValues.get(i) + " distance = " + result.getDistance());
				 distanceResults.add(s);
			 }	 	
		}
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
	
	public void runTest(String pathToJar) throws IOException, ClassNotFoundException {
		
		String filename = testFile.getName().substring(0, testFile.getName().length()-5);
		
		if (pathToJar != null && testFile != null) {
			JarFile jarFile = new JarFile(pathToJar);
			Enumeration<JarEntry> e = jarFile.entries();
			
			URL[] urls  = {new URL("jar:file:" + pathToJar+"!/")};
			for (URL url : urls) {
				System.out.println(url.toExternalForm());
			}
			
			URLClassLoader cl = URLClassLoader.newInstance(urls);
			
			while (e.hasMoreElements()) {
				JarEntry je = e.nextElement();
				if (je.isDirectory() || !je.getName().endsWith(".class")) {
					continue;
				}
				// -6 because of .class
				if (je.getName().contains(filename)) {
					String className = je.getName().substring(0, je.getName().length()-6);
					className = className.replace('/', '.');
					System.out.println(className);
					Class testClass = cl.loadClass(className);
					
					if (testClass != null) {
						
						JUnitCore jUnitCore = new JUnitCore();
						
						Result result = jUnitCore.run(testClass);
						for (Failure f: result.getFailures()) {
							System.out.println(f.getTestHeader() + " failed!");
							System.out.println(f.getMessage());
						}
						
//						Request request = Request.method(testClass, "testCreateNumber");
//						Result result = jUnitCore.run(request);
						
						if (result != null) {			
							Util.printResult(result);
							
							// save tests based on whether they passed on failed
							if (result.getFailureCount() == 0) {
								passingTests.add(targetTest.getFullTest());
							} else {
								failingTests.add(targetTest.getFullTest());
							}
						}

					} else {
						System.out.println("Could not create class file!");
					}
					
				}
			}
			jarFile.close();
		}

	}
	
	/**
	 * Returns null if can't find class.
	 */
	private Class findClass (IFile file) {
		
		// get package name
		String path = file.getFullPath().toString();
		String fullPackage = path.replaceAll("\\/", ".");
		String targetPackage = fullPackage.substring(fullPackage.indexOf("org"), fullPackage.length()-5);

				
		// create class from package
		Class targetClass;
		
		try {
			
			targetClass = Class.forName(targetPackage);
			
			return targetClass;
			
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
				
		return null;
	}
}
