package org.fuzzydriver.plugin.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.naming.InitialContext;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;
import org.fuzzydriver.plugin.util.Test;

public class FuzzyDriverAction implements IEditorActionDelegate {
	private IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
	
	public File inputFile;
	public Object input;
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
		
	Object currentParam;
	
	Test targetTest;
	IProject targetProject;
	
	List<IFile> filesToExport;
	
	List<String> passingTests;
	List<String> failingTests;
	
	List<String> fuzzedValues;
	List<String> distanceResults;
	
	File outputFile;

	@Override
	public void run(IAction arg0) {
		
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
		if (editor instanceof ITextEditor) {
			ITextEditor editor = (ITextEditor)this.editor;
			String selectedMethod = getSelectedText(editor);
			
			System.out.println(selectedMethod);
		}

	}
	
	private ITextSelection getSelection(ITextEditor editor) {
	     ISelection selection = editor.getSelectionProvider()
	            .getSelection();
	     return (ITextSelection) selection;
	}

	private String getSelectedText(ITextEditor editor) {
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
