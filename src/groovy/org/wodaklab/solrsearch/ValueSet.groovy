package org.wodaklab.solrsearch

/**
 * A ValueSet represents a simple value set (either a list or a range) that can
 * be used to filter search results.
 */
class ValueSet {
	private String alias = null
	
	public ValueSet alias(String alias) {
		this.alias = alias
		return this
	}
	
	public String alias() {
		return alias
	}
}
