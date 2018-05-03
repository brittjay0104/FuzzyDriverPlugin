package org.fuzzydriver.plugin.handlers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.lang3.math.NumberUtilsTest;
import org.apache.commons.text.similarity.LevenshteinDetailedDistance;
import org.apache.commons.text.similarity.LevenshteinResults;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.fuzzydriver.plugin.nodevisitor.TestMethodVisitor;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import org.eclipse.jface.text.Document;
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
	File kelinciDir = new File(workingDirectory.getAbsolutePath() + "/kelinci-master/instrumentor/build/libs/kelinci.jar");
	
	List<String> fuzzedValues = new ArrayList<>();
	ListMultimap<String, Integer> distanceResults = new ArrayListMultimap<String, Integer>();

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		
		IEditorPart editor = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
		
		IEditorInput input = editor.getEditorInput();
		IFile file = ((IFileEditorInput)input).getFile();
		
		ICompilationUnit icu = JavaCore.createCompilationUnitFrom(file);
		
		try {
			// creation of document containing source code			
			String source = icu.getSource();			 
			Document document = new Document(source);
			
			// create and set up ASTParser
			ASTParser parser = createParser(source);
			
			CompilationUnit cu = (CompilationUnit) parser.createAST(null);
			AST ast = cu.getAST();
			
			// Creation of ASTRewrite
			ASTRewrite rewrite = ASTRewrite.create(ast);
				
			
			// Find method of interest
			String targetTestMethod = "";
			
			if (file.getName().contains("NumberUtilsTest")) {
				if (file.getFullPath().toString().contains("lang_16")) {
					targetTestMethod = "testCreateNumber"; 
				}
				else if (file.getFullPath().toString().contains("lang_1_1")) {
					// TODO 
				} else {
					// TODO
				}
			} 
			// TODO other file names
			
			TestMethodVisitor visitor = new TestMethodVisitor(source.toCharArray(), targetTestMethod);
			cu.accept(visitor);
			
			MethodInvocation oldInvoc = visitor.getFullMethod();
			System.out.println(oldInvoc.getName().toString());
			
			// set up old and new parameters for modification
			StringLiteral oldParam = (StringLiteral) oldInvoc.arguments().get(0);
			System.out.println(oldParam.toString());
			
			StringLiteral newParam = ast.newStringLiteral();
						
			// Always try "" and null as input
			fuzzedValues.add("");
			fuzzedValues.add(null);
			
			try {
				
				// TODO run tests with "nearest" inputs; store passing and failing
				// TODO present results as comments? In view?
				// TODO add annotations that automate determining what test method/method invocation we care about (?)
				 
				/*
				 * RUN INPUT FUZZERS
				 */
				
				String cmdLineArg = oldParam.toString().replaceAll("\"", "");
				
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
				calculateEditDistance(cmdLineArg);
				
				/*
				 * RUN TESTS
				 */
				
				boolean passed = false;
				
				// Run test with ""
				this.input = fuzzedValues.get(0);
				
				
				
				
				// Run test with null
				
				
				// Iterate over "closest" fuzzed values to see if any pass
				for (String fuzzedValue : distanceResults.keySet()) {
					
//					newParam.setLiteralValue(this.input);
					
//					// rewrite AST with new param 
//					rewrite.replace(oldParam, newParam, null);
//					
//					TextEdit edits = rewrite.rewriteAST(document, JavaCore.getOptions());
//					edits.apply(document);
//
//					String newSource = document.get();
//					icu.getBuffer().setContents(newSource);

								
					// Update classpath with updated project
//					IPath projectPath = file.getProject().getFullPath();
//					updateClasspath(projectPath);
					
					// Save file
//					IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
//					page.saveEditor(editor, true);
					
					// run test
//					runTest(file);
					
					System.out.println("Fuzzed value: " + fuzzedValue + " Levenshtein score: " + distanceResults.get(fuzzedValue));
					
					
					
				}
				
				
				
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

	private void calculateEditDistance(String cmdLineArg) {
		LevenshteinDetailedDistance distanceStrategy = new LevenshteinDetailedDistance();
		String original = cmdLineArg;
		
		LevenshteinResults result;
		
		for (int i=2; i<fuzzedValues.size(); i++) {
			String s = fuzzedValues.get(i);
			 result = distanceStrategy.apply(original, s);
			 
			 // only add result if > 0 (not the same string) and <= 4 (no more than 4 edits)
			 if (result.getDistance() > 0 && result.getDistance() <=4) {						 						 
				 System.out.println("Fuzzed value: " + s + "     " + "Levenshtein score: " + result.getDistance());
				 distanceResults.put(s,result.getDistance());
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
	
	public void loadFile(String file) {
		try {
			inputFile = new File(file);
			FileReader fr = new FileReader(inputFile);
			BufferedReader br = new BufferedReader(fr);
//			StringBuffer sb = new StringBuffer();
			
			if ((input = br.readLine()) != null) {
				System.out.println(input);
			}
			
			fr.close();
			
			
		} catch (IOException e) {
			
		}
	
	}
	
	public void updateClasspath(IPath path) {
		IProject pluginProject = ResourcesPlugin.getWorkspace().getRoot().getProject("org.fuzzydriver.plugin");
		IClasspathEntry updatedProject = JavaCore.newProjectEntry(path);
		
		if (pluginProject != null) {
			System.out.println(pluginProject.getName());
			
			try {
				IJavaProject javaProject = (IJavaProject) pluginProject.getNature(JavaCore.NATURE_ID);
				IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
				List<IClasspathEntry> classpathList = new LinkedList(java.util.Arrays.asList(rawClasspath));
				
				for (IClasspathEntry item:classpathList) {
					System.out.println(item.getPath().toString());
				}
				
			} catch (CoreException e) {
				
			}
		}	
	}
	
	public void runTest(IFile file) {
		
		JUnitCore jUnitCore = new JUnitCore();
		Request request = Request.method(NumberUtilsTest.class, "testCreateNumber");
		
		Result result = jUnitCore.run(request);
		
		Util.printResult(result);
		
	}
	
	/**
	 *
	 * Returns a string that stores the contents of the file passed in.
	 *
	 * @param filename
	 * @return
	 */
	
	public static String readFiletoString(String filename) {
		StringBuffer sb = new StringBuffer();
		for(String s: readFile(filename))
		{
			sb.append(s);
			sb.append("\n");
		}
		return sb.toString();

	}
	
	/**
	 *
	 * Helper method for readFileToString (reads file to List of Strings)
	 *
	 * @param file (String)
	 * @return
	 */
	
	public static List<String> readFile(String file) {
		List<String> retList = new ArrayList<String>();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(file));
			String line;
			while ((line = br.readLine()) != null) {
				retList.add(line);
			}
		} catch (Exception e) {
			retList = new ArrayList<String>();
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					retList = new ArrayList<String>();
					e.printStackTrace();
				}
			}
		}
		return retList;
	}
}
