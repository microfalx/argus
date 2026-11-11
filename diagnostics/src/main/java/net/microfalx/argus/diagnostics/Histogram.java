package net.microfalx.argus.diagnostics;

import net.microfalx.resource.Resource;

/**
 * A histogram of in memory objects for a JVM.
 */
public class Histogram extends Diagnostics {

    public Histogram(String name) {
        super(name);
    }

    @Override
    protected Type getType() {
        return Type.HISTOGRAM;
    }

    @Override
    protected void collect(Resource resource) {

    }
}
