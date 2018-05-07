package org.fuzzydriver.plugin.util;

public class Test {
	
	String filename;
	String originalParameter;
	String newParameter;
	String testMethod;
	String fullTest;
	boolean passed;
	
	public Test(String fname) {
		filename = fname;
	}
	
	public Test(String fname, String param, String test) {
		filename = fname;
		originalParameter = param;
		fullTest = test;
	}
	
	public void setPassed(boolean passed) {
		this.passed = passed;
	}
	
	public void setOriginalParameter(String param) {
		originalParameter = param;
	}
	
	public void setNewParameter(String param) {
		newParameter = param;
	}

	public void setTestMethod(String method) {
		testMethod = method;
	}
	
	public void setFullTest(String test) {
		fullTest = test;
	}
	
	public boolean getPassed() {
		return passed;
	}
	
	public String getOriginalParameter() {
		return originalParameter;
	}
	
	public String getNewParameter() {
		return newParameter;
	}
	
	public String getFullTest() {
		return fullTest;
	}
	
	public String getTestMethod() {
		return testMethod;
	}
	
	public String getFilename() {
		return filename;
	}

}
