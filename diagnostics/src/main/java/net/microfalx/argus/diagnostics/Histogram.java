package net.microfalx.argus.diagnostics;

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
    protected void collect() {

    }
}
