package com.sysgears.grain.config

import com.sysgears.grain.preview.ConfigChangeListener
import com.sysgears.grain.service.ProxyManager
import com.sysgears.grain.service.Service
import groovy.util.logging.Slf4j

import javax.inject.Inject

/**
 * Implementation binder that generates proxy which binds implementation to Config property value on the fly.
 * <p>
 * Usage example:
 * <pre>
 * public @Bean @Primary Highlighter createHighlighter(ConfigBinder binder) {
 *     binder.bind(Highlighter.class, 'features.highlight', 
 *         [pygments: PygmentsHighlighter, default: FakeHighlighter])
 * }
 *
 * Somewhere in the other bean:
 * private @Inject Highlighter highlighter
 * ...
 * highlighter.highlight(code, lang) // Will use either PygmentsHighlighter or FakeHighlighter based on
 *                                   // the value of features.highlight in SiteConfig.groovy at the moment 
 * </pre>
 */
@javax.inject.Singleton
@Slf4j
public class ConfigBinder {
    
    /** Site config */
    @Inject private Config config
    
    /** Proxy manager */
    @Inject private ProxyManager proxyManager
    
    public <T extends ConfigChangeListener> T bind(
            final Class<T> ifc, final String propertyName,
            final Map<String, Object> implMap) {
        def proxy = proxyManager.createProxy(ifc, [configChanged: { args ->
            try {
                def managerState = proxyManager.state
                def propertyValue = propertyName.split(/\./).inject(config)
                        { parent, property -> parent?."$property" }
                T impl = implMap.find { it.key.toString() == propertyValue.toString() }?.value as T
                if (impl == null) {
                    impl = implMap['default'] as T
                }
                def proxyTarget = managerState.getTarget(delegate.proxy)
                def isService = Service.class.isAssignableFrom(ifc) && proxyTarget != impl
                if (isService) {
                    log.info "Switching from ${proxyTarget?.class?.name ?: 'none'} to ${impl.class.name} service for ${propertyName}"
                    proxyTarget?.stop()
                }
                proxyManager.setTarget(delegate.proxy, impl)
                log.debug "Using proxy ${impl?.class?.name} for ${propertyName}"
            } catch (e) {
                throw new RuntimeException("Error handling config change in binder", e)
            }
        }])
        
        proxyManager.setTarget(proxy, implMap['default'])
        
        proxy
    }
}
