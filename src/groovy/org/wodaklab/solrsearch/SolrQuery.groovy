package org.wodaklab.solrsearch

import java.util.Map;

import groovy.lang.Closure;

import org.codehaus.groovy.runtime.GroovyCategorySupport

import org.apache.commons.collections.FactoryUtils
import org.apache.commons.collections.map.LazyMap
import org.apache.commons.collections.list.LazyList
import org.apache.commons.collections.Transformer


/**
 * Represents a Solr query.
 */
class SolrQuery implements Visitable {
	String selectUrl = null
	String q
	Integer rows
	Integer offset
	String queryType
  String facetMethod = null
  
	private String outputType = "json"
	Set<String> fields = [] as Set
	Map<String,Filter> filter = LazyMap.decorate([:], new Transformer() {
		public Object transform(Object str) {
			if (str.endsWith("_custom"))
				str = str.substring(0, str.size() - 7)
			return new Filter(str)
		}
	})
	Map<String,Facet> facet = LazyMap.decorate([:], new Transformer() {
		public Object transform(Object str) {
			if (str.endsWith("_custom")) {
				def f = new Facet(str.substring(0, str.size() - 7))
				f.custom = true
				return f
			} else {
				return new Facet(str)
			}
		}
	})
	List<Sort> sort = LazyList.decorate([], FactoryUtils.instantiateFactory(Sort.class))
	Set<String> stats = [] as Set
	
	SolrQuery(String queryType, fields) {
		this.queryType = queryType
		this.outputType = outputType
		this.fields += fields
	}
	
	SolrQuery() {
	}
	
	public getOutputType() {
		return outputType
	}
	
	public SolrQuery fields(Closure clos) {
		def stringSetBuilder = new StringSetBuilder()
		clos.delegate = stringSetBuilder
		clos.resolveStrategy = Closure.DELEGATE_ONLY
		clos()
		fields.addAll(stringSetBuilder.set)
		return this
	}
	
	public SolrQuery stats(Closure clos) {
		def stringSetBuilder = new StringSetBuilder()
		clos.delegate = stringSetBuilder
		clos.resolveStrategy = Closure.DELEGATE_ONLY
		clos()
		stats.addAll(stringSetBuilder.set)
		return this
	}
	
	public SolrQuery facet(Closure clos) {
		def facetBuilder = new FacetMapBuilder()
		clos.delegate = facetBuilder
		clos.resolveStrategy = Closure.DELEGATE_ONLY
		clos()
		facet += facetBuilder.map
		return this
	}
	
	public SolrQuery filter(Closure clos) {
		def filterBuilder = new FilterMapBuilder()
		clos.delegate = filterBuilder
		clos.resolveStrategy = Closure.DELEGATE_ONLY
		clos()
		filter += filterBuilder.map
		return this
	}
	
	public SolrQuery merge(SolrQuery query) {
		def copier = new SolrQueryCopierVisitor(this)
		copier.merge(query)
		return this
	}
	
	public SolrQuery plus(SolrQuery query) {
		def copier = new SolrQueryCopierVisitor()
		copier.merge(this)
		copier.merge(query)
		return copier.solrQuery
	}
	
	public SolrQuery leftShift(SolrQuery query) {
		return merge(query)
	}
	
	void accept(SolrQueryVisitor v) {
		v.visit(this)
	}
}

class StringSetBuilder {
	Set set = [] as Set
	
	public Object getProperty(String value) {
		def metaProperty = StringSetBuilder.metaClass.getMetaProperty(value)
		if (metaProperty)
			return metaProperty.getProperty(this)
		set.add(value)
		return value
	}
}

class MapBuilder {
	protected Map map = [:]
	
	public Object invokeMethod(String name, Object args) {
		def metaMethod = this.metaClass.getMetaMethod(name, args)
		if (metaMethod) {
			return metaMethod.invoke(delegate, args)
		}
		
		if (args.size() != 1 || !(args[0] instanceof Closure)) {
			throw new IllegalArgumentException("Expected 1 argument of type Closure for method: " + name)
		}
		
		Closure clos = args[0]
		clos.delegate = getProperty(name)
		clos.resolveStrategy = Closure.DELEGATE_FIRST
		clos()
		return this
	}
	
	public Map getMap() {
		return this.map
	}
}

class FacetMapBuilder extends MapBuilder {
	public Object getProperty(String facetName) {
		def metaProperty = FacetMapBuilder.metaClass.getMetaProperty(facetName)
		if (metaProperty)
			return metaProperty.getProperty(this)
		def f = map[facetName]
		return f ? f : (map[facetName] = new Facet(facetName))
	}
}

class FilterMapBuilder extends MapBuilder {
	public Object getProperty(String filterName) {
		def metaProperty = FilterMapBuilder.metaClass.getMetaProperty(filterName)
		if (metaProperty)
			return metaProperty.getProperty(this)
		def f = map[filterName]
		return f ? f : (map[filterName] = new Filter(filterName))
	}
}
