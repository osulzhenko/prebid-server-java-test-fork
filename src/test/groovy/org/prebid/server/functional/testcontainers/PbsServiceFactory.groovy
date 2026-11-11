package org.prebid.server.functional.testcontainers

import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.container.PbsContainerConfig
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.testcontainers.images.builder.Transferable

import static org.prebid.server.functional.testcontainers.PbsServiceFactory.ContainerPolicy.LRU
import static org.prebid.server.functional.testcontainers.PbsServiceFactory.ContainerPolicy.PERSISTENT
import static org.prebid.server.functional.util.SystemProperties.DEBUG_ENABLED
import static org.prebid.server.functional.util.SystemProperties.MAX_CONTAINER
import static org.prebid.server.functional.util.SystemProperties.USE_FIXED_CONTAINER_PORTS

class PbsServiceFactory {

    private static final int MAX_CONTAINER_COUNT = maxContainerCount

    private static final Map<PbsContainerConfig, CachedContainer> containers =
            new LinkedHashMap<>(16, 0.75f, true)

    // Will be removed in 5.0
    @Deprecated(forRemoval = true)
    static PrebidServerService getService(Map<String, String> config,
                                          Map<String, Transferable> additionalFiles = [:],
                                          ContainerPolicy policy = LRU) {
        getService(new PbsContainerConfig(config, additionalFiles), policy)
    }

    static PrebidServerService getService(PbsContainerConfig config,
                                          ContainerPolicy policy = LRU) {
        def cached = containers.remove(config)
        if (cached) {
            cached.container.refresh()
            cached.persistent |= policy == PERSISTENT
            containers.put(config, cached)
            return new PrebidServerService(cached.container)
        }
        evictIfNeeded()
        def container = new PrebidServerContainer(config, DEBUG_ENABLED)
        container.start()
        containers.put(config, new CachedContainer(
                container: container,
                persistent: policy == PERSISTENT
        ))
        return new PrebidServerService(container)
    }

    private static void evictIfNeeded() {
        if (containers.size() < MAX_CONTAINER_COUNT) {
            return
        }

        def eldest = containers.entrySet().find { !it.value.persistent }
        if (!eldest) {
            throw new IllegalStateException("All container slots are persistent")
        }

        eldest.value.container.stop()
        containers.remove(eldest.key)
    }

    static void stopContainers() {
        def iterator = containers.entrySet().iterator()

        while (iterator.hasNext()) {
            def entry = iterator.next()
            if (!entry.value.persistent) {
                entry.value.container.stop()
                iterator.remove()
            }
        }
    }

    // Will be removed in 5.0.
    @Deprecated(forRemoval = true)
    static void removeContainer(Map<?, ?> config) {
        removeContainer(new PbsContainerConfig(config))
    }

    static void removeContainer(PbsContainerConfig config) {
        containers.remove(config)?.container?.stop()
    }

    static void stopAllContainers() {
        containers.values().each { it.container.stop() }
        containers.clear()
    }

    private static int getMaxContainerCount() {
        USE_FIXED_CONTAINER_PORTS
                ? 1
                : MAX_CONTAINER
    }

    private static class CachedContainer {
        PrebidServerContainer container
        boolean persistent
    }

    enum ContainerPolicy {
        PERSISTENT,
        LRU
    }
}
