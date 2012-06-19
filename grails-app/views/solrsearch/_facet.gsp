<fieldset ${id ? 'id="' + id + '"' : ''} class="facet ${facet.simple ? 'simple-facet' : ''} filterGroup filterGroup-${name}${showCustomRange ? ' provides-custom-range' : ''}">
	<g:if test="${title}">
		<legend>
			${title}
			<g:if test="${showMinMax}">
				<solrsearch:stats search="${search}" field="${facet.field}">
					<span class="range"><span class="min"><g:formatNumber number="${min}" format="#" /></span> - <span class="max"><g:formatNumber number="${max}" format="#" /></span></span>
				</solrsearch:stats>
			</g:if>
		</legend>
	</g:if>
	<div class="facetFields">
		<input class="facet-field ${facet.field == name ? 'removable' : ''}" type="hidden" name="${prefix}.field" value="${facet.field}" />
		<g:if test="${customizable}">
			<input class="custom-facet-field ${facet.field == name ? 'not-removable-for-some-reason' : ''}" type="hidden" name="${customFacetPrefix}.field" value="${facet.field}" />
		</g:if>
		<div class="facet-filters">
			<g:each var="filter" in="${filters}">
				<div class="filter${(filter.hidden && !filter.exists) ? ' hidden' : ''}">
					<g:if test="${filter.custom}">
						<input type="hidden" name="${customFacetPrefix}.values" value="${filter.value}" />
					</g:if>
					<label>
						<input type="checkbox" name="${prefix}.values" value='${filter.value}' ${filter.exists ? 'checked="checked"' : "" } />
						<span>
							<g:if test="${filter.label}">
								${filter.label}
							</g:if>
							<g:else>
								<solrsearch:valueset code="${filter.code}" valueset="${filter.valueset}" />
							</g:else>
						</span>
					</label>
					<span class="count">${filter.count}</span>
				</div>
			</g:each>
		</div>
		<g:if test="${showOps}">
			<div class="operation">
				<select name="${prefix}.op" ${selectedOp == defaultOp && defaultOp == "any" ? ' class="submit-on-change"' : ''}>
					<g:each var="op" in="${ops}">
						<option value="${op.value}" ${op.value == selectedOp ? 'selected="selected"' : ""}>${op.label}</option>
					</g:each>
				</select>
			</div>
		</g:if>
		<g:elseif test="${selectedOp != 'any'}">
			<input type="hidden" name="${prefix}.op" value="${selectedOp}" />
		</g:elseif>
	</div>
</fieldset>
