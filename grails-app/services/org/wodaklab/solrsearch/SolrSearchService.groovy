package org.wodaklab.solrsearch


import javax.servlet.ServletRequest

import org.springframework.beans.PropertyValues

import org.codehaus.groovy.grails.web.binding.GrailsDataBinder
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.springframework.web.bind.ServletRequestParameterPropertyValues

import grails.converters.*


/**
 * The {@code SolrSearchService} provides methods to query a Solr instance.
 * The benefit of using it over a plain old HTTP request is really in the
 * helper classes, such as {@code SolrQuery}, which provide a rich DSL to define
 * queries, a simple configuration file, and its ability to develop a Solr
 * query from simple form fields.
 */
class SolrSearchService {
	static transactional = false
	
	
	/**
	 * Creates and returns a copy of a named SolrQuery from the configuration.
	 * 
	 * @param name The name of the solr query to copy.
	 */
	SolrQuery createQuery(String name="default") {
		SolrQuery query = new SolrQuery()
		query << SolrSearchUtils.config.solrSearch.queries."$name"
		return query
	}
	
	
	/**
	 * Creates and returns a {@code Search} instance using the given properties.
	 * 
	 * @param props A GrailsParameterMap, Map, or PropertyValues instance.
	 * @return An instance of {@code Search} with the given properties.
	 */
	SolrQuery createQuery(String baseQuery="default", props) {
		return createQuery(baseQuery, props, null)
	}
	
	
	/**
	 * Creates and returns a {@code Search} instance using the given properties.
	 * The properties used must all have the prefix {@code prefix}.
	 *
	 * @param props A GrailsParameterMap, Map, or PropertyValues instance.
	 * @param prefix A prefix (trailing dot [.] assumed) for the properties.
	 * @return An instance of {@code Search} with the given properties.
	 */
	SolrQuery createQuery(String baseQuery="default", props, prefix) {
		SolrQuery query = new SolrQuery()
		def binder = new GrailsDataBinder(query, prefix)
		binder.bind(props, prefix)
		return createQuery(baseQuery) << query
	}
	
	SolrQuery createQuery(SolrQuery baseQuery, props) { return createQuery(baseQuery, props, null) }
	SolrQuery createQuery(SolrQuery baseQuery, props, prefix) {
		def query = new SolrQuery()
		def binder = new GrailsDataBinder(query, prefix)
		binder.bind(props, prefix)
		return new SolrQuery() << baseQuery << query
	}
	
	
	/**
	 * Searches Solr using the parameters from a form to determine the Solr
	 * query parameters.
	 * 
	 * @param params A map of the parameters given in the request.
	 * @returnA {@code JSONObject}
	 */
	Object search(String baseQuery="default", GrailsParameterMap params) {
		return search(createQuery(baseQuery, params))
	}
	
	
	Object search(String baseQuery="default", GrailsParameterMap params, Map storage) {
		return search(createQuery(baseQuery, params), storage)
	}
	
	
	/**
	 * Searches Solr using the parameters from a form to determine the Solr
	 * query parameters. The prefix is the prefix given for field names in the
	 * form which define Solr query parameters. An implied '.' (dot) is appended
	 * to the prefix (a prefix of 'search' would look for 'search.q', eg). 
	 * 
	 * @param params A map of the parameters given in the request.
	 * @param prefix The prefix of the parameters that determine the query.
	 * @return A {@code JSONObject}
	 */
	Object search(String baseQuery="default", GrailsParameterMap params, String prefix, Map storage) {
		return search(createQuery(baseQuery, params, prefix), storage)
	}
	
	
	Object search(String baseQuery="default", GrailsParameterMap params, String prefix) {
		return search(baseQuery, params, prefix, [:])
	}
	
	
	Object search(String baseQuery="default", ServletRequest request) { return search(baseQuery, request, "", null) }
	Object search(String baseQuery="default", ServletRequest request, String prefix) { return search(baseQuery, request, prefix, null) }
	Object search(String baseQuery="default", ServletRequest request, String prefix, Map storage) {
		ServletRequestParameterPropertyValues props
		if (prefix) {
			props = new ServletRequestParameterPropertyValues(request, prefix, ".")
		} else {
			props = new ServletRequestParameterPropertyValues(request)
		}
		
		return search(createQuery(baseQuery, props), storage)
	}
	
	Object search(SolrQuery baseQuery, ServletRequest request) { return search(baseQuery, request, "", null) }
	Object search(SolrQuery baseQuery, ServletRequest request, String prefix) { return search(baseQuery, request, prefix, null) }
	Object search(SolrQuery baseQuery, ServletRequest request, String prefix, Map storage) {
		ServletRequestParameterPropertyValues props
		if (prefix) {
			props = new ServletRequestParameterPropertyValues(request, prefix, ".")
		} else {
			props = new ServletRequestParameterPropertyValues(request)
		}
		
		return search(createQuery(baseQuery, props), storage)
	}
	
	
	Object search(SolrQuery query) {
		return search(query, [:])
	}
	
	
	/**
	 * Performs the actual Solr search, using the given query. The return
	 * value is simply a parsed version of the JSON response from Solr.
	 * 
	 * @param query An instance of {@code SolrQuery}, defining the query params.
	 * @return A {@code JSONObject}, as returned by Solr
	 */
	Object search(SolrQuery query, Map storage) {
		def v = new QueryStringVisitor(storage)
		query.accept(v)
		def queryString = v.queryString
		def base = query.selectUrl ?: SolrSearchUtils.config.solrSearch.url.select
		def getLimit = SolrSearchUtils.config.solrSearch.url.limit?: 0
		def data
		
		
		log.error("\nQuerying Solr with:\n$base?$queryString\n")
		
		if (!getLimit || base.size() + queryString.size() + 1 < getLimit) {
			// GET Request
			def url = new URL("$base?$queryString")
			data = url.openConnection().with {
				requestMethod = "GET"
				doOutput = true
				connect()
				if (responseCode != 200 && responseCode != 201) {
					log.error("GET from Solr failed: $responseCode\nQuery: $queryString\nResponse: $responseMessage")
					throw new RuntimeException("Solr Query failed.")
				}
				return content.text
			}
			
		} else {
			// POST Request
			def url = new URL(base)
			data = url.openConnection().with {
				requestMethod = "POST"
				doOutput = true
				outputStream.withWriter { w -> w.write(queryString) }
				connect()
				if (responseCode != 200 && responseCode != 201) {
					log.error("POST to Solr failed: $responseCode\nQuery: $queryString\nResponse: $responseMessage")
					throw new RuntimeException("Solr Query failed.")
				}
				return content.text
			}
		}
		
		def reply = JSON.parse(data)
		return new Search(query: query, result: reply)
	}
	
	
	/**
	 * Returns the a list of maps that represent the counts for each ValueSet
	 * for the facet with name {@code facetName}.
	 * 
	 * @param search
	 * @param facetName
	 * @return
	 */
	def getFacetCounts(Search search, String facetName) {
		def qsVisitor = new QueryStringVisitor()
		def facet = search.query.facet[facetName]
		def custom = search.query.facet["${facetName}_custom"]
		
		def counts = []
		if (facet.isSimple()) {
			def seenValueSets = [] as Set
			def enumCounts = search.result.facet_counts.facet_fields[facet.field]
			for (int i = 0; i < enumCounts.size() - 1; i += 2) {
				def valueset = new ValueList([enumCounts[i]])
				counts << [valueset: valueset, count: enumCounts[i+1]]
				seenValueSets << valueset
			}
			
			custom.values.findAll({ !(it in seenValueSets) }).each {
				def query = facet.field + ":" + qsVisitor.getValue(it)
				def count = search.result.facet_counts.facet_queries[query]
				counts << [ valueset: it, count: count, custom: true ]
			}
			
		} else {
			facet.values.each {
				def query = facet.field + ":" + qsVisitor.getValue(it)
				def count = search.result.facet_counts.facet_queries[query]
				counts << [ valueset: it, count: count, custom: false ]
			}
			
			custom.values.each {
				def query = facet.field + ":" + qsVisitor.getValue(it)
				def count = search.result.facet_counts.facet_queries[query]
				counts << [ valueset: it, count: count, custom: true ]
			}
		}
		return counts
	}
	
	def asHumanReadableText(ValueList v) {
		return v.values.join(", ")
	}
	
	def asHumanReadableText(ValueRange r) {
		if (r.from == r.to) {
			if (r.from == "*")
				return "Everything"
			return r.from
		} else if (r.from == "*") {
			return r.to + " or less"
		} else if (r.to == "*") {
			return r.from + " or more"
		}
		return "Between " + r.from + " and " + r.to
	}
}
