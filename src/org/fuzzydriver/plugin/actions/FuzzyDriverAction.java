package org.fuzzydriver.plugin.actions;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.naming.InitialContext;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.text.similarity.LevenshteinDetailedDistance;
import org.apache.commons.text.similarity.LevenshteinResults;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;
import org.fuzzydriver.plugin.nodevisitor.GeneratedTestVisitor;
import org.fuzzydriver.plugin.nodevisitor.TestMethodVisitor;
import org.fuzzydriver.plugin.util.Test;
import org.jboss.forge.roaster.Roaster;

public class FuzzyDriverAction implements IEditorActionDelegate {
	private IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
	
	public File inputFile;
	public Object input;
	public File workingDirectory = new File ("/Users/bjohnson/Documents/oxy-workspace/");
	public String packageNameStart = "org";
	
	File binInstrumentedTestDir;
	File binInstrumentedDepDir;
	
	IEditorPart editor = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
	
	IFile testFile;
	Document testDocument;
	ICompilationUnit icu;
	AST ast;
	CompilationUnit cu;
	ASTParser parser;
		
	Object currentParam;
	
	Test targetTest;
	IProject targetProject;
	String targetProjectName;
	
	List<IFile> filesToExport;
	
	List<String> passingTests;
	List<String> failingTests;
	
	List<String> fuzzedValues;
	List<String> distanceResults;
	
	File outputFile;

	@Override
	public void run(IAction arg0) {
		
		IViewPart view;
		try {
			view = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView("org.fuzzydriver.plugin.views.FuzzyDriverView");
			PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().hideView(view);
		} catch (PartInitException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		
		filesToExport = new ArrayList<>();
		passingTests = new ArrayList<>();
		failingTests = new ArrayList<>();
		fuzzedValues = new ArrayList<>();
		
		distanceResults = new ArrayList<String>();
		
		// write passing and failing tests to file (for view to read from)
		outputFile = new File(workingDirectory.getPath()+"/fuzzy-output.txt");
		if (outputFile.exists()) {
			outputFile.delete();					
		}
		
		IWorkbenchPage page = window.getActivePage();
		
		// File of interest
		IEditorInput input = editor.getEditorInput();
		testFile = ((IFileEditorInput)input).getFile();
		
		// get selected method
		CompilationUnitEditor editor = (CompilationUnitEditor)this.editor;
		ITextSelection selection = getSelection(editor);
		String selectedMethod = selection.getText();

		System.out.println("The method under test is: " + selectedMethod);
		
		// create test object
		targetTest = new Test(testFile.getName());
		targetProjectName = testFile.getProject().getName();
		
		// create compilation unit from test file
		icu = JavaCore.createCompilationUnitFrom(testFile);
		
		try {
			// creation of document containing source code			
			String source = icu.getSource();			 
			testDocument = new Document(source);
			
			// create and set up ASTParser
			updateASTParser();
			
			// get parameter of interest
			boolean first = true;
			
			// get test method of selected method
			int selectionOffset = selection.getOffset();
			ITypeRoot root = EditorUtility.getEditorInputJavaElement(this.editor, false);
			IJavaElement selectedElement =  root.getElementAt(selectionOffset);
			
			String targetTestMethod = selectedElement.getElementName();				
			
			// pass target method into visitor to get test method and other relevant parts 
			getMethodParameter(source, selectedMethod, targetTestMethod, first);
			targetTest.setTargetMethod(selectedMethod);
			 
			System.out.println("\nTest file: " + targetTest.getFilename()); 
			System.out.println("Project: " + targetProjectName);
			System.out.println("Test method: " + targetTest.getTestMethod());
			System.out.println("Target method: " + targetTest.getTargetMethod());
			System.out.println("Original test parameter: " + targetTest.getOriginalParameter());
			System.out.println("Full test: " + targetTest.getFullTest() + "\n");
			
//			// write original test to file to pipe to view later
			writeOriginalTestToFile(workingDirectory.getPath()+"/fuzzy-output-original.txt");
			
			// Always try "" and null as input
			fuzzedValues.add("");
			fuzzedValues.add(null);
			
			File executorDirectory = new File(workingDirectory.getPath() + "/" + testFile.getProject().getName());
			String filePath = testFile.getFullPath().toOSString();
			// assumes package starts with org but can be changed
			String targetTestPackage = filePath.substring(filePath.indexOf("org"), filePath.length()-5).replaceAll("/", ".");
			String targetClassPackage = targetTestPackage.substring(0,targetTestPackage.indexOf("Test"));
			String classDir = executorDirectory.getAbsolutePath() + "/target/classes";
			
			/* 
			 * RUN EVOSUITE
			 */
			
			// create EvoSuite command
			// java -jar /Users/bjohnson/Documents/oxy-workspace/lib/evosuite-1.0.6.jar
			DefaultExecutor evoExecutor = new DefaultExecutor();
			evoExecutor.setWorkingDirectory(executorDirectory);

			CommandLine runEvoSuiteCmd = new CommandLine("java");
			runEvoSuiteCmd.addArgument("-jar");
			runEvoSuiteCmd.addArgument("/Users/bjohnson/Documents/oxy-workspace/lib/evosuite-1.0.6.jar");
			runEvoSuiteCmd.addArgument("-class");
			runEvoSuiteCmd.addArgument(targetClassPackage);
			runEvoSuiteCmd.addArgument("-projectCP");
			runEvoSuiteCmd.addArgument(classDir);
//			runEvoSuiteCmd.addArgument("-criterion");
//			runEvoSuiteCmd.addArgument("branch");
						
//			evoExecutor.execute(runEvoSuiteCmd);
			
			// TODO: gather inputs from generated tests with matching method call
			
			List<String> evoGeneratedInputs = new ArrayList<>();
			String targetMethod = targetTest.getTargetMethod();
			
			// directory with tests
			File evoTestsDir = new File(executorDirectory.getAbsolutePath() + "/evosuite-tests/org/apache/commons/lang3/math");
			
			
//			// put EvoSuite tests dir in classpath
			// get current classpath
//			IJavaProject project = JavaCore.create(testFile.getProject());
//			IClasspathEntry[] oldCP = project.getRawClasspath();
//			List<IClasspathEntry> newCP = new ArrayList<>();
//			
//			for (IClasspathEntry cp : oldCP) {
//				newCP.add(cp);
//			}
//			
//			IClasspathEntry evoEntry = JavaCore.newSourceEntry(new Path(evoTestsDir.getAbsolutePath()));
//			newCP.add(evoEntry);
//			project.setRawClasspath((IClasspathEntry[]) newCP.toArray(), null);
//		 
			// find and parse files in directory
			File[] testFiles = evoTestsDir.listFiles();
			
			
			
			if (testFiles != null) {
				for (File file : testFiles) {
					InputStream is = new FileInputStream(file);
					BufferedReader br = new BufferedReader(new InputStreamReader(is));
					
					String line = br.readLine();
					StringBuilder sb = new StringBuilder();
					
					while (line != null) {
						sb.append(line).append("\n");
						line = br.readLine();
					}
					
					String fileAsString = sb.toString();
					Document genTestDocument = new Document(fileAsString);
					ASTParser genTestParser = createParser(genTestDocument.get());
					CompilationUnit gcu = (CompilationUnit) genTestParser.createAST(null);
					
					GeneratedTestVisitor visitor = new GeneratedTestVisitor(fileAsString.toCharArray(), targetMethod);
					gcu.accept(visitor);
				}
				
			}

			/*
			 * RUN INPUT FUZZERS
			 */
			
//			String cmdLineArg = targetTest.getOriginalParameter().replaceAll("\"", "");
//			System.out.println(cmdLineArg);
//			
//			// Python fuzzer
//			runPythonFuzzer(cmdLineArg);
//			
//			// JS fuzzer
//			runJSFuzzer(cmdLineArg);
//			
//			// parse case mutations
//			parseCaseMutations();
//			
//			// parse length mutations
//			parseLengthMutations();
//			
//			// parse other mutations
//			parseOtherMutations();
//			
//			

//			
////			// **** Run test with "" ****	
//			this.input = fuzzedValues.get(0);
//			runTests(page, executorDirectory);
//			
//			TimeUnit.SECONDS.sleep(2);
//			
//			/*
//			 * CHECK FUZZED VALUES FOR DISTANCE & RUN TESTS
//			 */
//			
//			calculateEditDistanceResults(cmdLineArg, page, executorDirectory);
//			
//			writeOutputFile();
		}
		
			
//			try {
//				IViewPart output = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView("org.fuzzydriver.plugin.views.FuzzyDriverView");
//				
//				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().hideView(output);
//				TimeUnit.SECONDS.sleep(3);
//				
//				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView("org.fuzzydriver.plugin.views.FuzzyDriverView");				
//				
//			} 
//			catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} 
//			catch (PartInitException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}			

			
//			/*
//			 * RUN TESTS
//			 */
//			
						
		 catch (JavaModelException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (Exception e) {
			// TODO: handle exception
		} 


	}
	
	private void calculateEditDistanceResults(String cmdLineArg, IWorkbenchPage page, File executorDirectory) throws JavaModelException, ExecuteException, BadLocationException, InterruptedException, IOException {
		
		LevenshteinDetailedDistance distanceStrategy = new LevenshteinDetailedDistance();
		String original = cmdLineArg;
		
		LevenshteinResults result;
		int threshold = 1;
		
		while (passingTests.size() <1 || failingTests.size() <3) {
			for (int i=2; i<fuzzedValues.size(); i++) {
				String s = fuzzedValues.get(i);
				result = distanceStrategy.apply(original, s);
				
				if (result.getDistance() == threshold) {
					if (distanceResults.indexOf(s) == -1) {
						distanceResults.add(s);
						
						this.input = s;
						runTests(page, executorDirectory);
						TimeUnit.SECONDS.sleep(2);						
					}
				}
			}
			threshold +=1;
		}
		
//		System.out.println("There are " + distanceResults.size() + " fuzzer results!");
	}
	
	
	private void writeOutputFile() {
		BufferedWriter bwPassing = null;
		BufferedWriter bwFailing = null;		
		
		FileWriter fwPassing = null;
		FileWriter fwFailing = null;
		
		String passingOutputFilename = workingDirectory.getPath()+"/fuzzy-output-passing.txt";
		String failingOutputFilename = workingDirectory.getPath()+"/fuzzy-output-failing.txt";
		
		try {
			fwPassing = new FileWriter(passingOutputFilename);
			fwFailing = new FileWriter(failingOutputFilename);
			
			bwPassing = new BufferedWriter(fwPassing);
			bwFailing = new BufferedWriter(fwFailing);
			
			// TODO format input before writing to file (for view)
			
			for (String test: passingTests) {
				for (String result: distanceResults) {
					if (test.contains(result)) {
						String formattedTest = test.substring(0, test.indexOf(result)) + "<b>" + test.substring(test.indexOf(result), test.indexOf(result)+result.length()) 
						+ "</b>" + test.substring(test.indexOf(result)+result.length(), test.length()) ;
						
						bwPassing.write("P: " + formattedTest);
						bwPassing.write("\n");
						System.out.println(formattedTest);
					}
				}
			}
			
			for (String test: failingTests) {
				for (String result: distanceResults) {
					if (test.contains(result)) {
						String formattedTest = test.substring(0, test.indexOf(result)) + "<b>" + test.substring(test.indexOf(result), test.indexOf(result)+result.length()) 
						+ "</b>" + test.substring(test.indexOf(result)+result.length(), test.length()) ;
						
						bwFailing.write("F: " + formattedTest);
						bwFailing.write("\n");
					}
				}
			}
			
//			for (String test: passingTests) {
//				bwPassing.write("P: " +test);
//				bwPassing.write("\n");
//			}	
//			
//			for (String test: failingTests) {
//				bwFailing.write("F:" + test);
//				bwFailing.write("\n");
//			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				if (bwPassing != null)
					bwPassing.close();
				
				if (bwFailing != null)
					bwFailing.close();

				if (fwPassing != null)
					fwPassing.close();
				
				if (fwFailing != null) {
					fwFailing.close();
				}

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
		getMethodParameter(testDocument.get(), targetTest.getTargetMethod(), targetTest.getTestMethod(), false);
				
		// D4J compile
		d4jCompile(executorDirectory); 
		
		// D4J test
		d4jTest(executorDirectory);
				
	}
	
	private String updateTestInput()
			throws BadLocationException, JavaModelException {
		
		// Creation of ASTRewrite
		ASTRewrite rewrite = ASTRewrite.create(ast);
		String newSource = null;
		
		
		if (currentParam instanceof VariableDeclarationStatement) {
			VariableDeclarationStatement stmt = (VariableDeclarationStatement) currentParam;
			
			String type = stmt.getType().toString();
			
			if (type.equals("boolean")) {
				BooleanLiteral newParam;
				
				if ((boolean) currentParam) {
					newParam = ast.newBooleanLiteral(false);					
				} else {
					newParam = ast.newBooleanLiteral(true);
				}
				
				newSource = replaceVariableDeclaration(rewrite, newParam, type);
				
			} else if (type.equals("String")) {
				StringLiteral newParam = ast.newStringLiteral();
				newSource = replaceVariableDeclaration(rewrite, newParam, type);
				
			} else if (type.equals("int") || type.equals("float") || type.equals("double")|| type.equals("short")
					|| type.equals("long")) {
				NumberLiteral newParam = ast.newNumberLiteral();
				newSource = replaceVariableDeclaration(rewrite, newParam, type);
				
			}
				
		} else {
			
			replaceASTLiteral(rewrite);
		}
		
		return newSource;

	}	
	
	private void savePage(IWorkbenchPage page) {
		
		for (IEditorPart dirtyPage: page.getDirtyEditors()) {
			dirtyPage.doSave(null);
			System.out.println("Editor saved!");
		}

	}
	
	private String replaceVariableDeclaration(ASTRewrite rewrite, Object newParam, String type)
			throws BadLocationException, JavaModelException {

		// Old variable fragments
		VariableDeclarationStatement oldVarDec = (VariableDeclarationStatement) currentParam;
		VariableDeclarationFragment frag = (VariableDeclarationFragment) oldVarDec.fragments().get(0);
		SimpleName varName = frag.getName();
		
		// new variable fragments
		String newSource = "";
		VariableDeclarationFragment newVarFrag = ast.newVariableDeclarationFragment();
		SimpleName newVarName = ast.newSimpleName(varName.getIdentifier());
		newVarFrag.setName(newVarName);
		
		targetTest.setNewParameter(newParam.toString());

		// new variable declaration statement based on fragment
		// TODO: is this in the wrong location?
		VariableDeclarationStatement newVarDec = ast.newVariableDeclarationStatement(newVarFrag);
		
		// determine which type of parameter passed in
		if (newParam instanceof BooleanLiteral) {
			// already has value if boolean
			BooleanLiteral param = (BooleanLiteral)newParam;
			newVarFrag.setInitializer(param);			
			
			newVarDec.setType(ast.newSimpleType(ast.newSimpleName("boolean")));
			
		} else if (newParam instanceof StringLiteral) {
			StringLiteral param = (StringLiteral)newParam;
			if (this.input == null) {
				// TODO should this be actual null value or the string "null"?
				param.setLiteralValue("null");
				
			} else {
				param.setLiteralValue(this.input.toString());				
			}
			
			newVarFrag.setInitializer(param);
			newVarDec.setType(ast.newSimpleType(ast.newSimpleName("String")));
			
		} else if (newParam instanceof CharacterLiteral) {
			CharacterLiteral param = (CharacterLiteral)newParam;
			if (this.input == null) {
				// TODO: figure out how to do this (if possible)
			} else {
				param.setCharValue((char)this.input);				
			}
			
			newVarFrag.setInitializer(param);
			newVarDec.setType(ast.newSimpleType(ast.newSimpleName("char")));
			
		} else if (newParam instanceof NumberLiteral) {
			
			if (this.input == null) {
				// TODO handle if null
			} else {
				NumberLiteral param = (NumberLiteral)newParam;
				
				if (this.input == null) {
					// TODO handle if null
				} else {
					param.setToken(this.input.toString());					
				}
				
				newVarFrag.setInitializer(param);
				
				if (type.equals("int")) {
					newVarDec.setType(ast.newSimpleType(ast.newSimpleName("int")));
					
				} else if (type.equals("float")) {
					newVarDec.setType(ast.newSimpleType(ast.newSimpleName("float")));
					
				} else if (type.equals("double")) {
					newVarDec.setType(ast.newSimpleType(ast.newSimpleName("double")));
					
				} else if (type.equals("short")) {
					newVarDec.setType(ast.newSimpleType(ast.newSimpleName("short")));
					
				} else if (type.equals("long")) {
					newVarDec.setType(ast.newSimpleType(ast.newSimpleName("long")));
					
				}
				
			}
			
		}
		
		rewrite.replace(oldVarDec, newVarDec, null);
		
		TextEdit edits = rewrite.rewriteAST(testDocument, JavaCore.getOptions());
		edits.apply(testDocument);
		
		newSource = testDocument.get();
		icu.getBuffer().setContents(newSource);
		
		return newSource;
	}
	
	private String replaceASTLiteral(ASTRewrite rewrite)
			throws BadLocationException, JavaModelException {
		
		String newSource = "";
		
		// handle each primitive type accordingly
		if (currentParam instanceof StringLiteral) {
			StringLiteral oldParam = (StringLiteral)currentParam;
			StringLiteral newParam = ast.newStringLiteral();
			
			if (this.input == null) {
				// TODO is this right?
				newParam.setLiteralValue("null");
			} else {
				newParam.setLiteralValue(this.input.toString());								
			}
			
			targetTest.setNewParameter(newParam.toString());
			rewrite.replace(oldParam, newParam, null);	
			
		} else if (currentParam instanceof BooleanLiteral	) {
			BooleanLiteral oldParam = (BooleanLiteral)currentParam;
			BooleanLiteral newParam;
			
			if ((boolean) currentParam) {
				newParam = ast.newBooleanLiteral(false);
			} else {
				newParam = ast.newBooleanLiteral(true);
			}
			
		} else if (currentParam instanceof CharacterLiteral) {
			CharacterLiteral oldParam = (CharacterLiteral)currentParam;
			CharacterLiteral newParam = ast.newCharacterLiteral();
			
			if (this.input == null) {
				// TODO handle if null
			} else {
				newParam.setCharValue((char)this.input);				
			}
			
			rewrite.replace(oldParam, newParam, null);
		} else if (currentParam instanceof NumberLiteral) {
			NumberLiteral oldParam = (NumberLiteral)currentParam;
			NumberLiteral param = ast.newNumberLiteral();
			
			if (this.input == null) {
				// TOOD is this right?
				param.setToken("null");
			} else {
				param.setToken(this.input.toString());				
			}
			
			rewrite.replace(oldParam, param, null);
		}

		
		TextEdit edits = rewrite.rewriteAST(testDocument, JavaCore.getOptions());
		edits.apply(testDocument);
		
		newSource = testDocument.get();
		icu.getBuffer().setContents(newSource);
		
		return newSource;
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

	
	private void writeOriginalTestToFile(String filename) {
		BufferedWriter bw = null;
		FileWriter fw = null;
		
		try {
			fw = new FileWriter(filename);
			bw = new BufferedWriter(fw);
			
			bw.write("O: " + targetTest.getOriginalTest());
			bw.write("\n");
			
			
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
			if (otherLine.startsWith("\"") && otherLine.endsWith("\"")) {
				String removeQuotes = otherLine.replace("\"", "");
				String removeSemicolon = removeQuotes.substring(0, removeQuotes.length()-1);
				
				System.out.println(removeSemicolon);
				
				fuzzedValues.add(removeSemicolon);
			} else {
				
				fuzzedValues.add(otherLine);				
			}
		}
		
		otherBR.close();
	}

	private void parseLengthMutations() throws FileNotFoundException, IOException {
		File lengthMutationFile = new File("length-mutations.txt");
		FileReader lengthFileReader = new FileReader(lengthMutationFile);
		BufferedReader lengthBR = new BufferedReader(lengthFileReader);
		
		String lengthLine;
		
		while ((lengthLine = lengthBR.readLine()) != null	) {
			if (lengthLine.startsWith("\"") && lengthLine.endsWith("\"")) {
				String removeQuotes = lengthLine.replace("\"", "");
				String removeSemicolon = removeQuotes.substring(0, removeQuotes.length()-1);
								
				System.out.println(removeSemicolon);
				
				fuzzedValues.add(removeSemicolon);
			} else {
				
				fuzzedValues.add(lengthLine);
			}
		}
		
		lengthFileReader.close();
	}

	private void parseCaseMutations() throws FileNotFoundException, IOException {
		File caseMutationFile = new File("case-mutations.txt");
		FileReader caseFileReader = new FileReader(caseMutationFile);
		BufferedReader caseBR = new BufferedReader(caseFileReader);
		
		String caseLine;
		
		while ((caseLine = caseBR.readLine()) != null) {
			if (caseLine.startsWith("\"") && caseLine.endsWith("\"") ) {
				String removeQuotes = caseLine.replace("\"", "");
				String removeSemicolon = removeQuotes.substring(0, removeQuotes.length()-1);
								
				System.out.println(removeSemicolon);
				
				fuzzedValues.add(removeSemicolon);
			} else {
				
				fuzzedValues.add(caseLine);
			}
		}
		
		caseFileReader.close();
	}
	
	private void getMethodParameter(String source, String targetMethod, String targetTestMethod, boolean first) {
		TestMethodVisitor visitor;
		if (first) {
			visitor = new TestMethodVisitor(source.toCharArray(), targetMethod, targetTestMethod, true);
		} else {
			visitor = new TestMethodVisitor(source.toCharArray(), targetMethod, targetTestMethod, false);
		}
		
		cu.accept(visitor);		
		
		targetTest.setOriginalTest(visitor.getOriginalTest());			
		
		MethodInvocation testMethodInvoc = visitor.getFullMethod();
		if (visitor.getTestStatements() == null) {			
			targetTest.setFullTest(visitor.getFullTest());
		} else {
			targetTest.setFullTest(visitor.getTestStatements());
		}
		
		targetTest.setTestMethod(visitor.getTargetTestMethod());
		
		// set up old and new parameters for modification
		if (visitor.getIsNotStringLiteral()) {
			// if not hard coded string, get the variable declaration with value
			currentParam = visitor.getDeclOfInterest();	
		} else {
			currentParam = visitor.getParamOfInterest();							
		}
		
		System.out.println("Current parameter = " + currentParam.toString());
		
		if (first) {
			if (visitor.getIsNotStringLiteral()) {
				String param = currentParam.toString();
				String originalParam = param.substring(param.indexOf("\""),param.length()-1);
								
				targetTest.setOriginalParameter(originalParam);
			} else {
				
				targetTest.setOriginalParameter(currentParam.toString());
//				if (currentParam == null) {
//					targetTest.setOriginalParameter("null");
//				} else {
//					targetTest.setOriginalParameter(currentParam.toString());									
//				}
			}
		} 
		
	}
	
	private void updateASTParser() {
		parser = createParser(testDocument.get());
		
		cu = (CompilationUnit) parser.createAST(null);
		ast = cu.getAST();
	}
	
	private ASTParser createParser(String source) {
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		Map options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_6, options);
		parser.setCompilerOptions(options);
		 
		parser.setResolveBindings(true);
		parser.setStatementsRecovery(true);
		parser.setSource(source.toCharArray());
		
		return parser;
	}
	
	private ITextSelection getSelection(CompilationUnitEditor editor) {
	     ISelection selection = editor.getSelectionProvider()
	            .getSelection();
	     return (ITextSelection) selection;
	}

	private String getSelectedText(CompilationUnitEditor editor) {
	     return getSelection(editor).getText();
	}

	@Override
	public void selectionChanged(IAction arg0, ISelection arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setActiveEditor(IAction arg0, IEditorPart arg1) {
		// TODO Auto-generated method stub
		
	}

}
