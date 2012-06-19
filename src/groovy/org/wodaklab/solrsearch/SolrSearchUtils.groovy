package org.wodaklab.solrsearch

import grails.util.Environment

import org.codehaus.groovy.grails.commons.ConfigurationHolder


class SolrSearchUtils {
	private static ConfigObject config
	
	/**
	 * Config currently set in {@code SolrSearchGrailsPlugin} in 
	 * {@code doWithSpring}.
	 * 
	 * @param config The {@code SolrSearchConfig} configuration.
	 */
	static void setConfig(ConfigObject config) {
		this.config = config
	}
	
	/**
	 * Returns the SolrSearch configuration.
	 */
	static ConfigObject getConfig() {
		if (!config)
			reloadConfig()
		return config
	}
	
	static String queryToString(SolrQuery query) {
		def v = new QueryStringVisitor()
		query.accept(v)
		return v.queryString
	}
	
	private static final SIMPLE_TERM = /^[\w\._\*-]+$/
	
	static boolean isValidFieldName(String fieldName) {
		return fieldName =~ SIMPLE_TERM
	}
	
	static String escape(String s) {
		if (s =~ SIMPLE_TERM)
			return s
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
	}
	
	static String escape(Object obj) {
		return SolrSearchUtils.escape(obj.toString())
	}
	
	static Map asSolrParams(Search search) {
		def params = [:]
		
		if (search.q)
			params.q = escape(search.q)
		if (search.rows)
			params.rows = escape(search.rows)
		if (search.offset)
			params.start = escape(search.offset)
			
		search.filter.each { name, filter ->
		}
	}
	
	
	private static final Random rng = new Random(System.currentTimeMillis())
	
	
	/**
	 * Partitions {@code list} using the element at {@code pivot} 
	 * ({@code list[pivot]}). Specifically, this will move all elements less
	 * than {@code list[pivot]} to the left of it and all elements greater than
	 * or equal to {@code list[pivot]} right. This then returns the new position
	 * in {@code list} where the pivot element was moved to. The 2 arguments
	 * {@code l} and {@code r} give the lower- and upper-bound (inclusive) of
	 * {@code list} that will be partitioned. All other elements in {@code list}
	 * will be ignored.
	 * 
	 * @note The pivot MUST be between [l, r], otherwise the results are
	 * undefined.
	 * 
	 * @param list A list of items.
	 * @param pivot An index into list that points to the pivot element.
	 * @param l The lower-bound of list to constrain the parition to.
	 * @param r The upper-bound (inclusive) of list to constrain the partition to.
	 * @return The index into list of the pivot element's new position.
	 */
	private static def partition(list, pivot, l, r) {
		// I'm assuming this style of getting/setting multiple array elements is O(1) time.
		list[pivot,r] = list[r,pivot]
		while (l < r) {
			if (list[l] >= list[r]) {
				list[l,r-1,r] = list[r-1,r,l]
				r -= 1
			} else {
				l += 1
			}
		}
		return r
	}
	
	
	/**
	 * Selects the {@code k}-th largest element in the list, with {@code k=0} 
	 * being the smallest and {@code k=list.size() - 1} being the largest and
	 * returns it. More over, the list will be modified such that the element
	 * at position {@code k} ({@code list[k]}) is also the {@code k}-th largest
	 * element. It does this in expected linear time (vs. sorting, which
	 * requires O(n log n) time).
	 * 
	 * @param list A list to select the element from.
	 * @param k The rank of the element you wish to select.
	 * @return The k-th largest (starting at 0) item from list.
	 */
	static def select(list, k) {
		def l = 0, r = list.size() - 1, p = -1
		while (p != k && l < r) {
			p = partition(list, l + rng.nextInt(r - l + 1), l, r)
			if (p < k)
				l = p + 1
			else if (p > k)
				r = p - 1
		}
		return list[k]
	}
}
