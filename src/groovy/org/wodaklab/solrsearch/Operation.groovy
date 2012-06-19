package org.wodaklab.solrsearch

enum Operation {
	AND("AND"),
	OR("OR"),
	NOT("NOT")
	
	final String op
	
	Operation(String op) {
		this.op = op
	}
	
	String toString() {
		return op;
	}
}
