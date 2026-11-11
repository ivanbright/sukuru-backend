package rw.shop.common;

import java.util.UUID;

public final class Ids {

    private Ids() {
    }

    public static UUID newId() {
        return UUID.randomUUID();
    }
}
