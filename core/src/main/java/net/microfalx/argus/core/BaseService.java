package net.microfalx.argus.core;

import net.microfalx.argus.api.Constants;
import net.microfalx.lang.Service;
import net.microfalx.threadpool.ThreadPool;

/**
 * An implementation of a {@link Service} which provides more building blocks for all services.
 */
public abstract class BaseService implements Service {

    /**
     * Returns the thread pool shared by all services.
     *
     * @return a non-null instance
     */
    protected final ThreadPool getThreadPool() {
        return ThreadPool.builder(Constants.THREAD_POOL).getOrBuild();
    }
}
