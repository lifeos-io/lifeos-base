package com.lifeos.base.id;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * RFC 9562 UUID version 7: the first 48 bits are the Unix time in ms, the rest are random.
 * Being time-ordered, it causes fewer B-tree page splits than UUID v4 when used as a primary key.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID next() {
        return at(System.currentTimeMillis());
    }

    static UUID at(long epochMillis) {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        b[0] = (byte) (epochMillis >>> 40);
        b[1] = (byte) (epochMillis >>> 32);
        b[2] = (byte) (epochMillis >>> 24);
        b[3] = (byte) (epochMillis >>> 16);
        b[4] = (byte) (epochMillis >>> 8);
        b[5] = (byte) epochMillis;
        b[6] = (byte) ((b[6] & 0x0F) | 0x70);
        b[8] = (byte) ((b[8] & 0x3F) | 0x80);
        ByteBuffer buf = ByteBuffer.wrap(b);
        return new UUID(buf.getLong(), buf.getLong());
    }
}
