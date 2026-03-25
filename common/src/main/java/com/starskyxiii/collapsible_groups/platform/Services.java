package com.starskyxiii.collapsible_groups.platform;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.platform.services.IConfigProvider;
import com.starskyxiii.collapsible_groups.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

/**
 * Loader-agnostic service access used by common code.
 * <p>
 * Implementations are supplied by each loader module through Java's {@link ServiceLoader}.
 */
public class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    public static final IConfigProvider CONFIG   = load(IConfigProvider.class);

    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Constants.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
