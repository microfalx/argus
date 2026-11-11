package net.microfalx.argus.diagnostics;

public class Profile extends Diagnostics {

    public Profile(String name) {
        super(name);
    }

    @Override
    protected Type getType() {
        return null;
    }

    @Override
    protected void collect() {

    }
}
