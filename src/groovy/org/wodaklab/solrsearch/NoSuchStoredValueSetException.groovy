package org.wodaklab.solrsearch

class NoSuchStoredValueSetException extends SolrSearchException {
	String key
	
	NoSuchStoredValueSetException(key) {
		this.key = key
	}
	
	def getKey = { key }
}
