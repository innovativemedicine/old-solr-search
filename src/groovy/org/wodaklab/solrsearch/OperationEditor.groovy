package org.wodaklab.solrsearch

import java.beans.PropertyEditorSupport

class OperationEditor extends PropertyEditorSupport {
	void setAsText(String op) {
		switch (op.toLowerCase()) {
			case ["and", "AND", "all", "ALL"]:
				setValue(Operation.AND)
				break
			case ["or", "OR", "any", "ANY"]:
				setValue(Operation.OR)
				break
			case ["not", "NOT", "none", "NONE"]:
				setValue(Operation.NOT)
				break
			default:
				throw new IllegalArgumentException("Invalid operation given; cannot convert to Operation.")
		}
	}
	
	String getAsText() {
		return getValue()?.toString()
	}
}
