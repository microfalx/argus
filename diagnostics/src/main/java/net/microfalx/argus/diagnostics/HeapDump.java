package net.microfalx.argus.diagnostics;

/**
 * A memory dump of a JVM.
 */
public class HeapDump extends Diagnostics {

    public HeapDump(String name) {
        super(name);
    }

    @Override
    protected Type getType() {
        return Type.HEAP_DUMP;
    }

    @Override
    protected void collect() {

    }
}
