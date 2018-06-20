package org.fuzzydriver.plugin.nodevisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

public class TestMethodVisitor extends ASTVisitor {
	
	public char[] source;
	
	
	// method call of interest
	public MethodInvocation methOfInterest;
	public String paramOfInterest;
	public VariableDeclarationStatement declOfInterest;
	
	// test method to find
	public String targetTestMethod;
	
	// full test statement (for tool output)
	public String fullTest;
	
	public boolean notStringLiteral = false;
	
	// list of variables declared (in case needed to find declaration for test input)
	public List<VariableDeclarationStatement> declarations;
	
	// list of statements in test
	String testStatements;


	public TestMethodVisitor (char[] source, String targetTestMethod) {
		this.source = source;
		this.targetTestMethod = targetTestMethod;
		declarations = new ArrayList<>();
	}
	
	public boolean visit(VariableDeclarationStatement node) {
		declarations.add(node);
		return true;
	}
	
	public boolean visit (MethodInvocation node) {
		
		String methInv = node.getName().getFullyQualifiedName();
		
		MethodDeclaration methDec = getMethodDeclaration(node);
		
		if (methDec != null) {			
			// Lang-16
			if (methInv.equals("createNumber") && methDec.getName().toString().equals("testCreateNumber") 
					|| methInv.equals("createNumber") && methDec.getName().toString().equals("TestLang747")
					|| methInv.equals("abbreviate") && methDec.getName().toString().equals("testAbbreviate") 
					|| methInv.equals("escapeJava") && methDec.getName().toString().equals("testEscapeJavaWithSlash")) {
				
				methOfInterest = node;
				
				// if hardcoded string, get just test statement
				if (node.arguments().get(0) instanceof StringLiteral) {
					paramOfInterest = node.arguments().get(0).toString();	
					
					// get full test statement for tool output
					ExpressionStatement fullTest = findFullTest(node);
					
					if (fullTest != null) {
						this.fullTest = fullTest.toString();				
					}
					
				}
				// if variable, get all statements in test
				else {
					if (node.arguments().get(0) instanceof SimpleName) {
						notStringLiteral = true;
						SimpleName nameParamOfInterest = (SimpleName) node.arguments().get(0);
						
						for (VariableDeclarationStatement stmt : declarations	) {
							List<VariableDeclarationFragment> frags = stmt.fragments();
							
							if (frags != null) {
								for (VariableDeclarationFragment frag: frags) {
									if (frag.getName().toString().equals(nameParamOfInterest.toString())) {
										
										List<Statement> statements = methDec.getBody().statements();
										StringBuffer sb = new StringBuffer();
										
										for (Statement s: statements) {
											sb.append(s);
											sb.append("\n");
										}
										
										testStatements = sb.toString();										
										declOfInterest = stmt;
									}
								}
							}
						}
					}
//					// find declaration of variable passed in for value
//					
				}
			}
			
		}
		
		
		return true;
	}
	
	private ExpressionStatement findFullTest(ASTNode node) {
		if (node.getParent() != null) {
			return node instanceof ExpressionStatement ? (ExpressionStatement)node : findFullTest(node.getParent());
		}
		
		return null;
	}
	
	public boolean getIsNotStringLiteral() {
		return notStringLiteral;
	}
	
	public String getFullTest() {
		return fullTest;
	}

	public String getTestStatements() {
		return testStatements;
	}
	
	public String getParamOfInterest() {
		return paramOfInterest;
	}
	
	public VariableDeclarationStatement getDeclOfInterest() {
		return declOfInterest;
	}
	
	public MethodInvocation getFullMethod() {
		return methOfInterest;
	}
	
	protected String findSourceForNode(ASTNode node) {
		try {
			return new String(Arrays.copyOfRange(source, node.getStartPosition(), node.getStartPosition() + node.getLength()));
		}
		catch (Exception e) {
			System.err.println("OMG PROBLEM MAKING SOURCE FOR "+node);
			return "";
		}
	}
	
	private MethodDeclaration getMethodDeclaration(ASTNode node) {
		if (node.getParent() != null){
			return node instanceof MethodDeclaration ? (MethodDeclaration)node : getMethodDeclaration(node.getParent());			
		}
		
		return null;
	}

}
