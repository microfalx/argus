package net.microfalx.argus.core;

import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.Resource;
import net.microfalx.registry.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonSerializerTest {

    private final Registry registry = Registry.get();

    @Test
    void health() {
        Health health = createHealth();
        registry.set("/test", health);
        Health healthFromRegistry = registry.get("/test").orElseThrow().get();
        assertEquals(health, healthFromRegistry);
    }

    @Test
    void resource() {
        Health health = createHealth();
        Resource resource = Resource.create(Resource.Type.SERVICE, "test").withHealth(health);
        registry.set("/test", resource);
        Resource resourceFromRegistry = registry.get("/test").orElseThrow().get();
        assertEquals(resource, resourceFromRegistry);
    }

    private Health createHealth() {
        Health health = new Health();
        health.update("b", "c", 3.5f);
        health.getGroup("test").update("a", 4.5f);
        return health;
    }
}
