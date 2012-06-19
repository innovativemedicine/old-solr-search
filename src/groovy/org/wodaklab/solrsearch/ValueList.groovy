package org.wodaklab.solrsearch

import static org.wodaklab.solrsearch.SolrSearchUtils.escape

class ValueList extends ValueSet implements Visitable {
	def values = []
	
	ValueList(values) {
		this.values += values.sort()
	}
	
	public boolean equals(Object obj) {
		return obj instanceof ValueList && obj.values == values
	}
	
	public int hashCode() { return values.hashCode() * 19 }
	
	/**
	* Returns a string version of this value set. The string returned can
	* be immediately used in Solr.
	*/
   String toString() {
	   switch (values.size()) {
		case 0:
			return ""
			break
		case 1:
			return escape(values[0])
			break;
		default:
			return "(" + values.collect{escape(it)} + ")"
		}
   }
	
	void accept(SolrQueryVisitor v) {
		v.visit(this);
	}
}
