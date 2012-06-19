<html>
<head></head>
<link rel="stylesheet" type="text/css" href="${resource(dir: "css", file: "solrsearch.css")}" />
<script src="http://ajax.googleapis.com/ajax/libs/jquery/1.4.2/jquery.min.js"></script>
<script src="${resource(dir: "js", file: "jquery.color.js")}"></script>
<script src="${resource(dir: "js", file: "solrsearch.js")}"></script> 
<body>
<g:form controller="test" action="facets" method="GET">
	<label>Search: <input name="search.q" /></label>
	<label>Gene ID(s):<input name="search.filter[gene_list].values" /></label>
	<input type="hidden" name="search.filter[gene_list].field" value="gene_id" />
	<label><input type="checkbox" name="search.filter[gene_list].field" value="homolog_gene_id" /> Include Homologs</label>
	<label><input type="checkbox" name="search.filter[gene_list].field" value="partner_gene_id" /> Include Neighbours</label>
	<solrsearch:facet id="gene_tax" search="${search}" name="gene_tax" title="Taxonomy" prefix="search" include="['Homo sapiens','Mus musculus']" />
	<solrsearch:facet search="${search}" name="homo_count" title="Number of Homologs" prefix="search" customrange="true" />
	<solrsearch:facet search="${search}" name="neighbour_count" title="Number of Neighbours" prefix="search" customrange="true" />
	<input type="submit" value="Search" />
</g:form>
<ul>
	<g:each var="doc" in="${search.result.response.docs}">
		<li>${doc.gene_id}</li>
	</g:each>
</ul>
</body>
</html>