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
	public MethodInvocation methOfInterest;
	public List<?> parameters;
	public String method;


	public TestMethodVisitor (char[] source) {
		this.source = source;
	}
	
	public boolean visit (MethodInvocation node) {
		
		String methInv = node.getName().getFullyQualifiedName();
		
		MethodDeclaration methDec = getMethodDeclaration(node);
		
		if (methDec != null) {
			String method = methDec.getName().getFullyQualifiedName();
			
			// TODO: These values should be passed in somehow
			if (method.equals("testCreateNumber")) {
				this.method = method;
				if (methInv.equals("createNumber")) {
					methOfInterest = node;
					parameters = node.arguments();
				}

			}
			
		}
		
		return true;
	}

	
	public String getMethod() {
		return method;
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
