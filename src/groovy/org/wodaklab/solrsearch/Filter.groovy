package org.wodaklab.solrsearch

import org.apache.commons.collections.FactoryUtils
import org.apache.commons.collections.list.LazyList

/**
 * Represents a simple field filter for search results.
 */
class Filter implements Visitable {
	String fallbackField = null
	protected Set<String> fields = [] as Set
	Operation op = Operation.OR
	List<ValueSet> values = LazyList.decorate([], FactoryUtils.instantiateFactory(ValueSet.class))
	Boolean includesCustomRange = false
	Integer customRangeLower = 0
	Integer customRangeUpper = 0
	boolean multiField = true
	
	// Adds a prefix to EVERY ValueSet.
	String prefix
	
	Filter(String field, Operation op, values) {
		fallbackField = field
		/*
		if (field)
			this.setField(field)
		*/
		this.op = op
		if (values)
			this.values += values
	}
	
	Filter(String field, values) {
		this(field, Operation.OR, values)
	}
	
	Filter(String field) {
		this(field, null)
	}
	
	Filter(Filter filter) {
		this.setField(filter.getField())
		this.op = filter.op
		this.values += filter.values
	}
	
	Filter() { }
	
	public Set<String> getField() { return this.fields.clone() }
	
	public void setField(Set<String> field) {
		if (multiField) {
			this.fields.addAll(field)
		} else {
			this.fields = new HashSet(field)
			// this.fields = [ field.find { true } ] as Set
		}
	}
	
	public void setField(Object field) {
		if (field instanceof Collection) {
			this.setField(field as Set)
		} else if (field != null) {
			if (multiField) {
				this.fields.add(field)
			} else {
				this.fields =  [ field ] as Set
			}
		}
	}
	
	public void field(String fieldName) {
		this.setField(fieldName)
	}
	
	public void op(Operation oper) {
		this.op = oper
	}
	
	public void matches(value) {
		values << new ValueList([value])
	}
	
	public void inList(vals) {
		values << new ValueList(vals)
	}
	
	public void between(lb, ub) {
		values << new ValueRange(lb, ub)
	}
	
	void accept(SolrQueryVisitor v) {
		v.visit(this);
	}
}
