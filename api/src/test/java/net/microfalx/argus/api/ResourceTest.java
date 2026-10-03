package net.microfalx.argus.api;

import net.microfalx.lang.convert.Types;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResourceTest {

    @Test
    void create() {
        Resource resource = createResource();
        assertNotNull(resource);
        serializeAndAssert(resource);
    }

    @Test
    void withGroup() {
        Resource resource = createResource().withGroup("test-group");
        assertNotNull(resource);
        serializeAndAssert(resource);
    }

    @Test
    void withHealth() {
        Resource resource = createResource().withHealth(createHealth());
        assertNotNull(resource);
        serializeAndAssert(resource);
    }

    @Test
    void withInstance() {
        Resource resource = createResource().withInstance(createResource("instance-1"))
                .withInstances(Arrays.asList(createResource("instance-2"), createResource("instance-3")));
        assertNotNull(resource);
        serializeAndAssert(resource);
    }

    private Resource createResource() {
        return createResource("test");
    }

    private Resource createResource(String id) {
        return Resource.create(Resource.Type.SERVICE, id)
                .withHealth(createHealth());
    }

    private Health createHealth() {
        Health health = new Health("test");
        Health.Group group1 = health.getGroup("Database");
        Health.Group group2 = group1.getGroup("Node");

        health.update("Messaging", "Broker", 1.75f);
        health.update("Database", "Latency", 3f);

        group1.update("Broker", 1.75f);
        group2.update("Latency", 3f);
        return health;
    }

    private void serializeAndAssert(Resource resource) {
        String json = Types.asString(resource);
        Assertions.assertThat(json).isNotBlank();

        Resource deserializedResource = Types.asObject(json, Resource.class);
        assertEquals(deserializedResource.getId(), resource.getId());
        assertEquals(deserializedResource.getType(), resource.getType());
        assertEquals(deserializedResource.getHealth(), resource.getHealth());
        assertEquals(deserializedResource.getInstances().size(), resource.getInstances().size());
        assertEquals(deserializedResource.getGroup(), resource.getGroup());
    }
}