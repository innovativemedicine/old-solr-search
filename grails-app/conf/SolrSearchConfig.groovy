import org.wodaklab.solrsearch.SolrQuery;

import org.wodaklab.solrsearch.SolrQuery
import org.wodaklab.solrsearch.Filter
import org.wodaklab.solrsearch.ValueSet
import org.wodaklab.solrsearch.ValueList

solrSearch {
	queries = [
		'default':  new SolrQuery().with {
			q = "*"
			queryType = "dismax"
			rows = 10
			
			fields {
				gene_id
				score
			}
			
			filter {
				eType.matches "gene"
			}
			
			facet {
				gene_status
				gene_tax
				evidence_desc
				ortholog_tax_name
				ortho_tax_unq_count.split 0..0, 1..1, 2..9, 10
				pdb_count.split 0..0, 1..1, 2..9, 10
				crm_count.split 0..0, 1..1, 2..9, 10
				domain_count.split 0..0, 1..1, 2..9, 10
				complex_count.split 0..0, 1..1, 2..9, 10
				disease_count.split 0..0, 1..1, 2..9, 10
				homo_count {
					inList([0,1])
				}
				para_count.split 0..0, 1..1, 2..9, 10
				ortho_count.split 0..0, 1..1, 2..9, 10
				rog_count.split 0..0, 1..1, 2..9, 10
				neighbour_count.split 0..0, 1..1, 2..9, 10
				neighbour_disease_count.split 0..0, 1..1, 2..9, 10
				confirmed_count.split 0..0, 1..1, 2..9, 10
				putative_count.split 0..0, 1..1, 2..9, 10
			}
			
			stats {
				homo_count
				disease_count
				neighbour_count
				neighbour_disease_count
				para_count
				ortho_count
				crm_count
				confirmed_count
				putative_count
			}
			
			return delegate
		}
	]
	
	url {
		
		// The "select" URL for the solr server.
		select = "http://wodaklab.org/dancerSolr/select"
		
		// Maximum URL size. If a GET request will result in an URL larger than
		// this, then it will be POSTed instead. Use 0 for no limit.
		limit = 8191
	}
}
