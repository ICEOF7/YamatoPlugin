package lama.project;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
public final class Cooldowns {
    private final Map<UUID, EnumMap<YamatoAbility, Long>> lastUseMs = new ConcurrentHashMap<>();

    public boolean ready(UUID uuid, YamatoAbility ability, long cooldownMs) {
        long now = System.currentTimeMillis();
        long last = lastUseMs.getOrDefault(uuid, new EnumMap<>(YamatoAbility.class))
                .getOrDefault(ability, 0L);
        return (now - last) >= cooldownMs;
    }

    public long remainingMs(UUID uuid, YamatoAbility ability, long cooldownMs) {
        long now = System.currentTimeMillis();
        long last = lastUseMs.getOrDefault(uuid, new EnumMap<>(YamatoAbility.class))
                .getOrDefault(ability, 0L);
        long rem = cooldownMs - (now - last);
        return Math.max(0, rem);
    }

    public void mark(UUID uuid, YamatoAbility ability) {
        lastUseMs.computeIfAbsent(uuid, __ -> new EnumMap<>(YamatoAbility.class)).put(ability, System.currentTimeMillis());
    }

    public void cleanup(UUID uuid) {
        lastUseMs.remove(uuid);
    }
}
