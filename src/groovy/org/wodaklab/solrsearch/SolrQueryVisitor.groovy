package org.wodaklab.solrsearch

interface SolrQueryVisitor {
	void visit(SolrQuery query)
	void visit(Facet facet)
	void visit(Filter filter)
	void visit(ValueRange range)
	void visit(ValueList list)
	void visit(StoredValueSet stored)
	void visit(Sort sort)
}
