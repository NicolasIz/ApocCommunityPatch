package com.arkcronist.content.core.upload;

import java.io.IOException;

/** An upload that did not end in a working download link, with a message an admin can act on. */
public final class UploadException extends IOException {

    /** Whether trying again could help: a network error or an overloaded service, not a refusal. */
    private final boolean retryable;

    public UploadException(String message) {
        this(message, false, null);
    }

    public UploadException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
