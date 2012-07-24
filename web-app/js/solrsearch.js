function addCommas(nStr)
{
  nStr += '';
  x = nStr.split('.');
  x1 = x[0];
  x2 = x.length > 1 ? '.' + x[1] : '';
  var rgx = /(\d+)(\d{3})/;
  while (rgx.test(x1)) {
    x1 = x1.replace(rgx, '$1' + ',' + '$2');
  }
  return x1 + x2;
}

(function(window, $, undefined) {

if (!$) {
	throw new Error("solrsearch.js: Cannot find jQuery (expected in variable 'jQuery').");
}

/// TODO: Put into wodaklookandfeel.
$.fn.sortBy = function (keyFunc) {
	var keys = [];
	this.each(function() { keys.push({k: keyFunc(this), v: this}); });
	keys.sort(function(a, b) {
		return a.k == b.k ? 0 : (a.k < b.k ? -1 : 1);
	});
	
	this.length = 0;
	for (var i = 0, len = keys.length; i < len; i++) {
		this.push(keys[i].v);
	}
	return this;
};


var solrsearch = window.solrsearch = window.solrsearch || {},
	isInt = /^-?\d+$/,
	bind = function(f, that) {
		return f.bind ? f.bind(that) : function() { return f.apply(that, arguments) }
	},
	reverse = function(s) { return s.split("").reverse().join("") },
	slice = Array.prototype.slice,
	min = function(list) { return Math.min.apply(Math, list) },
	max = function(list) { return Math.max.apply(Math, list) };

var pause = solrsearch.pause = function(f, wait, pipe) {
		var t = null;
		return function() {
			var self = this,
				args = arguments,
				def = new $.Deferred();
			
			clearTimeout(t);
			t = setTimeout(function() {
				t = null;
				var result = f.apply(self, args);
				if (pipe) {
					result.then(function() {
						def.resolveWith(this, arguments);
					}, function() {
						def.rejectWith(this, arguments);
					});
				} else {
					def.resolve(result);
				}
			}, wait);
			
			return def.promise();
		}
	};


var escape = solrsearch.escape = function(w) {
	if (/^[\w\._\*-]+$/.test(w) || /^\[(\*|\d+)\s+TO\s+(\*|\d+)\]$/.test(w))
		return w;
	return "\"" + w.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
};
	

/**
 * This briefly highlights (for the user's benefit) the given jQuery elements
 * passed in. The function also takes an optional colour "hint" as a 2nd
 * argument. This is only used as a hint to the highlight function about what
 * colour the elements should be highlighted, but does not actually guarantee
 * the colour will be used.
 * 
 * @function highlight
 * @param f
 *            The jQuery instance.
 * @param hintColor
 *            A CSS2 colour as a string (eg. "rgb(100,22,253)").
 */
var highlight = solrsearch.highlight = (function() {
	var highlighted = [];
	
	return function(f, hintColor) {
		f = f instanceof $ ? f : $(f);
		hintColor = hintColor || "yellow";
		if ($.fx.step.backgroundColor) {
			if (highlighted.indexOf(f) >= 0)
				return f;
			
			highlighted.push(f);
			var oldBgColor = f.css("background-color");
			f.css({opacity: 0, backgroundColor: hintColor})
				.animate({opacity: 1}, 300, function() {
					f.animate({backgroundColor: oldBgColor}, 700, function() {
						highlighted.splice(highlighted.indexOf(f), 1);
					});
				});
		} else {
			f.css("opacity", 0).animate({opacity: 1}, 700);
		}
		return f;
	};
})();


function property(opts) {
	var val = opts.val,
		getter = (typeof val == "undefined" ? opts.get : function() { return val }) || function() {};
		setter = opts.set || function() { throw new Error("Cannot write to read-only property.") };
	
	return function() {
		if (arguments.length == 0)
			return getter.call(this);
		setter.apply(this, arguments);
		return this;
	};
};



/**
 * Constructs a new facet.
 */
var Facet = solrsearch.Facet = function(facet) {
	facet = $(facet);
	
	var self = this,
		facetField = facet.find(".facet-field"),
		customFacetField = facet.find(".custom-facet-field"),
		type = facet.hasClass("provides-custom-range") ? "range" : "simple";
	
	this.facet = property({ val: facet });
	this.type = property( { val: type });
	this.customizable = property({ val: customFacetField.length > 0 });
	this.fieldName = property({ val: facetField.val() });
	this.prefix = property({ val: reverse(reverse(facetField.attr("name")).replace(/^.*[\[\.](?=[^\[\.]*$)/, "")) });
	this.filterPrefix = property({ val: facetField.attr("name").slice(0, -6) });
	this.facetQueryPrefix = property({
		val: (customFacetField.attr("name") || "")
				.slice(0, -6)
				.replace(/(?=.*\.)filter(?=\[.*\])/, "facet")
	});
	
	// Mostly, this cleans up spurious hidden fields and the like.
	
	facet.find(".removable").remove();	// Uncommenting this causes search
										// filter count updates to fail.
	facet.find(":input.submit-on-change").each(function() {
		var input = $(this),
			name = input.attr("name"),
			value = input.val();
		input.removeAttr("name");
		
		input.change(function() {
			var curValue = input.val(),
				curName = input.attr("name");
			
			if (!curName && value != curValue) {
				input.attr("name", name);
			} else if (curName && value == curValue) {
				input.removeAttr("name");
				self.updateCounts();	// Counts won't be updated for inputs
										// with no names!
			}
		});
	});
};


Facet.prototype.extraFormFields = function() {
	var self = this,
		inputs = $();
	
	if (this.type() == "simple") {
		this.filters().each(function() {
			var facetQueryName = self.facetQueryPrefix() + ".values",
				facetQuery = $(":input", this).filter(function() { return $(this).attr("name") == facetQueryName });
			if (!facetQuery.length) {
				var filterQueryName = self.filterPrefix() + ".values",
					filterQuery = $(":input", this).filter(function() { return $(this).attr("name") == filterQueryName });
				facetQuery = $("<input name='" + facetQueryName + "' value='" + filterQuery.val() + "' type='hidden' />");
				inputs = inputs.add(facetQuery);
			}
		});
	}
	
	return inputs;
};


Facet.prototype.min = function() {
	var min = parseInt(this.facet().find(".range .min").text());
	return isFinite(min) ? min : undefined;
};

Facet.prototype.max = function() {
	var max = parseInt(this.facet().find(".range .max").text());
	return isFinite(max) ? max : undefined;
};

Facet.prototype.createFilter = function(value, label, count) {
	return $("<div class='filter' />")
		.append("<input type='hidden' name='" + this.facetQueryPrefix() + ".values' value='" + escape(value) + "' />")
		.append($("<label />")
			.append("<input type='checkbox' name='" + this.filterPrefix() + ".values' value='" + escape(value) + "' />")
			.append(" ")
			.append($("<span />").append(label))
		)
		.append(" <span class='count'>" + (count === undefined ? "" : count) + "</span>");
};

Facet.prototype.append = function(html) {
	this.facet().find(".facet-filters").after(html);
	return this;
};

Facet.prototype.filters = function() { return this.facet().find(".facet-filters .filter") };

Facet.prototype.addFilter = function(value, label, count) {
	if (arguments.length > 1)
		value = this.createFilter(value, label, count);
	else
		value = $(value);
	this.filters().last().after(value);
	highlight(value);
};

Facet.prototype.addMenu = function(menu) {
	this.append(menu.container());
};

Facet.prototype.updateCounts = function() {
	this.facet().find("input[name!='']:first").change();
};



/**
 * This is a facet that behaves almost like another facet, except that it
 * presents a different fieldName.
 */
var FakeFacet = solrsearch.FakeFacet = function(facet, fieldName) {
	var prefix = facet.prefix(),
		facetQueryPrefix = facet.facetQueryPrefix(),
		filterPrefix = facet.filterPrefix();
	
	this.facet = bind(facet.facet, facet);
	this.type = function() { return "simple" };
	this.customizable = function() { return false };
	this.fieldName = function() { return fieldName };
	this.prefix = bind(facet.prefix, facet);
	this.facetQueryPrefix = bind(facet.facetQueryPrefix, facet);
	this.filterPrefix = bind(facet.filterPrefix, facet);
};

FakeFacet.prototype = Facet.prototype;


/**
 * Constructs a menu for range-type facets. This can be used to simply create
 * new custom range queries.
 */
var RangeMenu = solrsearch.RangeMenu = function(facet, container) {
	this.facet = property({ val: facet });
	
	var menu = $("<fieldset class='custom-range-menu'></fieldset>"),
		fromField = $("<input type='text' size='6' />"),
		toField = $("<input type='text' size='6' />"),
		showLink = $("<a class='create-filter' href='#'>&nbsp;Create Custom Range</a>").click(bind(function(e) {
				e.preventDefault();
				this.show();
				return false;
			}, this)),
		addLink = $("<a class='add-filter' href='#'><span>Add</span></a>").click(bind(function(e) {
				e.preventDefault();
				this.addFilter(true);
				return false;
			}, this)),
		clearLink = $("<a class='remove-filter' href='#'><span>Cancel</span></a>").click(bind(function(e) {
				e.preventDefault();
				this.hide();
				return false;
			}, this));
	
	menu.append($("<label />")
			.append("<input type='checkbox' disabled='disabled' />")
			.append(" Between ").append(fromField).append(" and ").append(toField)
		)
		.append(addLink)
		.append(clearLink)
		.click(function(e) { e.stopPropagation() })
		.hide();

	$("body").click(bind(function() { if (menu.is(":visible")) this.addFilter() }, this));
	$().add(fromField).add(toField).keydown(bind(function(e) {
		if (e.keyCode == 13) {
			e.preventDefault();
			this.addFilter(true);
		}
	}, this));
	
	this.menu = property({ val: menu });
	this.fromField = property({ val: fromField });
	this.toField = property({ val: toField });
	this.hide = function() {
		menu.hide()
			.find("input[type=text]").val("").end()
			.find(".error").remove().end();
		showLink.show();
		return this;
	};
	this.show = function() {
		menu.show();
		showLink.hide();
		this.fromField().focus();
		return this;
	};
	
	this.container = property({ val: $("<div class='custom-range-menu-container' />").append(showLink).append(menu) });
};

RangeMenu.prototype.from = function() { return $.trim(this.fromField().val()) };
RangeMenu.prototype.to = function() { return $.trim(this.toField().val()) };

RangeMenu.prototype.validate = function() {
	var errors = [];
	$.each([ this.fromField(), this.toField() ], function(i, f) {
		if (!isInt.test($.trim(f.val())))
			errors.push({ field: f, error: "Values must be numbers." });
	});
	return errors.length ? errors : undefined;
};

RangeMenu.prototype.error = function(message) {
	return this.menu()
		.append("<em class='error'>" + message + "</em>")
		.find(".error:not(:last)")
			.remove();
};

RangeMenu.prototype.addFilter = function(refocus) {
	var errors = this.validate(),
		from = parseInt(this.from()),
		to = parseInt(this.to()),
		tmp;
	
	if (errors) {
		if (this.error(errors[0].error).length == 0 && refocus)
			highlight(this.menu(), "#f55");
		errors[0].field.focus().select();
		return false;
	}
	
	if (to < from) {
		tmp = to; to = from; from = tmp;	// Swap from/to.
	}
	
	var filter = this.facet().createFilter(
			"[" + from + " TO " + to + "]",
			"Between " + from + " and " + to,
			"?"
		);
	this.facet().addFilter(filter);
	filter.find(":input[type=checkbox]").attr("checked", true).change();
	
	this.hide();
	return true;
};


/**
 * This provides a dataset for a set of filters. The search is implemented
 * simply by comparing the term ('q' field) with each of the filter's label.
 */
var HiddenFilterDataSource = solrsearch.HiddenFilterDataSource = function(facet) {
	facet = facet instanceof Facet ? facet : $(facet).facet();
	
	var filters = facet.facet().find(".filter.hidden").remove().removeClass("hidden");
	this.filters = property({ val: filters });
};

HiddenFilterDataSource.prototype.search = function(query) {
	var term = $.trim(query.q).toLowerCase(),
		matches = this.filters().filter(function() {
				return $("label", this).text().toLowerCase().indexOf(term) >= 0;
			});
	
	return new $.Deferred().resolve({
			next: function(success, fail) {
				var def = new $.Deferred();
				if (!matches)
					def.reject();
				else
					def.resolve(matches);
				matches = null;
				return def.promise().then(success, fail);
			}
		}).promise();
};


var PagedFacetDataSource = solrsearch.PagedFacetDataSource = function(facet, labelMaker, form) {
	facet = facet instanceof Facet ? facet : $(facet).facet();
	labelMaker = labelMaker || function(vals) { return new $.Deferred().resolve(vals).promise() };
	
	form = form || facet.facet().closest("form");
	
	var limit = 40,
		prefix = facet.prefix() + ".facet[" + facet.fieldName() + "].";
	
	this.search = function(query) {
		var term = $.trim(query.q),
			params = {},
			offset = 0;
		
		params[prefix + "field"] = facet.fieldName();
		params[prefix + "limit"] = limit;
		if (term)
			params[prefix + "prefix"] = term;
		
		return new $.Deferred().resolve({
			next: function(s, f) {
				var def = new $.Deferred();
			
				params[prefix + "offset"] = offset;
				offset += limit;
				
				form.solrSearch(params).then(function(result) {
					var faceting = result.facet_counts.facet_fields[facet.fieldName()],
						values = [],
						counts = [],
						filters = $(),
						i, len;
					if (faceting.length) {
						for (i = 0, len = faceting.length; i < len; i += 2) {
							values.push(faceting[i]);
							counts.push(faceting[i + 1]);
						}
						labelMaker(values).then(function(labels) {
							for (i = 0, len = values.length; i < len; i++)
								filters = filters.add(facet.createFilter(values[i], labels[i], counts[i]));
							def.resolve(filters);
						}, function() {
							def.reject();
						});
					} else {
						def.resolve();
					}
					
				}, function() {
					def.reject();
				});
				
				return def.promise().then(s, f);
			}
		}).promise();
	};
};


/**
 * Wraps another datasource that returns filters. This is will automatically add
 * handlers to the filters to insert them into a facet when they are selected.
 * It will also filter out all filters from the search result that already exist
 * in the facet.
 * 
 * @param datasource
 *            A solrsearch.DataSource with a search method.
 * @param facet
 *            An solrsearch.Facet instance.
 */
var FilterDataSource = solrsearch.FilterDataSource = function(datasource, facet) {
	this.datasource = property({ val: datasource });
	this.facet = property({ val: facet });
};

FilterDataSource.prototype.search = function(query) {
	var self = this;
	return self.datasource().search(query).pipe(function(cursor) {
		return {
			next: function(s, f) {
				return cursor.next().pipe(function(filters) {
					if (filters) {
						var values = {};
						self.facet().filters().find("input[type=checkbox]").each(function() {
							values[this.value] = 1;
						});
						
						return filters.filter(".filter")
							.filter(function() {
								return !values[$("input[type=checkbox]", this).val()];
							})
							.one("change", function() {
								self.facet().addFilter(this);
							});
					}
				})
				.then(s, f);
			}
		};
	});
};


var MappedDataSource = solrsearch.MappedDataSource = function(datasource, map) {
	this.map = map;
	this.datasource = property({ val: datasource });
};

MappedDataSource.prototype.search = function(query) {
	var self = this;
	
	return self.datasource().search(query).pipe(function(cursor) {
		return {
			next: function(s, f) {
				return cursor.next().pipe(function(filters) {
					if (filters)
						return self.map(filters); 
				})
				.then(s, f);
			}
		};
	});
};


/**
 * This will mix several filter datasources together, sorting them in decreasing
 * order by their counts.
 */
var MixedDataSource = solrsearch.MixedDataSource = function(datasources) {
	var self = this;
	
	this.search = function(query) {
		var searches = $.map(datasources, function(ds) { return ds.search(query) });
		
		return $.when.apply($, searches).pipe(function() {
			var cursors = slice.call(arguments),
				buffers = [];
			$.each(cursors, function(i) { buffers[i] = [] });
			
			return {
				next: function(s, f) {
					var i = 0, len = buffers.length, j,
						def = $.Deferred(),
						batch = [];
					
					for (; i < len; i++) (function(buffer, cursor, i) {
						console.log(buffer, cursor, i);
						if (buffer.length == 0 && cursor)
							batch.push(cursor.next(function(filters) {
								if (filters) {
									filters.each(function() { buffer.push($(this)) });
								} else {
									cursors[i] = undefined;
								}
							}));
					})(buffers[i], cursors[i], i);
					
					if (batch.length == 0) {
						def.resolve();
					} else {
						$.when.apply($, batch).then(function() {
							var minCount = max($.map(buffers, function(buffer) {
								min($.map(buffer, function(f) {
									return parseInt(f.find(".count").text());
								}));
							}));
							
							var filters = $();
							for (i = 0; i < len; i++) {
								var newbuf = [], count;
								$.each(buffers[i], function() {
									count = parseInt(this.find(".count").text());
									
									if (count < minCount)
										newbuf.push(this);
									else
										filters.push(this.get(0));
								});
								buffers[i] = newbuf;
							}
							
							def.resolve(filters.sortBy(function(f) {
								return -parseInt($(f).find(".count").text());
							}));
						}, function() {
							def.reject();
						});
					}
					
					return def.promise();
				}
			};
		});
	};
};


/**
 * A menu that can be used to search and page through a datasource. Examples of
 * datasources include hidden filters, or searching DAnCER for entities.
 */
var SearchMenu = solrsearch.SearchMenu = function(datasource, refreshDelay) {
	var self = this,
		showLink = $("<a class='show-hidden' href='#'>More filters hidden</a>")
			.click(function(e) {
				e.preventDefault();
				self.show();
			}),
		fields = $("<div class='fields' />"),
		search = $("<input type='search' placeholder='Search for Filters' class='search' />"),
		pager = $("<div class='results' />"),
		results = $("<fieldset class='facet-search-menu' />")
			.append(fields.append(search))
			.append(pager)
			.css({
				position: "absolute",
				zIndex: 2,
				top: showLink.parent().height()
			})
			.hide(),
		removedPager;

	fields.delegate(":input", "change keyup click", function() {
		self.refresh();
	});
	
	self.fields = property({ val: fields });
	
	self.datasource = function(ds) {
		if (arguments.length) {
			datasource = ds;
			return this;
		}
		return datasource;
	};
	self.pager = property({ val: pager });
	
	self.show = function() {
		results.show();
		self.refresh();
	};
	self.hide = function() {
		results.hide();
		pager.empty();
	};
	self.container = property({
		val: $("<div style='position: relative' />")
			.append(showLink)
			.append(results)
			.click(function(e) { e.stopPropagation() })
	});
	
	self.refresh = (function() {
		var lastQuery;
		
		return pause(function() {
			var query = self.query(),
				def;
			
			if (self.queryChanged(lastQuery, query) || self.pager().children().length == 0) {
				self.container().addClass("waiting");
				self.pager().empty();
				
				def = self.datasource().search(this.query())
					.then(function(cursor) {
						$(self.pager()).empty().pager(cursor);
					})
					.always(function() { self.container().removeClass("waiting") })
					.pipe(function() {
						return self;
					});
			} else {
				def = new $.Deferred().resolve(self).promise();
			};
			
			lastQuery = query;
			return def;
		}, refreshDelay || 600, true);
	})();
	
	$("body").click(function(e) { self.hide(); });
};

SearchMenu.prototype.query = function() {
	return {
		q: this.container().find("input.search").val()
	}
};

/**
 * Returns true if the query b has 'changed' from a. Otherwise returns false.
 * This is used to prevent frivolous refreshes.
 */
SearchMenu.prototype.queryChanged = function(a, b) {
	var equal = a == b || (a !== undefined && b !== undefined),
		p;
	if (equal)
		for (p in a)
			equal = equal && a[p] == b[p];
	if (equal)
		for (p in b)
			equal = equal && a[p] == b[p];
	return !equal;
};


/**
 * Constructs a new Pager. A Pager is useful for datasources that don't load all
 * their content up front. Instead of taking a single lump of HTML and loading
 * it, it takes a 'cursor' that will has one method: 'next'. This method should
 * always return a promise that'll resolve to the next chunk of HTML to load up.
 * When none are left, the promise should resolve to false (or equivalent).
 * 
 * This works by creating a "witness" at the bottom of the pager's container. As
 * long as this witness is visible to the user, the pager will continuously load
 * the next chunk of HTML (after the previous chunk has been resolved) and
 * append it to the pager. This creates an effect like on Twitter, where
 * scrolling to the bottom will cause the page causes more tweets to load.
 * 
 * @param cursor
 *            An object with a method 'next'.
 */
var Pager = solrsearch.Pager = function(cursor) {
	var self = this,
		witness = $("<div class='witness' />"),
		container,
		done;
	
	this.witnessVisible = function() {
		return witness.is(":visible") && witness.position().top <= container.height();
	};
	
	this.isDone = function() { return !!done };
	
	this.refresh = function(cb) {
		if (!done && this.witnessVisible()) {
			cursor.next()
				.done(function(page) {
					if (!page) {
						done = true;
						witness.addClass("finished");
					} else {
						witness.before(page);
					}
				})
				.fail(function() {
					done = true;
					witness.addClass("failed");
				})
				.always(function() {
					self.refresh(cb);
				});
		} else if (cb) {
			cb(this);
		}
		return this;
	};
	
	this.container = function(c) {
		if (arguments.length) {
			(container = $(c))
				.css("position", "relative")
				.find(".witness")
					.remove()
					.end()
				.append(witness);
			return this;
		} else {
			return container;
		}
	};
	
	this.container($("<div class='pager' />"));
};




/**
 * Returns a solrsearch.Facet instance for the closest facet to the matched
 * facet.
 */
$.fn.facet = function() {
	return this.closest(".facet").each(function() {
		$(this).data("facet") || $(this).data("facet", new Facet(this));
	}).data("facet");
};


/**
 * Turns the matched element into a "pager." Whenever the bottom of the pager is
 * visible, the cursor's next() method is called which returns a promise. This
 * promise should resolve to either some HTML, a DOM Node, a jQuery instance, or
 * nothing (in the event there are no more rows). It is rejected when there is
 * an error.
 */
$.fn.pager = function(cursor) {
	this.each(function() {
		function check(pager) {
			setTimeout(function() {
				if (!pager.isDone()) {
					pager.refresh(check);
				}
			}, 1000);
		}
		
		new Pager(cursor).container(this).refresh(check);
	});
};


$.fn.solrSearch = function(extras, s, f) {
	var defs = [];
	
	this.each(function() {
		var form = $(this).closest("form"),
			fields = form,
			q = [ "return=counts" ];
		
		form.find(".facet").each(function() {
			fields = fields.add($(this).facet().extraFormFields());
		});
		
		q.push(fields.serialize());
		if (extras) {
			if (extras = $.param(extras))
				q.push(extras);
		}
		
		form.addClass("refresh");
		defs.push($.ajax({
			type: "POST",
			url: form.attr("action"),
			dataType: "json",
			data: q.join("&"),
			success: function(data) {
				if (!data)
					return;	// For some reason, success is called on xhr.abort()
				solrsearch.updateFacetCounts(data, form);
				elasticUpdate(data, form);
			},
			complete: function() {
				form.removeClass("refresh");
			}
		}));
	});
	
	return $.when.apply($, defs).then(s, f);
};


/**
 * For each form matched, it'll add handlers to its input elements so that when
 * they are changed the facet counts are changed.
 */
$.fn.updateFacetCountsOnChange = (function() {
	var xhr;
	return function() {
		this.filter("form").each(function() {
			var form = $(this);
			form.find(":input[name!='']").live("change", pause(function() {
				// if (xhr && xhr.readyState != 4)
				// xhr.abort();
				
				form.solrSearch();
			}, 200));	// 200ms pause, just in case JS updates them en masse.
		});
	};
})();



/**
 * Updates the facet counts on all facet filters using a (JSON) search result
 * from Solr. The function requires the 'facet_counts' property from the Solr
 * search result as the 1st argument and the form that contains the facets'
 * fields as the 2nd argument. All "filters" with counts created using the
 * solrsearch:facet tag will have their counts updated.
 * 
 * @param facetCounts
 *            A map as found in the facet_counts property of a Solr result.
 * @param form
 *            The form whose facet counts we wish to update.
 */
solrsearch.updateFacetCounts = function(result, form) {
	var facetCounts = result.facet_counts,
		fieldStats = result.stats && result.stats.stats_fields,
		fq = {};
	
	if (result.response.numFound == 0) {
		form.find(".filterGroup").each(function() {
			$(".filter .count", this).text("0");
			$(".range .min, .range .max", this).text("0");
		});
		solrsearch.updateHiddenCounts(form);
		return;
	}
	
	// Populate fq
	
	form.find(".filterGroup").each(function() {
		var field = $("input[name$='.field']", this).val(),
			filters = $(".filter", this),
			range = $(".range", this),
			filtersByValues = {};
		filters.each(function() {
			var filter = $(this),
				value = filter.find("input[name$='.values']").val();
			if (value)
				filtersByValues[value] = filter;
		});
		
		fq[field] = $.extend(fq[field], filtersByValues);
		
		if (range.length && (field in fieldStats)) {
			range.find(".min").text(fieldStats[field].min);
			range.find(".max").text(fieldStats[field].max);
		}
	});
	
	for (var q in facetCounts.facet_queries) {
		var field = q.split(":", 1)[0],
			values = q.substring(field.length + 1),
			count = facetCounts.facet_queries[q],
			filter = fq[field] && (fq[field][values] ||
					 (values[0] == "(" && values[values.length - 1] == ")" &&
							 fq[field][values.substring(1, values.length - 1)]));
		if (filter) {
			filter.find(".count").text(count);
		}
	}
	
	for (var field in facetCounts.facet_fields) {
		var enums = facetCounts.facet_fields[field];
		for (var i = 0; i < enums.length - 1; i += 2) {
			var values = enums[i],
				count = enums[i + 1],
				filter = fq[field] && (fq[field][values] || fq[field]['"' + values + '"']);
			if (filter)
				filter.find(".count").text(count);
		}
	}
	
	solrsearch.updateHiddenCounts(form);
};

solrsearch.updateHiddenCounts = function(form) {
	form = form || $(document);
	
	form.find(".hidden-filters").each(function() {
		var filters = $(".filter", this),
			showLink = $(this).closest(".filterGroup").find(".show-hidden:has(.count)");
		var count = 0;
		$(".filter .count", this).each(function() {
			var val = $.trim($(this).text());
			if (isInt.test(val)) {
				count += parseInt(val);
			} else {
				count = "?";
				return false;
			}
		});
		showLink.find(".count").text(count);
	});
};


$(document).ready(function() {
	$("form:has(.facet)").each(function() {
		var form = $(this);
		form.updateFacetCountsOnChange();
		form.find(".facet:has(.filter input:checked)").addClass("filled");
		form.find(".filterGroup:not(.facet) .filter :input").each(function() {
			if ($.trim($(this).val()))
				$(this).closest(".filterGroup").addClass("filled");
		});
		
		var i =0;
		form.find(".facet .filter input[type='checkbox']").live("change", function() {
			var facet = $(this).closest(".facet");
			if (facet.find(".filter input:checked").length) {
				facet.addClass("filled");
			} else {
				facet.removeClass("filled");
			}
		});
		form.find(".filterGroup:not(.facet) .filter :input").live("change", function() {
			var facet = $(this).closest(".filterGroup");
			if ($.trim($(this).val()))
				facet.addClass("filled");
			else
				facet.removeClass("filled");
		});
	});
	
	$(".facet").facet();
	
	// Enable custom range menus.
	$(".facet.provides-custom-range").each(function() {
		var facet = $(this).facet();
		facet.addMenu(new RangeMenu(facet));
	});
	
	// Enable filter searching for there-but-hidden filters.
	$(".facet:has(.filter.hidden)").each(function() {
		var facet = $(this).facet();
		facet.addMenu(new SearchMenu(new FilterDataSource(new HiddenFilterDataSource(facet), facet)));
	});
	
	solrsearch.updateHiddenCounts();
});



})(this, jQuery);