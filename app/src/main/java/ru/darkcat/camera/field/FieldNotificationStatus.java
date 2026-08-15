package ru.darkcat.camera.field;

/** Pure wording policy: notification readiness must reflect the actual owner, never a legacy hint. */
public final class FieldNotificationStatus {
    public static String cameraState(boolean storageBlocked, boolean ownerReady, boolean activityVisible) {
        if (storageBlocked) return "Ошибка хранилища · съёмка заблокирована";
        if (ownerReady) return "Камера готова";
        if (activityVisible) return "Камера используется экраном";
        return "Камера инициализируется";
    }

    private FieldNotificationStatus() { }
}
