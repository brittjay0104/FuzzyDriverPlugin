package org.fuzzydriver.plugin.handlers;

import org.junit.runner.Result;

public class Util {
	
	public static void printResult (Result result) {
		System.out.printf("Tests ran: %s, Failed: %s%n",
				result.getRunCount(), result.getFailureCount());
	}

}