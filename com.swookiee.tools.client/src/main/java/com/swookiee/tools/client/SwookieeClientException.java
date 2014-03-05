package com.swookiee.tools.client;

/**
 * This is a basic Exception which wraps possible errors from inside the {@link SwookieeClient}.
 */
public class SwookieeClientException extends Exception {
    private static final long serialVersionUID = 6304373975435563005L;

    public SwookieeClientException(final String message) {
        super(message);
    }

    public SwookieeClientException(final String message, final Exception ex) {
        super(message, ex);
    }
}
