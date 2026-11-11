package net.microfalx.argus.diagnostics;

import net.microfalx.resource.Resource;

public class Profile extends Diagnostics {

    public Profile(String name) {
        super(name);
    }

    @Override
    protected Type getType() {
        return null;
    }

    @Override
    protected void collect(Resource resource) {

    }
}
