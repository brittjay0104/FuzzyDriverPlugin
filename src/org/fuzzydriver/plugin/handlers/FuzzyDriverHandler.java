package org.fuzzydriver.plugin.handlers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.math.NumberUtilsTest;
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

import edu.cmu.sv.kelinci.Kelinci;
import edu.cmu.sv.kelinci.instrumentor.Instrumentor;

import org.eclipse.jface.text.BadLocationException;
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
			
			// create necessary directory for fuzzer to instrument files
			createInstrumentedTestDirectory();
			createInstrumentedDepDirectory();
			
			// Prepare classes for fuzzing			
			// Only working on sub-directory of classes with class of interest (for now) -- optional alternative available in kelinci-master
			// TODO pass in directory to analyze based on open file location (parent folder); alter "test-classes" --> "classes" for second
			String targetTestDir = workingDirectory.getAbsolutePath() + "/lang_16_buggy/target/test-classes/org/apache/commons/lang3/math";
						
			String[] args = {"-i", targetTestDir, "-o",  binInstrumentedTestDir.getAbsolutePath()};
			
			String targetDependencyDir = workingDirectory.getAbsolutePath() + "/lang_16_buggy/target/classes/org/apache/commons/lang3/math";
			
			String[] args2 = {"-i", targetDependencyDir, "-o", binInstrumentedDepDir.getAbsolutePath()};
			
			Instrumentor.main(args);
			Instrumentor.main(args2);
			
			// start Kelinci server
			String targetDriverCP = workingDirectory.getAbsolutePath() + "/org.fuzzydriver.plugin/bin/org/fuzzydriver/plugin/handlers/FuzzyDriverHandler";	
			String[] kelinciArgs = {targetDriverCP, "@@"};
			
//			System.out.println(kelinciArgs[0] + " " + kelinciArgs[1]);
//			
//			Kelinci.main(kelinciArgs);
						
			String startServerCommand = "java -cp " + binInstrumentedTestDir.getAbsolutePath() + ":" + binInstrumentedDepDir.getAbsolutePath() + ":" + kelinciDir.getAbsolutePath() + ":" + targetTestDir + ":" + targetDependencyDir
					+ " edu.cmu.sv.kelinci.Kelinci " + targetDriverCP + " @@";
			
			ProcessBuilder startServer = new ProcessBuilder(startServerCommand);
			startServer.directory(new File(workingDirectory.getAbsolutePath() + "/kelinci-master/"));
			
			Process server = startServer.start();
			
			// Make sure server starts
			server.waitFor();
			InputStream is = server.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			String outcome = null;
			while ((outcome = br.readLine()) != null) {
				System.out.println(outcome);
			}
			is.close();
			
			String runInterfaceCommand = "";
			ProcessBuilder runInterface = new ProcessBuilder(runInterfaceCommand); 
			runInterface.directory(workingDirectory);			
			
			// Find method of interest
			// TODO pass method to look for into constructor
			TestMethodVisitor visitor = new TestMethodVisitor(source.toCharArray());
			
			cu.accept(visitor);
			
			MethodInvocation oldInvoc = visitor.getFullMethod();
	
			// set up old and new parameters for modification
			StringLiteral oldParam = (StringLiteral) oldInvoc.arguments().get(0);
			StringLiteral newParam = ast.newStringLiteral();
			
			// Create input directory and write original output to input file
			createInputFile(oldParam.getLiteralValue());
			
			// Pass in fuzzed input from file
//			loadFile("/Users/bjohnson/eclipse-workspace/lang_16_buggy/target/test-classes/org/apache/commons/lang3/math/in_dir/testInput.txt");			
			loadFile(workingDirectory + "/org.fuzzydriver.plugin/in_dir/testInput.txt");
									
			newParam.setLiteralValue(this.input);
			
//			// rewrite AST with new param 
			rewrite.replace(oldParam, newParam, null);
//			
			TextEdit edits = rewrite.rewriteAST(document, JavaCore.getOptions());
			edits.apply(document);

			String newSource = document.get();
			icu.getBuffer().setContents(newSource);
			
			
						
			// Update classpath with updated project
//			IPath projectPath = file.getProject().getFullPath();
//			updateClasspath(projectPath);
			
			// Save file
//			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
//			page.saveEditor(editor, true);
			
			// run test
//			runTest(file);

		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedTreeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
		return null;
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

	private void createInstrumentedTestDirectory() {
		binInstrumentedTestDir = new File(workingDirectory + "/lang_16_buggy/target/test-classes/org/apache/commons/lang3/math/bin-instrumented");
		
		if (!binInstrumentedTestDir.exists()) {
			if (binInstrumentedTestDir.mkdir()) {
				System.out.println("Created instrumented classes directory!");
			} else {
				System.out.println("Failed to create instrumented classes directory!");
			}
		}
	}
	
	private void createInstrumentedDepDirectory() {
		binInstrumentedDepDir = new File(workingDirectory + "/lang_16_buggy/target/classes/org/apache/commons/lang3/math/bin-instrumented");
		
		if (!binInstrumentedDepDir.exists()) {
			if (binInstrumentedDepDir.mkdir()) {
				System.out.println("Created directory for instrumented dependency classes!");
			} else {
				System.out.println("Failed to create instrumented dependency classes directory!");
			}
		}
	}

	private void createInputFile(String value) throws IOException {
//		File inputDir = new File("/Users/bjohnson/eclipse-workspace/lang_16_buggy/target/test-classes/org/apache/commons/lang3/math/in_dir");
//		
//		if (!inputDir.exists()) {
//			if (inputDir.mkdir()) {				
//				System.out.println("Created input directory!");
//			} else {
//				System.out.println("Failed to create input directory!");
//			}
//		}
		
		// create and populate input file
//		inputFile = new File(inputDir + "/" + "testInput.txt");
		inputFile = new File(workingDirectory + "/org.fuzzydriver.plugin/in_dir/testInput.txt");
		inputFile.createNewFile();
		
		FileWriter fw = new FileWriter(inputFile.getAbsoluteFile());
		BufferedWriter bw = new BufferedWriter(fw);
		
		bw.write(value);
		bw.close();
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
