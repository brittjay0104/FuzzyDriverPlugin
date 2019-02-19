package org.fuzzydriver.plugin.nodevisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.MethodInvocation;

public class GeneratedTestVisitor extends ASTVisitor {
	
	public char[] source;
	
	// method call of interest
	public String targetMethod;
	
	// generated inputs
	public List<Object> newInputs;
	
	public GeneratedTestVisitor() {
		
	}
	
	public GeneratedTestVisitor(char[] source, String targetMethod) {
		this.source = source;
		this.targetMethod = targetMethod;
		newInputs = new ArrayList<Object>();
	}
	
	public boolean visit (MethodInvocation node) {
		
		String methInv = node.getName().getFullyQualifiedName();
		
		if (methInv.equals(targetMethod)) {
			System.out.println(findSourceForNode(node));
		}
		
		return true;
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

}
