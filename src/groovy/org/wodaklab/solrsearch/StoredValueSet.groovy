package org.wodaklab.solrsearch

class StoredValueSet extends ValueSet implements Visitable {
	def key = null
	
	StoredValueSet(key) {
		this.key = key
	}
	
	public boolean equals(Object obj) {
		if (obj instanceof StoredValueSet) {
			return obj.key == this.key
		}
		return false
	}
	
	public int hashCode() { return 23 * key.hashCode() }
	
	/**
	 * Returns a string version of this value set. The string returned cannot be
	 * used in a Solr search.
	 */
	String toString() {
		return "stored:" + key
	}
	
	void accept(SolrQueryVisitor v) {
		v.visit(this);
	}
}
