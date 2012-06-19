package org.wodaklab.solrsearch

import groovy.xml.MarkupBuilder
import java.text.DecimalFormat;
import org.codehaus.groovy.grails.web.json.JSONObject

import org.springframework.web.servlet.support.RequestContextUtils as RCU


class SolrSearchTagLib {
	static namespace = "solrsearch"
	
	def solrSearchService
	def grailsApplication
	
	
	/**
	 * Renders a set of filters (checkboxes) based on a facet. There are 2
	 * required attributes for this tag: search and name. The search attribute
	 * must be set to an instance of {@code org.wodaklab.solrsearch.Search}.
	 * The name attribute should be the name (String) of a facet in the Search
	 * object.
	 * 
	 * Optional paramters include;
	 * <ul>
	 * 	<li>prefix: The prefix to give form elements (this will appended with a dot .);</li>
	 *  <li>title: The title to use as the legend of the fieldset rendered;</li>
	 *  <li>max: The maximum number of filters to display (lowest counts truncated);</li>
	 *  <li>op: The default operator to use (overridable by previous search's choice);</li>
	 *  <li>showops: If "false", then the operator selection won't be shown.</li>
	 * </ul>
	 * 
	 * The parameters given will also be searched for previously submitted form
	 * elements. Those that exist will be checked/selected automatically.
	 */
	def facet = { attrs, body ->
		Search search = attrs.search
		String facetName = attrs.name
		String id = attrs.id
		String domainClass = attrs.domainClass
		String code = attrs.code?: "solrsearch.facet.${facetName}"
		Facet f = search.query.facet[facetName]
		if (!f)
			throw new IllegalArgumentException("Invalid facet name given; no facet exists with name: $facetName")
		
		String prefix = (attrs.prefix ? (attrs.prefix + ".") : "") + "filter[$facetName]"
		String customFacetPrefix = (attrs.prefix ? attrs.prefix + "." : "") + "facet[${facetName}_custom]"
		
		String title = attrs.title?: ""
		
		def counts = solrSearchService.getFacetCounts(search, facetName)
		
		def max = attrs.max ? Integer.valueOf(attrs.max) : 0
		def cutoff = (max && counts.size() > max) ? SolrSearchUtils.select(counts.collect { it.count }, counts.size() - max) : null
		
		def prevValues = params.list("${prefix}.values")
		
		def defaultOp = attrs.op?.toLowerCase()?: "any"
		def showOps = attrs.showops ? Boolean.valueOf(attrs.showops) : true
		
		def showCustomRange = attrs.customrange ? Boolean.valueOf(attrs.customrange) : false
		def showMinMax = attrs.showminmax ? Boolean.valueOf(attrs.showminmax) : showCustomRange
		if (showMinMax && !search.result.stats.stats_fields[f.field]) {
			log.warn("No stats for field " + f.field + ", though required for facet '" + facetName + "'.")
			showMinMax = false
		}
		
		def include = null
		if (attrs.include) {
			def sb = new StringBuilder()
			attrs.include.eachWithIndex { s, notFirst ->
				if (notFirst)
					sb << "|"
				sb << "(" << s << ")"
			}
			include = sb.toString()
		}
		
		counts = counts.collect { it + [value: ValueSetEditor.getAsText(it.valueset)] }
		if (domainClass) {
			def dc = grailsApplication.getArtefact("Domain", domainClass)
			def objs = dc.clazz.getAll(counts.value)
			counts.eachWithIndex { cnt, i ->
				cnt.object = objs[i]
			}
		}
		
		def maxOnCutoff = (cutoff != null) ? counts.inject(max) { total, it -> it.count > cutoff ? (total - 1) : total } : 0 
		def filters = counts.collect {
			def hidden = (cutoff != null && ((it.count < cutoff) || (it.count == cutoff && maxOnCutoff <= 0))) ||
						 (include != null && !(it.value =~ include))
			if (!hidden && maxOnCutoff > 0 && it.count == cutoff)
				maxOnCutoff -= 1
			def ftr = [
				count: it.count,
				value: it.value,
				valueset: it.valueset,
				object: it.object,
				code: code,
				exists: it.value in prevValues,
				hidden: hidden,
				custom: it.custom
			]
			ftr.label = body(ftr)?: null
			return ftr
		}
		
		def model = [
				id: id,
				search: search,
				name: facetName,
				facet: f,
				prefix: prefix,
				customFacetPrefix: customFacetPrefix,
				title: title,
				filters: filters,
				showOps: showOps,
				customizable: true,	/// @todo Don't output if not customizable.
				ops: [
					[value: "any", label: "Can match ANY of these"],
					[value: "all", label: "Must match ALL of these"],
					[value: "none", label: "Must match NONE of these"]
				],
				defaultOp: defaultOp,
				selectedOp: params["${prefix}.op"]?: defaultOp,
				showCustomRange: showCustomRange,
				showMinMax: showMinMax,
				limited: f.limit > 0
			]
		out << render(plugin: "solr-search", template: "/solrsearch/facet", model: model)
	}
	
	def valueSetToMessage(ValueRange range, String baseCode, String defaultMessage) {
		def msg = g.message(code: "${baseCode}", default: defaultMessage, args: ["${range.from} - ${range.to}"])
		msg = g.message(code: "${baseCode}.range", default: msg, args: [range.from, range.to])
		if (range.from == '*' && range.to != '*')
			msg = g.message(code: "${baseCode}.lte", default: msg, args: [range.to])
		if (range.to == '*' && range.from != '*')
			msg = g.message(code: "${baseCode}.gte", default: msg, args: [range.from])
		if (range.alias()) {
			def alias = list.alias()
			msg = g.message(code: "${baseCode}.${alias}", default: msg, args: [range.from, range.to])
		}
		return msg
	}
	
	def valueSetToMessage(ValueList list, String baseCode, String defaultMessage) {
		def messages = []
		for (v in list.values) {
			def msg = g.message(code: "${baseCode}", default: defaultMessage, args: [v])
			msg = g.message(code: "${baseCode}.value", default: msg, args: [v])
			msg = g.message(code: "${baseCode}.value.${v}", default: msg, args: [v])
			messages << msg
		}
		def sep = g.message(code: "${baseCode}.separator", default: ", ")
		def msg = messages.join(sep)
		if (list.alias()) {
			def alias = list.alias()
			msg = g.message(code: "${baseCode}.${alias}", default: msg, args: list.values)
		}
		return msg
	}
	
	def valueSetToMessage(ValueSet valueset, String baseCode, String defaultMessage) {
		return defaultMessage
	}
	
	def valueset = { attrs ->
		def vals = attrs.valueset
		def code = attrs.code
		
		def defaultMessage = valueSetToMessage(vals, "solrsearch.valueset", "")
		def message = valueSetToMessage(vals, code, defaultMessage)
		
		out << message
	}
	
	private def titleExtractor = /%([^%]*)\/([^%]*)%/
	
	def sortlink = { attrs, body ->
		def prefix = attrs.prefix ? (attrs.prefix + ".") : ""
		def search = attrs.search
		def field = attrs.field
		def order = attrs.order?: (search.query.sort[0]?.field == field
			? (search.query.sort[0]?.order == 'asc' ? 'desc' : 'asc') 
			: 'asc')
		
		def title = attrs.title?: ""
		title = title.replaceAll(titleExtractor, { m, inc, dec -> order == 'asc' ? inc : dec })
		def extraClass = attrs.'class'?: ""
		
		def linkParams = params.findAll({ k,v -> k.startsWith(prefix) })
		linkParams[prefix + 'sort[0].field'] = field
		linkParams[prefix + 'sort[0].order'] = order
		
		def linkAttrs = [:]
		if (attrs.controller) linkAttrs.controller = attrs.controller
		if (attrs.action) linkAttrs.action = attrs.action
		if (attrs.fragment) linkAttrs.fragment = attrs.fragment
		linkAttrs.params = linkParams
		
		def link = createLink(linkAttrs)
		
		def sortClass = 'sortable'
		if (search.query.sort[0]?.field == field)
			sortClass += " sorted sorted" + (search.query.sort[0]?.order == "desc" ? "Desc" : "Asc")
		
		def model = [
				field: field,
				prefix: prefix,
				search: search,
				title: title,
				link: link,
				extraClass: extraClass,
				sortClass: sortClass,
				body: body
			]
		
		out << render(plugin: "solr-search", template: "/solrsearch/sortlink", model: model)
	}
	
	def paginate = { attrs ->
		def search = attrs.search
		def prefix = attrs.prefix? (attrs.prefix + ".") : ""
		def prev = attrs.prev?: "Prev"
		def next = attrs.next?: "Next"
		def extras = attrs.extra ? "^" + attrs.extra.split(/\s/).findAll { it }.join("|") + "\$" : null
		
		def max = attrs.maxsteps ? Integer.parseInt(attrs.maxsteps) : 5
		
		def total = search.result.response.numFound
		def offset = search.query.offset?: 0
		def pageSize = (int) search.query.rows
		def finalPage = (int) ((total - 1) / pageSize)
		
		def page = (int) (offset / pageSize)
		def firstPage = Math.max(page - ((int) (max / 2)), 0)
		def lastPage = Math.min(page + ((int) (max / 2)), finalPage)
		
		def pages = new IntRange(
				Math.max(Math.min(lastPage - max + 1, firstPage), 0),
				Math.min(Math.max(firstPage + max - 1, lastPage), finalPage)
			)
		
		def linkParams = params.findAll({ k,v ->
				k.startsWith(prefix) || (extras && k =~ extras)
			})
		
		def linkAttrs = [:]
		if (attrs.controller) linkAttrs.controller = attrs.controller
		if (attrs.action) linkAttrs.action = attrs.action
		if (attrs.fragment) linkAttrs.fragment = attrs.fragment
		linkAttrs.params = linkParams
		
		def pageLink = { pg ->
			linkParams["${prefix}offset"] = (pg * pageSize).toString()
			linkAttrs.params = linkParams	// Yes, this is needed. Lord knows why...
			return createLink(linkAttrs)
		}
		
		new MarkupBuilder(out).with {
			if (page > 0)
				a(href: pageLink(page - 1), 'class': "step") {
					mkp.yieldUnescaped prev
				}
			if (pages[0] != 0) {
				a(href: pageLink(0), 'class': "step", "1")
				if (pages[0] != 1)
					span('class': "step", "..")
			}
			
			pages.each {
				if (it == page)
					span('class': "currentStep", (it + 1).toString())
				else
					a(href: pageLink(it), 'class': "step", (it + 1).toString())
			}
			
			if (pages[-1] != finalPage) {
				if (pages[-1] < (finalPage - 1))
					span('class': "step", "..")
				a(href: pageLink(finalPage), 'class': "step", (finalPage + 1).toString())
			}
			
			if (page < finalPage)
				a(href: pageLink(page + 1), 'class': "step") {
					mkp.yieldUnescaped next
				}
		}
	}
	
	def summary = { attrs, body ->
		def search = attrs.search
		def total = search.result?.response?.numFound?: 0
		def start = (search.query.offset?: 0) + 1
		def end = Math.min(start + search.query.rows - 1, total)
		out << body([total: total, start: start, end: end])
	}
	
	def stats = { attrs, body ->
		def search = attrs.search
		def field = attrs.field
		
		def stats = search.result.stats.stats_fields[field] ?: [:]
		if (stats == JSONObject.NULL) stats = [:]
		
		out << body([
			min: stats.min?: 0,
			max: stats.max?: 0,
			mean: stats.mean?: 0,
			stddev: stats.stddev?: 0
		])
	}
	
	def fieldstats = { attrs, body ->
		def intFormat = new DecimalFormat("#######")
		def realFormat = attrs.format? new DecimalFormat(attrs.format) : new DecimalFormat("#.##")
		
		def search = attrs.search
		def field = attrs.field
		
		def stats = search.result.stats.stats_fields[field] ?: [:]
		if (stats == JSONObject.NULL) stats = [:]
		
		def dlClass = "stats"
		def dlStyle = ""
		if (attrs['class'])
			dlClass += " " + attrs['class']
		if (attrs.style)
			dlStyle = attrs.style
		
		def html = new MarkupBuilder(out)
		html.dl('class': dlClass, style: dlStyle) {
			dt('class': 'minLabel') { abbr(title: "Minimum", "Min") }
			dd('class': 'min', realFormat.format(stats.min?: 0))
			dt('class': 'maxLabel') { abbr(title: "Maximum", "Max") }
			dd('class': 'max', realFormat.format(stats?.max?: 0))
			dt('class': 'meanLabel', "Mean")
			dd('class': "mean approx", realFormat.format(stats?.mean?: 0))
			dt('class': 'stddevLabel') { abbr(title: "Standard deviation") { mkp.yieldUnescaped "&sigma;" } }
			dd('class': "stddev approx", realFormat.format(stats?.stddev?: 0))
		}
	}
}
