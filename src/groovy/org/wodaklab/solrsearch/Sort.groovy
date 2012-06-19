package org.wodaklab.solrsearch

class Sort implements Visitable {
	String field
	String order = "asc"
	
	Sort() {
		
	}
	
	Sort(Sort s) {
		field = s.field
		order = s.order
	}
	
	public void accept(SolrQueryVisitor v) {
		v.visit(this)
	}
	
	public String toString() {
		return field + " " + order
	}
}
