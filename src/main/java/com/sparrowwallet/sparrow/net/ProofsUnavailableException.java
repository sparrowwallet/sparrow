package com.sparrowwallet.sparrow.net;

/**
 * Thrown where the connected server did not answer a request for inclusion proofs at all, on any attempt, for a reason other than not implementing the
 * call - a batch it will not accept, a size it will not return, an error of its own. Nothing has been proven or disproven, and nothing refused.
 * <p>
 * A server that answered and then stopped is deliberately not this: it has shown it can serve the call, so what followed is to be retried rather than
 * worked around, and it may already have proven a transaction false - not a finding to trade away for an unverified session.
 */
public class ProofsUnavailableException extends ServerException {
    public ProofsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
