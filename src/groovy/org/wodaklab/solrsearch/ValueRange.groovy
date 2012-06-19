package org.wodaklab.solrsearch

class ValueRange extends ValueSet implements Visitable {
	def from = '*'
	def to = '*'
	
	ValueRange(from, to) {
		this.from = from
		this.to = to
	}
	
	public boolean equals(Object obj) {
		return obj instanceof ValueRange && obj.from == from && obj.to == to
	}
	
	public int hashCode() { return from.hashCode() + to.hashCode() * 17 }
	
	String toString() {
		return "[" + from + " TO " + to + "]"
	}
	
	void accept(SolrQueryVisitor v) {
		v.visit(this);
	}
}
