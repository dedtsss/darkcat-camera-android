package ru.darkcat.camera.upload;

/** Pure strict verification policy for a remote encrypted object. */
public final class WebDavVerification {
    public static boolean hasExactLength(int httpStatus, long reportedLength, long expectedLength) {
        return httpStatus >= 200 && httpStatus < 300
                && expectedLength >= 0
                && reportedLength >= 0
                && reportedLength == expectedLength;
    }

    private WebDavVerification() { }
}
