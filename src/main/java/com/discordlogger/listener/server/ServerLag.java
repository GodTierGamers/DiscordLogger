package com.discordlogger.listener.server;

import com.discordlogger.lang.Lang;
import com.discordlogger.log.Log;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Warns when the server's tick rate drops.
 *
 * <p>Not a listener: there is no lag event, so this samples. A task scheduled every
 * 20 ticks should run once a second; however much longer it actually takes is the
 * shortfall, and the ratio is the tick rate.
 *
 * <p><strong>The rate is measured rather than asked for, deliberately.</strong>
 * {@code Bukkit.getTPS()} is a Spigot addition, and the {@code bukkit-api} CI job
 * compiles this plugin against pure Bukkit precisely so one such call cannot slip
 * in and quietly end support for CraftBukkit. Measuring costs a single scheduled
 * task and works everywhere back to 1.8.
 *
 * <p>Rate limited for the same reason {@code OpAlert} is: a struggling server stays
 * struggling, and an unthrottled warning would post every second of an outage and be
 * muted within a minute.
 */
public final class ServerLag {
    /**
     * The clock, animated, served from the site like every other icon.
     *
     * <p>A GIF rather than a still because Discord animates one in a thumbnail,
     * and because every frame of this one still reads as a clock -- which matters
     * for the readers whose autoplay is off or who run Reduced Motion, and who see
     * only the first frame.
     */
    private static final String THUMB_LAG =
            "https://discordlogger.godtiergamers.xyz/assets/icons/events/lag.gif";

    private static final long PERIOD_TICKS = 20L;
    private static final double NOMINAL_TPS = 20.0;

    private final JavaPlugin plugin;
    private long lastSampleNanos = 0L;
    private long lastWarnMillis = 0L;
    private int taskId = -1;

    public ServerLag(JavaPlugin plugin) { this.plugin = plugin; }

    public void start() {
        stop();
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin, this::sample, PERIOD_TICKS, PERIOD_TICKS);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        lastSampleNanos = 0L;
    }

    private void sample() {
        final long now = System.nanoTime();
        final long previous = lastSampleNanos;
        lastSampleNanos = now;

        // The first run has nothing to measure against, and the gap across a reload
        // or a freeze would read as a tick rate near zero.
        if (previous == 0L) return;
        if (!plugin.getConfig().getBoolean("log.server.lag.enabled", true)) return;

        final double elapsedMs = (now - previous) / 1_000_000.0;
        if (elapsedMs <= 0) return;
        final double tps = Math.min(NOMINAL_TPS, (PERIOD_TICKS * 1000.0) / elapsedMs);

        final double threshold = number("log.server.lag.threshold_tps", 15.0);
        if (tps >= threshold) return;

        final long cooldownMs = (long) (number("log.server.lag.cooldown_minutes", 10.0) * 60_000L);
        final long millis = System.currentTimeMillis();
        if (lastWarnMillis != 0L && millis - lastWarnMillis < cooldownMs) return;
        lastWarnMillis = millis;

        final List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field(Lang.text("discord.lag.tps-field"), round(tps), true));
        fields.add(new Log.Field(Lang.text("discord.lag.threshold-field"), round(threshold), true));
        fields.add(new Log.Field(Lang.text("discord.lag.players-field"),
                String.valueOf(Bukkit.getOnlinePlayers().size()), true));

        Log.eventFieldsWithThumb(
                "server_lag", Lang.text("discord.lag.title"), null, fields, THUMB_LAG);
    }

    /**
     * These ship as strings so the website generator can offer them as text inputs,
     * the same shape {@code expiry_time} uses. A value that cannot be read falls back
     * rather than disabling the check silently.
     */
    private double number(String path, double fallback) {
        final String raw = plugin.getConfig().getString(path, String.valueOf(fallback));
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException | NullPointerException ex) {
            return fallback;
        }
    }

    static String round(double v) {
        return String.valueOf(Math.round(v * 10.0) / 10.0);
    }
}
