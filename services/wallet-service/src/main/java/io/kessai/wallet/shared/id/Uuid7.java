package io.kessai.wallet.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

public final class Uuid7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuid7() {
    }

    public static UUID generate() {
        byte[] rand = new byte[10];
        RANDOM.nextBytes(rand);

        long msb = (System.currentTimeMillis() & 0xFFFF_FFFF_FFFFL) << 16;
        msb |= 0x7000L;                                       // version 7
        msb |= ((rand[0] & 0x0FL) << 8) | (rand[1] & 0xFFL);  // rand_a

        long lsb = 0;
        for (int i = 2; i < rand.length; i++) {
            lsb = (lsb << 8) | (rand[i] & 0xFFL);
        }
        lsb &= 0x3FFF_FFFF_FFFF_FFFFL;
        lsb |= 0x8000_0000_0000_0000L;                        // RFC 4122 variant

        return new UUID(msb, lsb);
    }
}
