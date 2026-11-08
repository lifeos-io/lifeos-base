package com.lifeos.base.error;

import java.util.regex.Pattern;

/**
 * A stable, machine-readable error identifier. The web module turns it into an RFC 9457 problem response
 * ({@code status}, {@code title}, and {@code code} as an extension member); clients branch on {@link #code()},
 * never on the human-readable text.
 *
 * <p>Conventions:
 * <ul>
 *   <li>{@link #code()} is UPPER_SNAKE_CASE and never changes once released; renaming a code is a breaking
 *       API change.</li>
 *   <li>Cross-cutting codes live in {@link CommonErrorCode}. A service declares its own codes in an enum that
 *       implements this interface, prefixed with the service name, e.g. {@code STRATEGY_CAPACITY_EXCEEDED}.</li>
 *   <li>{@link #status()} is an HTTP status in the 4xx or 5xx range.</li>
 * </ul>
 */
public interface ErrorCode {

    /** Valid shape of {@link #code()}: upper-case words separated by single underscores. */
    Pattern CODE_FORMAT = Pattern.compile("[A-Z][A-Z0-9]*(_[A-Z0-9]+)*");

    /** Stable identifier, e.g. {@code NOT_FOUND} or {@code STRATEGY_CAPACITY_EXCEEDED}. */
    String code();

    /** HTTP status the error maps to (400-599). */
    int status();

    /** Short, human-readable summary that is the same for every occurrence of this code. */
    String title();
}
