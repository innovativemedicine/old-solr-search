package org.wodaklab.solrsearch

import java.util.Set;

class SolrQueryCopierVisitor implements SolrQueryVisitor {
	private SolrQuery query
	def results = []
	
	SolrQueryCopierVisitor(SolrQuery query) {
		this.query = query
	}
	
	SolrQueryCopierVisitor() {
		this(new SolrQuery())
	}
	
	SolrQuery getSolrQuery() {
		return query
	}
	
	void setSolrQuery(SolrQuery q) {
		this.query = q
	}
	
	SolrQueryCopierVisitor merge(SolrQuery query) {
		query.accept(this)
	}
	
	void visit(SolrQuery other) {
		query.with {
			if (other.q)
				q = other.q
			if (other.rows)
				rows = other.rows
			if (other.offset)
				offset = other.offset
			if (other.queryType)
				queryType = other.queryType
			if (other.selectUrl)
				selectUrl = other.selectUrl
      if (other.facetMethod)
        facetMethod = other.facetMethod
			fields.addAll(other.fields)
			stats.addAll(other.stats)
		}
		
		other.facet.each { facetName, facet ->
			facet.accept(this)
			query.facet[facetName] = results.pop()
		}
		
		other.filter.each { filterName, filter ->
			results.push(query.filter[filterName])
			filter.accept(this)
			query.filter[filterName] = results.pop()
		}
		
		other.sort.each { sort ->
			sort.accept(this)
			query.sort << results.pop()
		}
	}
	
	void visit(Facet facet) {
		def f = new Facet(facet.field)
		facet.values.each { valueset ->
			valueset.accept(this)
			f.values << results.pop()
		}
		f.limit = facet.limit
		f.offset = facet.offset
		f.minCount = facet.minCount
		f.prefix = facet.prefix
		f.custom = facet.custom
		results << f
	}
	
	void visit(Filter filter) {
		def old = results.pop()
		def f = new Filter(null, filter.op, null)
		f.setField(old.getField())
		f.multiField = old.multiField	// Moves forward, not back.
		
		f.fallbackField = filter.fallbackField
		f.op = filter.op
		f.prefix = filter.prefix
		
		if (filter.getField())
			f.setField(filter.getField())
		filter.values.each { valueset ->
			valueset.accept(this)
			f.values << results.pop()
		}
		results << f
	}
	
	void visit(ValueRange range) {
		def r = new ValueRange(range.from, range.to)
		if (range.alias())
			r.alias(range.alias())
		results << r
	}
	
	void visit(ValueList list) {
		def l = new ValueList(list.values)
		if (list.alias())
			l.alias(list.alias())
		results << l
	}
	
	void visit(StoredValueSet stored) {
		results << new StoredValueSet(stored.key)
	}
	
	void visit(Sort sort) {
		results << new Sort(sort)
	}
}
