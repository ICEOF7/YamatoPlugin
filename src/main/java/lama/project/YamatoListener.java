package lama.project;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public final class YamatoListener implements Listener {

    private final YamatoPlugin plugin;
    private final YamatoItem yamato;
    private final Cooldowns cooldowns;

    private final Map<UUID, ComboState> combos = new ConcurrentHashMap<>();
    private final Map<UUID, InputState> inputs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> stunnedUntilMs = new ConcurrentHashMap<>();

    public YamatoListener(YamatoPlugin plugin, YamatoItem yamato, Cooldowns cooldowns) {
        this.plugin = plugin;
        this.yamato = yamato;
        this.cooldowns = cooldowns;
    }


    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        combos.remove(id);
        inputs.remove(id);
        stunnedUntilMs.remove(id);
        cooldowns.cleanup(id);
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();


        trackJumpInput(p, e.getFrom(), e.getTo());

        long now = System.currentTimeMillis();
        Long until = stunnedUntilMs.get(p.getUniqueId());
        if (until == null || now >= until) return;

        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;


        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            e.setTo(back);
        }
    }

    private void trackJumpInput(Player p, Location from, Location to) {
        if (to == null) return;

        if (to.getY() > from.getY() && p.getVelocity().getY() > 0.1) {
            inputs.computeIfAbsent(p.getUniqueId(), __ -> new InputState()).lastJumpMs = System.currentTimeMillis();
        }
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity)) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!yamato.isYamato(hand)) return;

        FileConfiguration cfg = plugin.getConfig();
        long window = cfg.getLong("combo.window-ms", 1200);
        int max = cfg.getInt("combo.max", 5);
        double per = cfg.getDouble("combo.damage-bonus-per-stack", 0.05);
        boolean baseOnly = cfg.getBoolean("combo.apply-to-base-only", true);

        ComboState st = combos.computeIfAbsent(p.getUniqueId(), __ -> new ComboState());
        long now = System.currentTimeMillis();
        if (now - st.lastHitMs <= window) {
            st.count = Math.min(max, st.count + 1);
        } else {
            st.count = 1;
        }
        st.lastHitMs = now;


        if (st.count >= 2) {
            double mult = 1.0 + per * (st.count - 1);
            if (baseOnly) {

                double base = cfg.getDouble("item.base-damage", 7.0);
                e.setDamage(Math.max(e.getDamage(), base * mult));
            } else {
                e.setDamage(e.getDamage() * mult);
            }
        }


        int speedAt = cfg.getInt("combo.speed-at", 3);
        int strAt = cfg.getInt("combo.strength-at", 5);
        int dur = cfg.getInt("combo.buff-duration-ticks", 40);
        if (st.count == speedAt) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur, 0, true, false, true));
        }
        if (st.count == strAt) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, dur, 0, true, false, true));
        }

        if (cfg.getBoolean("combo.show-actionbar", true)) {
            Text.actionBarCfg(p, cfg, "messages.actionbar.combo", "<gray>Комбо: <yellow>{count}<gray>/<yellow>{max}", "{count}", String.valueOf(st.count), "{max}", String.valueOf(max));
        }
    }

    private double abilityMultiplier(Player p) {
        FileConfiguration cfg = plugin.getConfig();
        ComboState st = combos.get(p.getUniqueId());
        if (st == null) return 1.0;
        long window = cfg.getLong("combo.window-ms", 1200);
        long now = System.currentTimeMillis();
        if (now - st.lastHitMs > window) return 1.0;
        int count = st.count;
        double per = cfg.getDouble("combo.damage-bonus-per-stack", 0.05);
        if (count < 2) return 1.0;
        return 1.0 + per * (count - 1);
    }


    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;

        ItemStack hand = killer.getInventory().getItemInMainHand();
        if (!yamato.isYamato(hand)) return;

        int kills = yamato.getKills(hand);
        yamato.setKills(hand, kills + 1);
        yamato.refreshLore(hand, plugin.getConfig());
        killer.updateInventory();
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!yamato.isYamato(hand)) return;


        boolean isSneak = p.isSneaking();
        if (a == Action.RIGHT_CLICK_AIR || isSneak) {
            e.setCancelled(true);
        }

        if (isSneak) {
            dash(p);
        } else {
            judgementCut(p);
        }
    }

    private void judgementCut(Player p) {
        FileConfiguration cfg = plugin.getConfig();
        long cd = cfg.getLong("cooldowns-ms.judgement-cut", 1300);
        UUID id = p.getUniqueId();
        if (!cooldowns.ready(id, YamatoAbility.JUDGEMENT_CUT, cd)) {
            long rem = cooldowns.remainingMs(id, YamatoAbility.JUDGEMENT_CUT, cd);
            Text.actionBarCfg(p, cfg, "messages.actionbar.judgement-cut-cooldown", "<red>Judgement Cut: <gray>кд {seconds}s", "{seconds}", Text.seconds(rem));
            return;
        }
        cooldowns.mark(id, YamatoAbility.JUDGEMENT_CUT);

        World w = p.getWorld();
        double range = cfg.getDouble("judgement-cut.range", 25.0);
        double radius = cfg.getDouble("judgement-cut.radius", 2.6);
        double damage = cfg.getDouble("judgement-cut.damage", 6.0) * abilityMultiplier(p);

        RayTraceResult r = w.rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), range);
        Location hit = (r != null && r.getHitPosition() != null)
                ? r.getHitPosition().toLocation(w)
                : p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(range));

        w.spawnParticle(Particle.SWEEP_ATTACK, hit, 20, radius / 2, radius / 2, radius / 2, 0.0);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f);

        for (LivingEntity le : nearbyLiving(hit, radius, radius, radius)) {
            if (le.equals(p)) continue;
            le.damage(damage, p);
        }
    }


    private void dash(Player p) {
        FileConfiguration cfg = plugin.getConfig();
        long cd = cfg.getLong("cooldowns-ms.dash", 6500);
        UUID id = p.getUniqueId();
        if (!cooldowns.ready(id, YamatoAbility.DASH, cd)) {
            long rem = cooldowns.remainingMs(id, YamatoAbility.DASH, cd);
            Text.actionBarCfg(p, cfg, "messages.actionbar.dash-cooldown", "<red>Dash Slash: <gray>кд {seconds}s", "{seconds}", Text.seconds(rem));
            return;
        }
        cooldowns.mark(id, YamatoAbility.DASH);

        int steps = cfg.getInt("dash.steps", 10);
        double dist = cfg.getDouble("dash.step-distance", 0.85);
        double hitRadius = cfg.getDouble("dash.hit-radius", 1.65);
        double damage = cfg.getDouble("dash.damage", 4.0) * abilityMultiplier(p);

        Set<UUID> hit = new HashSet<>();

        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-6) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();
        final Vector dashDirection = dir.clone();

        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f);

        new BukkitRunnable() {
            int i = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }

                if (i >= steps) {
                    cancel();
                    return;
                }


                double maxSub = 0.30;
                int sub = (int) Math.ceil(dist / maxSub);
                double per = dist / sub;

                Location cur = p.getLocation();
                for (int s = 0; s < sub; s++) {
                    Location next = cur.clone().add(dashDirection.clone().multiply(per));
                    next.setYaw(cur.getYaw());
                    next.setPitch(cur.getPitch());

                    if (!canTeleportTo(p, next)) {

                        cancel();
                        return;
                    }

                    p.teleport(next);
                    cur = next;
                }

                w.spawnParticle(Particle.CLOUD, p.getLocation(), 8, 0.15, 0.15, 0.15, 0.0);

                for (LivingEntity le : nearbyLiving(p.getLocation(), hitRadius, hitRadius, hitRadius)) {
                    if (le.equals(p)) continue;
                    if (hit.add(le.getUniqueId())) {
                        le.damage(damage, p);
                        w.spawnParticle(Particle.SWEEP_ATTACK, le.getLocation().add(0, 1.0, 0), 1, 0, 0, 0, 0);
                    }
                }

                i++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }


    private boolean canTeleportTo(Player p, Location next) {
        World w = next.getWorld();
        if (w == null) return false;
        if (!w.isChunkLoaded(next.getBlockX() >> 4, next.getBlockZ() >> 4)) return false;

        Location cur = p.getLocation();
        BoundingBox box = p.getBoundingBox();

        double dx = next.getX() - cur.getX();
        double dy = next.getY() - cur.getY();
        double dz = next.getZ() - cur.getZ();

        BoundingBox shifted = box.clone().shift(dx, dy, dz).expand(0.02);

        int minX = (int) Math.floor(shifted.getMinX());
        int maxX = (int) Math.floor(shifted.getMaxX());
        int minY = Math.max(w.getMinHeight(), (int) Math.floor(shifted.getMinY()));
        int maxY = Math.min(w.getMaxHeight() - 1, (int) Math.floor(shifted.getMaxY()));
        int minZ = (int) Math.floor(shifted.getMinZ());
        int maxZ = (int) Math.floor(shifted.getMaxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (!b.isPassable()) return false;
                }
            }
        }

        return true;
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!yamato.isYamato(hand)) return;


        e.setCancelled(true);
        stunCut(p);
    }

    private void stunCut(Player p) {
        FileConfiguration cfg = plugin.getConfig();
        long cd = cfg.getLong("cooldowns-ms.stun-cut", 12000);
        UUID id = p.getUniqueId();
        if (!cooldowns.ready(id, YamatoAbility.STUN_CUT, cd)) {
            long rem = cooldowns.remainingMs(id, YamatoAbility.STUN_CUT, cd);
            Text.actionBarCfg(p, cfg, "messages.actionbar.stun-cut-cooldown", "<red>Stun Cut: <gray>кд {seconds}s", "{seconds}", Text.seconds(rem));
            return;
        }
        cooldowns.mark(id, YamatoAbility.STUN_CUT);

        double range = cfg.getDouble("stun-cut.range", 5.0);
        double radius = cfg.getDouble("stun-cut.radius", 4.0);
        int freezeTicks = cfg.getInt("stun-cut.freeze-ticks", 60);
        int delay = cfg.getInt("stun-cut.damage-delay-ticks", 60);
        double damage = cfg.getDouble("stun-cut.damage", 9.0) * abilityMultiplier(p);


        int self = cfg.getInt("self-stun.f-ticks", 60);
        stunSelf(p, self);

        World w = p.getWorld();
        Vector forward = flatDirection(p);
        Location center = p.getLocation().add(forward.multiply(range * 0.5));

        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity le : nearbyLiving(center, radius, radius, radius)) {
            if (le.equals(p)) continue;
            if (le.getLocation().distanceSquared(p.getLocation()) > range * range + radius * radius) continue;
            targets.add(le);

            le.setFreezeTicks(Math.max(le.getFreezeTicks(), freezeTicks));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeTicks, 4, true, false, true));
            le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, freezeTicks, 1, true, false, true));
        }

        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.7f);
        w.spawnParticle(Particle.CRIT, center, 40, radius / 2, 0.6, radius / 2, 0.0);

        if (targets.isEmpty()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (LivingEntity le : targets) {
                if (le.isDead()) continue;
                le.damage(damage, p);
                w.spawnParticle(Particle.SWEEP_ATTACK, le.getLocation().add(0, 1.0, 0), 1, 0, 0, 0, 0);
            }
        }, delay);
    }

    private void stunSelf(Player p, int ticks) {
        long until = System.currentTimeMillis() + (ticks * 50L);
        stunnedUntilMs.put(p.getUniqueId(), until);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 10, true, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 0, true, false, true));
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmSwing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!yamato.isYamato(hand)) return;

        if (!p.isSneaking()) return;

        FileConfiguration cfg = plugin.getConfig();
        long jumpWindow = cfg.getLong("ult-input.jump-window-ms", 600);
        double stillThr = cfg.getDouble("ult-input.still-threshold", 0.05);

        InputState st = inputs.computeIfAbsent(p.getUniqueId(), __ -> new InputState());
        long now = System.currentTimeMillis();
        if (now - st.lastJumpMs > jumpWindow) return;

        Vector v = p.getVelocity();
        double horiz = Math.abs(v.getX()) + Math.abs(v.getZ());
        if (horiz > stillThr) return;


        int need = cfg.getInt("charge.kills-needed", 10);
        int kills = yamato.getKills(hand);
        if (kills < need) {
            Text.actionBarCfg(p, cfg, "messages.actionbar.ult-not-ready", "<red>Ульта не готова: <yellow>{kills}<gray>/<yellow>{need}", "{kills}", String.valueOf(kills), "{need}", String.valueOf(need));
            return;
        }

        long cd = cfg.getLong("cooldowns-ms.ult", 60000);
        UUID id = p.getUniqueId();
        if (!cooldowns.ready(id, YamatoAbility.ULT, cd)) {
            long rem = cooldowns.remainingMs(id, YamatoAbility.ULT, cd);
            Text.actionBarCfg(p, cfg, "messages.actionbar.ult-cooldown", "<red>Ульта: <gray>кд {seconds}s", "{seconds}", Text.seconds(rem));
            return;
        }

        cooldowns.mark(id, YamatoAbility.ULT);


        yamato.setKills(hand, 0);
        yamato.refreshLore(hand, cfg);
        p.updateInventory();

        ultimate(p);
    }

    private void ultimate(Player p) {
        FileConfiguration cfg = plugin.getConfig();
        World w = p.getWorld();

        double radius = cfg.getDouble("ult.radius", 7.5);
        int duration = cfg.getInt("ult.duration-ticks", 90);

        int stepInterval = cfg.getInt("ult-style.step-interval-ticks", 2);
        double hopRadius = cfg.getDouble("ult-style.owner-hop-radius", 2.6);
        double stepDmgRadius = cfg.getDouble("ult-style.step-damage-radius", 2.2);
        double stepDmg = cfg.getDouble("ult-style.step-damage", 2.2) * abilityMultiplier(p);
        int ringBurstInterval = cfg.getInt("ult-style.ring-burst-interval-ticks", 10);

        int selfExtra = cfg.getInt("self-stun.ult-ticks-extra", 10);
        stunSelf(p, duration + selfExtra);

        Location center = p.getLocation().clone();
        w.playSound(center, Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.8f);
        w.spawnParticle(Particle.END_ROD, center, 60, 0.6, 0.3, 0.6, 0.02);

        new BukkitRunnable() {
            int t = 0;
            final Random rnd = new Random();

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }

                if (t >= duration) {
                    cancel();
                    return;
                }


                if (t % ringBurstInterval == 0) {
                    w.spawnParticle(Particle.SONIC_BOOM, center, 1, 0, 0, 0, 0);
                    w.spawnParticle(Particle.PORTAL, center, 140, radius / 2, 0.5, radius / 2, 0.4);
                }

                if (t % stepInterval == 0) {

                    double ang = rnd.nextDouble() * Math.PI * 2;
                    Vector off = new Vector(Math.cos(ang), 0, Math.sin(ang)).multiply(rnd.nextDouble() * hopRadius);
                    Location next = center.clone().add(off);
                    next.setYaw(p.getLocation().getYaw());
                    next.setPitch(p.getLocation().getPitch());


                    next.setY(p.getLocation().getY());

                    if (canTeleportTo(p, next)) {
                        p.teleport(next);
                    }


                    Location here = p.getLocation();
                    w.spawnParticle(Particle.SWEEP_ATTACK, here.add(0, 1.0, 0), 2, 0.2, 0.2, 0.2, 0.0);

                    for (LivingEntity le : nearbyLiving(center, radius, radius, radius)) {
                        if (le.equals(p)) continue;
                        if (le.getLocation().distanceSquared(p.getLocation()) <= stepDmgRadius * stepDmgRadius) {
                            le.damage(stepDmg, p);
                        }
                    }
                }

                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }


    private List<LivingEntity> nearbyLiving(Location center, double x, double y, double z) {
        World w = center.getWorld();
        if (w == null) return Collections.emptyList();

        List<LivingEntity> out = new ArrayList<>();
        for (Entity entity : w.getNearbyEntities(center, x, y, z)) {
            if (entity instanceof LivingEntity living) {
                out.add(living);
            }
        }
        return out;
    }

    private Vector flatDirection(Player p) {
        Vector dir = p.getLocation().getDirection();
        dir.setY(0);
        if (dir.lengthSquared() < 1e-6) return new Vector(0, 0, 1);
        return dir.normalize();
    }


    static final class ComboState {
        int count = 0;
        long lastHitMs = 0;
    }

    static final class InputState {
        long lastJumpMs = 0;
    }
}
