import grails.util.Environment

import org.wodaklab.solrsearch.SolrSearchUtils

class SolrSearchGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.4 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    def author = "Tom Switzer"
    def authorEmail = "thomas@wodaklab.org"
    def title = "Solr Search"
    def description = '''\\
Provides a simplified service for querying solr from request parameters.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/solr-search"

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before 
    }

    def doWithSpring = {
        // TODO Implement runtime spring config (optional)
		ConfigSlurper slurper = new ConfigSlurper(Environment.getCurrent().getName());
		GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader())
		try {
		   SolrSearchUtils.config = slurper.parse(classLoader.loadClass('SolrSearchConfig'))
		} catch (Exception e) {
			throw new RuntimeException(e)
		}
    }

    def doWithDynamicMethods = { ctx ->
        // TODO Implement registering dynamic methods to classes (optional)
    }

    def doWithApplicationContext = { applicationContext ->
        // TODO Implement post initialization spring config (optional)
    }

    def onChange = { event ->
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }
}
