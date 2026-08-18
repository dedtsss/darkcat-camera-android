package ru.darkcat.camera.field;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class FieldCameraOwnershipTest {
    @Test
    public void eachLifecycleHandoffAdvancesGenerationAndOwner() {
        FieldCameraOwnership ownership = new FieldCameraOwnership();
        assertEquals(FieldCameraOwnership.Owner.ACTIVITY, ownership.owner());
        assertEquals(0L, ownership.generation());

        long activityGeneration = ownership.handoffToActivity();
        assertEquals(1L, activityGeneration);
        assertEquals(FieldCameraOwnership.Owner.ACTIVITY, ownership.owner());

        long serviceGeneration = ownership.handoffToService();
        assertEquals(2L, serviceGeneration);
        assertEquals(FieldCameraOwnership.Owner.SERVICE, ownership.owner());
        assertEquals(serviceGeneration, ownership.generation());
    }
}
