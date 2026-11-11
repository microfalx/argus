package net.microfalx.argus.diagnostics;

import net.microfalx.resource.Resource;

public class Profile extends Diagnostics {

    public Profile(String name) {
        this(name, Options.create());
    }

    public Profile(String name, Options options) {
        super(name, options);
    }

    @Override
    protected Type getType() {
        return null;
    }

    @Override
    protected void collect(Resource resource) {

    }
}
