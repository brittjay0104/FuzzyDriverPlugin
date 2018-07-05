package org.fuzzydriver.plugin.nodevisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.PrimitiveType.Code;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

public class TestMethodVisitor extends ASTVisitor {
	
	public char[] source;
	
	public boolean originalTest;
	
	// method call of interest
	public MethodInvocation methOfInterest;
	public Object paramOfInterest;
	public VariableDeclarationStatement declOfInterest;
	
	// test method to find
	public String targetTestMethod;
	
	// full test statement (for tool output)
	public String fullTest;
	public String originalFullTest;
	
	public boolean notStringLiteral = false;
	
	// list of variables declared (in case needed to find declaration for test input)
	public List<VariableDeclarationStatement> declarations;
	
	// list of statements in test
	String testStatements;


	public TestMethodVisitor (char[] source, String targetTestMethod, boolean original) {
		this.source = source;
		this.targetTestMethod = targetTestMethod;
		declarations = new ArrayList<>();
		originalTest = original;
	}
	
	public boolean visit(CharacterLiteral node) {
		
		CharacterLiteral num = node;
		
		
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
									
									if (sb != null) {
										if (originalTest) {
											originalFullTest = sb.toString();
										}
										
										testStatements = sb.toString();																					
									}
									
									declOfInterest = stmt;
								}
							}
						}
					}
				} else {
					// if hardcoded value, get just gather test statement
					ExpressionStatement fullTest = findFullTest(node);
					
					if (fullTest != null) {
						if (originalTest) {
							originalFullTest = fullTest.toString();
						}
						
						this.fullTest = fullTest.toString();				
					}
					
					if (node.arguments().get(0) instanceof StringLiteral || node.arguments().get(0) instanceof CharacterLiteral) {
						paramOfInterest = node.arguments().get(0).toString();				
						
					} else if (node.arguments().get(0) instanceof NumberLiteral) {
						// handle numbers
						NumberLiteral numParam = (NumberLiteral) node.arguments().get(0);
						
						paramOfInterest = numParam.getToken();
						
					} else if (node.arguments().get(0) instanceof BooleanLiteral	) {
						BooleanLiteral boolParam = (BooleanLiteral) node.arguments().get(0);
						
						paramOfInterest = boolParam.booleanValue();
					} else if (node.arguments().get(0) instanceof NullLiteral) {
						NullLiteral nullParam = (NullLiteral) node.arguments().get(0);
						
						paramOfInterest = null;
					}
					
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
	
	public String getOriginalTest()	{
		return originalFullTest;
	}

	public String getTestStatements() {
		return testStatements;
	}
	
	public Object getParamOfInterest() {
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
