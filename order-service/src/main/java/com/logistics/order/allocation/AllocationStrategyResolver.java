package com.logistics.order.allocation;

import com.logistics.order.config.AllocationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Picks the strategy to use for a given request.
 *
 * <p>Registry, populated by Spring: injecting {@code List<WarehouseAllocationStrategy>} means every
 * implementation on the classpath registers itself. Adding a third strategy is a new class with
 * {@code @Component} - this class does not change, and neither does any {@code if} anywhere.
 *
 * <p>An unknown name is a 400 listing what does exist, never a silent fall back to the default: an
 * operator who mistypes {@code nearset} must be told, not quietly served a different rule and left
 * wondering why the plans look wrong.
 */
@Slf4j
@Component
public class AllocationStrategyResolver {

    private final Map<String, WarehouseAllocationStrategy> strategiesByName;
    private final String defaultStrategyName;

    public AllocationStrategyResolver(List<WarehouseAllocationStrategy> strategies,
                                      AllocationProperties properties) {
        this.strategiesByName = strategies.stream()
                .sorted(java.util.Comparator.comparing(WarehouseAllocationStrategy::name))
                .collect(Collectors.toMap(WarehouseAllocationStrategy::name, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
        this.defaultStrategyName = properties.defaultStrategy();

        if (!strategiesByName.containsKey(defaultStrategyName)) {
            // Fail at startup, not on the first order: a misconfigured default is a deployment
            // mistake, and the cheapest place to discover it is here.
            throw new IllegalStateException("Configured default allocation strategy '"
                    + defaultStrategyName + "' does not exist. Available: " + strategiesByName.keySet());
        }
        log.info("Allocation strategies registered: {} (default: {})",
                strategiesByName.keySet(), defaultStrategyName);
    }

    /** Resolves by name, or returns the configured default when the caller expressed no preference. */
    public WarehouseAllocationStrategy resolve(String requestedName) {
        String name = (requestedName == null || requestedName.isBlank())
                ? defaultStrategyName
                : requestedName.trim();

        WarehouseAllocationStrategy strategy = strategiesByName.get(name);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown allocation strategy '" + name
                    + "'. Available strategies: " + strategiesByName.keySet() + ".");
        }
        return strategy;
    }

    public Set<String> availableStrategies() {
        return strategiesByName.keySet();
    }

    public List<WarehouseAllocationStrategy> all() {
        return List.copyOf(strategiesByName.values());
    }

    public String defaultStrategyName() {
        return defaultStrategyName;
    }
}
