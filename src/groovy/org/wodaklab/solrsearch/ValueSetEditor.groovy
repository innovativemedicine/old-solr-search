package org.wodaklab.solrsearch

import java.beans.PropertyEditorSupport
import static org.wodaklab.solrsearch.SolrSearchUtils.escape

/**
 * A {@code PropertyEditor} for {@code ValueSet}s. This can be used by a
 * {@code DataBinder} to bind parameter values to a ValueSet. The actual syntax
 * supported is fairly basic. Ranges must look, generally, like [X TO Y]. X to
 * Y are expected to be either a date, number or a "*" (open ended range). List
 * of values are separated by spaces and/or commas. In order to specify a value
 * with a comma or space in it, it must be wrapped in quotes. All quotes within
 * can be escaped with a "\". For example, 
 * '"Homo sapiens", "Drosphila melanogaster"', would create a ValueSet with 2
 * values, "Homo sapiens" and "Drosphila melanogaster".
 */
class ValueSetEditor extends PropertyEditorSupport {
	private static final STORED = /^stored\:(.*)$/
	private static final RANGE = /^\s*\[((\d+)|([\w\.\:\/\*-]+)|(\"([^\\\"]|\\\\|\\\")+\"))\s*[Tt][Oo]\s*((\d+)|([\w\.\:\/\*-]+)|(\"([^\\\"]|\\\\|\\\")+\"))\]\s*$/
	private static final LIST = /^\s*(((\"([^\\\"]|\\\\|\\\")+\")|([^\s\"][^\s,]*))[\s,]*)*$/
	private static final TERM_CHUNKER = /((\"(([^\\\"]|\\\\|\\\")+)\")|([^\s\"][^\s,]*))/
	
	void setAsText(String op) {
		def rangeMatch = op =~ RANGE
		if (rangeMatch) {
			setValue(new ValueRange(rangeMatch[0][1]?: "*", rangeMatch[0][6]?: "*"))
		} else if (op =~ STORED) {
			def m = op =~ STORED
			setValue(new StoredValueSet(m[0][1]))
		} else if (op =~ LIST) {
			setValue(new ValueList((op =~ TERM_CHUNKER).collect({ (it[5] == null ? it[3] : it[5]).trim() }).findAll({ it })))
		} else {
			throw new IllegalArgumentException("Invalid range, value list, or value provided. Cannot convert to ValueSet.")
		}
	}
	
	String getAsText() {
		return ValueSetEditor.getAsText(value)
	}
	
	static String getAsText(StoredValueSet stored) {
		return "stored:" + stored.key
	}
	
	static String getAsText(ValueRange range) {
		def s = new StringBuilder()
		s << "[" << escape(range.from) << " TO " << escape(range.to) << "]"
		return s.toString()
	}
	
	static String getAsText(ValueList list) {
	   switch (list.values.size()) {
		case 0:
			return ""
			break
		case 1:
			return escape(list.values[0])
			break;
		default:
			return list.values.collect{escape(it)}.join(" ")
		}
	}
}
