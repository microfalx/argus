package net.microfalx.argus.core;

import net.microfalx.argus.api.Constants;
import net.microfalx.lang.Initializable;
import net.microfalx.lang.service.Service;
import net.microfalx.threadpool.ThreadPool;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * An implementation of a {@link Service} which provides more building blocks for project services.
 */
public abstract class AbstractService implements Service, Initializable {

    private ThreadPool threadPool;

    /**
     * Replaces the default thread pool.
     *
     * @param threadPool a new pool
     */
    public void setThreadPool(ThreadPool threadPool) {
        requireNonNull(threadPool);
        this.threadPool = threadPool;
    }

    /**
     * Returns the thread associated with the service.
     * <p>
     * If no thread pool is provided, a shared thread pool is registered
     *
     * @return a non-null instance
     */
    protected final ThreadPool getThreadPool() {
        if (threadPool == null) {
            threadPool = ThreadPool.builder(Constants.THREAD_POOL).getOrBuild();
        }
        return threadPool;
    }
}
