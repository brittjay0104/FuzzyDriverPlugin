package org.fuzzydriver.plugin.nodevisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

public class GeneratedTestVisitor extends ASTVisitor {
	
	public char[] source;
	
	// method call of interest
	public String targetMethod;
	
	// store generated inputs for single param method
	public List<Object> newInputs;
	// store generated inputs for multi parameter method
	public HashMap<String, List<Object>> newMultiInputs;
	
	List<VariableDeclarationStatement> declarations;
	
	public boolean notStringLiteral = false;
	
	// list of statements in test
	String testStatements;
	
	// full test 
	String fullTest;
	
	public Object genParamOfInterest;
	
	public GeneratedTestVisitor() {
		
	}
	
	public GeneratedTestVisitor(char[] source, String targetMethod) {
		this.source = source;
		this.targetMethod = targetMethod;
		newInputs = new ArrayList<Object>();
		newMultiInputs = new HashMap<String, List<Object>>();
		declarations = new ArrayList<VariableDeclarationStatement>();
	}
	
	public boolean visit (VariableDeclarationStatement node) {
		declarations.add(node);
		
		return true;
	}
	
	public boolean visit (MethodInvocation node) {
		
		String methInv = node.getName().getFullyQualifiedName();
		
		MethodDeclaration methDec = getMethodDeclaration(node);
		String methodName = methDec.getName().toString();
		
		if (methInv.equals(targetMethod)) {
//			System.out.println("Test calling target method --> " + findSourceForNode(node));
			
			List params = node.arguments();
			
			if (params.size() == 1) {
				Object p = params.get(0);
				
				if (p instanceof SimpleName) {
					
					SimpleName nameGenInput = (SimpleName) p;
					
					System.out.println(nameGenInput.getParent().toString());
					
					// find variable declaration that sets value
					for (VariableDeclarationStatement stmt : declarations) {
						List<VariableDeclarationFragment> frags = stmt.fragments();
						
						if (frags != null) {
							for (VariableDeclarationFragment frag: frags) {
								if (frag.getName().toString().equals(nameGenInput.toString())) {
									
									List<Statement> statements = methDec.getBody().statements();
									StringBuffer sb = new StringBuffer();
									
									for (Statement s : statements) {
										sb.append(s);
										sb.append("\n");
									}
									
									if (sb != null) {
										testStatements = sb.toString();
										
									}
									
									VariableDeclarationStatement genVarDeclaration = stmt;
								}
							}
						}
					}
				} else {
					// hardcoded so just gather test statement
					ExpressionStatement fullTest = findFullTest(node);
					
					if (fullTest != null) {
						this.fullTest = fullTest.toString();
					}
					
					if (p instanceof StringLiteral || p instanceof CharacterLiteral) {
						genParamOfInterest = p;
						
					} else if (p instanceof NumberLiteral) {
						// numbers
						NumberLiteral genNumParam = (NumberLiteral) p;
						
						genParamOfInterest = genNumParam;
						
					} else if (p instanceof BooleanLiteral) {
						// boolean
						BooleanLiteral genBoolParam = (BooleanLiteral) p;
						
						genParamOfInterest = genBoolParam;
						
					} else if (p instanceof NullLiteral) {
						NullLiteral genNullParam = (NullLiteral) p;
						
						genParamOfInterest = genNullParam;
					}
					
					
					newInputs.add(genParamOfInterest);
				}
			} else {
				// iterate over all parameters
				for (Object p : params) {
					// check if parameter hardcoded or literal value
					if (p instanceof SimpleName) {
						notStringLiteral = true;
						
					}	
				}
			}
			
			
		}
		
		return true;
	}
	
	public List<Object> getGeneratedSingleParamInputs() {
		return newInputs;
	}
	
	public HashMap<String, List<Object>> getGeneratedMultiParamInputs(){
		return newMultiInputs;
	}
	
	private ExpressionStatement findFullTest(ASTNode node) {
		if (node.getParent() != null) {
			return node instanceof ExpressionStatement ? (ExpressionStatement)node : findFullTest(node.getParent());
		}
		
		return null;
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
