package broccolai.tickets.core.model.user;

import java.util.UUID;

import net.kyori.adventure.audience.Audience;
import org.checkerframework.checker.nullness.qual.NonNull;

public final class ConsoleUserAudience implements UserAudience {

    public static final @NonNull UUID UUID = new UUID(0, 0);

    public static final @NonNull String USERNAME = "CONSOLE";

    private final Audience audience;

    public ConsoleUserAudience(final @NonNull Audience audience) {
        this.audience = audience;
    }

    @Override
    public @NonNull UUID uuid() {
        return UUID;
    }

    @Override
    public @NonNull String username() {
        return USERNAME;
    }

    @Override
    public @NonNull Audience audience() {
        return this.audience;
    }

}
