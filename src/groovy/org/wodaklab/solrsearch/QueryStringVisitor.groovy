package org.wodaklab.solrsearch


import static SolrSearchUtils.escape
import static SolrSearchUtils.isValidFieldName

import org.apache.commons.logging.LogFactory


class QueryStringVisitor implements SolrQueryVisitor {
	private static log = LogFactory.getLog(QueryStringVisitor.class)
	def storage = [:]
	def params = []
	def results = []
	
	QueryStringVisitor(Map storage) {
		this.storage = storage
	}
	
	QueryStringVisitor() {
		this([:])
	}
	
	String getQueryString() {
		return params.collect { URLEncoder.encode(it.p) + "=" + URLEncoder.encode(it.v) }.join("&")
	}
	
	String getValue(ValueSet vs) {
		vs.accept(this);
		return results.pop()
	}
	
	private void add(p, v) {
		params << [p: p, v: v]
	}
	
	void visit(SolrQuery query) {
		add("q.alt", "*:*")
		if (query.q)
			add("q", query.q)
		if (query.rows)
			add("rows", query.rows.toString())
		if (query.offset)
			add("start", query.offset.toString())
		if (query.queryType)
			add("qt", escape(query.queryType))
		if (query.outputType)
			add("wt", escape(query.outputType))
    if (query.facetMethod)
      add("facet.method", escape(query.facetMethod))
		if (query.fields) {
			add("fl", query.fields.findAll { name ->
				if (!isValidFieldName(name)) {
					log.error("Invalid field name given for field list - skipping: " + name)
					return false
				}
				return true
			}.join(","))
		}
		if (query.stats) {
			add("stats", "true")
			query.stats.each { name ->
				if (!isValidFieldName(name)) {
					log.error("Invalid field name given for statistics - skipping: " + name)
					return false
				}
				add("stats.field", name)
			}
		}
		
		if (query.facet.size()) {
			add("facet", "true")
			add("facet.limit", "-1")
			query.facet.each { name, f ->
				f.accept(this)
			}
		}
		query.filter.each { name, f ->
			f.accept(this)
		}
		
		def sorts = []
		query.sort.each {
			it.accept(this)
			def sortStr = results.pop()
			if (sortStr)
				sorts << sortStr
		}
		add("sort", sorts.reverse().join(", "))
	}
	
	void visit(Facet facet) {
		if (!isValidFieldName(facet.field)) {
			log.error("Invalid field name given for facet - skipping: " + facet.field)
			return
		}
		
		if (!facet.simple) {
			def field = facet.field + ":"
			
			facet.values.each { valueset ->
				valueset.accept(this)
				add("facet.query", field + results.pop())
			}
			
		} else {
			add("facet.field", facet.field)
			
			if (facet.limit >= 0)
				add("f.${facet.field}.facet.limit", facet.limit.toString())
			if (facet.offset > 0)
				add("f.${facet.field}.facet.offset", facet.offset.toString())
			if (facet.minCount > 0)
				add("f.${facet.field}.facet.mincount", facet.minCount.toString())
			if (facet.prefix)
				add("f.${facet.field}.facet.prefix", facet.prefix.toString())
		}
	}
	
	void visit(Filter filter) {
		def fq = new StringBuilder()
		def fields = filter.field?: [ filter.fallbackField ]
		
		fields.eachWithIndex { it, notFirstField ->
			if (!isValidFieldName(it)) {
				log.error("Invalid field name specified - skipping: " + it)
				return
			}
			
			if (notFirstField)
				fq << " "
			
			def field = it + ":"
			
			switch (filter.op) {
				case Operation.AND:
					field = "+" + field
					break
				case Operation.NOT:
					field = "-" + field
			}
			
			def values = filter.values.collect {
				if (filter.prefix && it instanceof ValueList) {
					def vl = new ValueList(it.values.findAll { it.trim() }.collect { "${filter.prefix}${it}" })
					vl.accept(this);
				} else {
					it.accept(this);
				}
				results.pop()
			}.findAll { it.trim() }
			fq << values.collect { field + it }.join(" ")
		}
		
		def query = fq.toString()
		if (query)
			add("fq", query)
	}
	
	void visit(ValueRange range) {
		results << ValueSetEditor.getAsText(range)
	}
	
	void visit(ValueList list) {
		if (list.values.size() > 1)
			results << "(" + ValueSetEditor.getAsText(list) + ")"
		else
			results << ValueSetEditor.getAsText(list)
	}
	
	void visit(StoredValueSet stored) {
		def key = stored.key
		def valueSet = storage[key]
		if (!valueSet)
			throw new NoSuchStoredValueSetException(key)
		if (!(valueSet instanceof ValueSet)) {
			def ed = new ValueSetEditor()
			ed.setAsText(valueSet)
			valueSet = ed.value
		}
		valueSet.accept(this)
	}
	
	void visit(Sort sort) {
		if (!isValidFieldName(sort.field)) {
			log.error("Invalid field name given for sort - skipping: " + sort.field)
			results << ""
			return
		}
		results << sort.field + ((sort.order in ["desc", "DESC"]) ? " desc" : " asc")
	}
}
