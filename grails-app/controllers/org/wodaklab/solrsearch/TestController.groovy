package org.wodaklab.solrsearch

import grails.converters.JSON

class TestController {
	def solrSearchService
	
	def index = {
		def query = solrSearchService.search(params, "search")
		render query.result as JSON
	}
	
	def facets = {
		def search = solrSearchService.search(params, "search")
		return [search: search]
	}
	
	def counts = {
		def search = solrSearchService.search(params, "search")
		render search.result.facet_counts as JSON
	}
}
