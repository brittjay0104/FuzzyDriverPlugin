package org.fuzzydriver.plugin.nodevisitor;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;

public class TestMethodVisitor extends ASTVisitor {
	
	public char[] source;
	
	
	// method call of interest
	public MethodInvocation methOfInterest;
	public List<?> parameters;
	
	// test method to find
	public String targetTestMethod;


	public TestMethodVisitor (char[] source, String targetTestMethod) {
		this.source = source;
		this.targetTestMethod = targetTestMethod;
	}
	
	public boolean visit (MethodInvocation node) {
		
		String methInv = node.getName().getFullyQualifiedName();
		
		MethodDeclaration methDec = getMethodDeclaration(node);
		
		if (methDec != null) {			
			// Lang-16
			if (methInv.equals("createNumber") && methDec.getName().toString().equals("testCreateNumber")) {
				methOfInterest = node;
				parameters = node.arguments();
			}
			
		}
		
		return true;
	}

	
	public List<?> getParameters() {
		return parameters;
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
