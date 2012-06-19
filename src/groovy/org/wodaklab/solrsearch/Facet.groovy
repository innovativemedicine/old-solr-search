package org.wodaklab.solrsearch


import org.apache.commons.collections.FactoryUtils
import org.apache.commons.collections.list.LazyList


class Facet implements Visitable {
	String field
	int limit = -1
	int offset = 0
	
	/**
	 * The minimum number of docs with a given value in order ot be included in
	 * the faceting result.
	 */
	int minCount = 0
	
	/**
	 * If not-null, this restricts the faceting to values starting with the given
	 * prefix.
	 */
	String prefix = null
	
	/**
	 * Indicates this customizes another facet and should never be "simple".
	 */
	boolean custom = false
	
	List<ValueSet> values = LazyList.decorate([], FactoryUtils.instantiateFactory(ValueSet.class))
	
	
	Facet(String field, values) {
		this.field = field
		if (values)
			this.values += values
	}
	
	Facet(String field) {
		this(field, null)
	}
	
	Facet(Facet facet) {
		this(facet.field, facet.values)
	}
	
	Facet() {}
	
	public boolean isSimple() {
		return !custom && values.size() == 0
	}
	
	public void field(String fieldName) {
		this.field = fieldName
	}
	
	public ValueList matches(value) {
		ValueList vl = new ValueList([value])
		values << vl
		return vl
	}
	
	public ValueList inList(vals) {
		ValueList vl = new ValueList(vals)
		values << vl
		return vl
	}
	
	public ValueRange between(lb, ub) {
		ValueRange r = new ValueRange(lb, ub) 
		values << r
		return r
	}
	
	public split(Object ... values) {
		values.eachWithIndex { val, i ->
			if (val instanceof Range) {
				if (val.from != val.to || val.from == '*')
					between(val.from, val.to)
				else
					matches(val.from)
			} else {
				if (i == 0) {
					between("*", val)
				} else if (i == values.length - 1) {
					between(val, "*")
				} else {
					matches(val)
				}
			}
		}
	}
	
	void accept(SolrQueryVisitor v) {
		v.visit(this);
	}
}
