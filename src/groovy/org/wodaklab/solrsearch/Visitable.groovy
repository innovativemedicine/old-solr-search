package org.wodaklab.solrsearch

interface Visitable {
	void accept(SolrQueryVisitor visitor)
}
