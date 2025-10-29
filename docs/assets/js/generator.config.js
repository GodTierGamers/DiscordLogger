// Optional: proxy URL for webhook tests (your Cloudflare Worker /relay endpoint)
// Leave blank to attempt a direct POST (usually blocked by CORS in browsers).
window.DL_PROXY_URL = window.DL_PROXY_URL || "";

// Map plugin → config schema + stock download URL (for future use on the page)
window.DL_VERSIONS = {
    "2.1.5": { configVersion: "v9", downloadUrl: "/docs/assets/configs/v9/config.yml" },
    // "2.1.6": { configVersion: "v9", downloadUrl: "/docs/assets/configs/v9/config.yml" },
};

// Default on/off toggles for v9
window.DL_DEFAULT_TOGGLES = {
    "log.player.join": true,
    "log.player.quit": true,
    "log.player.chat": true,
    "log.player.command": true,
    "log.player.death": true,
    "log.player.teleport": true,
    "log.player.gamemode": true,

    "log.server.start": true,
    "log.server.stop": true,
    "log.server.command": true,
    "log.server.explosion": true,

    "log.moderation.ban": true,
    "log.moderation.unban": true,
    "log.moderation.kick": true,
    "log.moderation.op": true,
    "log.moderation.deop": true,
    "log.moderation.whitelist_toggle": true,
    "log.moderation.whitelist": true
};

// Default colors (v9) — matches your theme (moderation = red, join = green, etc.)
window.DL_DEFAULT_COLORS = {
    "player.join": "#57F287",
    "player.quit": "#ED4245",
    "player.chat": "#5865F2",
    "player.command": "#FEE75C",
    "player.death": "#ED4245",

    "server.start": "#43B581",
    "server.stop":  "#ED4245",
    "server.command":"#EB459E",
    "server.explosion": "#F97316",

    "moderation.ban": "#FF3B30",
    "moderation.unban": "#FF3B30",
    "moderation.kick": "#FF3B30",
    "moderation.op": "#FF3B30",
    "moderation.deop": "#FF3B30",
    "moderation.whitelist_toggle": "#1ABC9C",
    "moderation.whitelist": "#16A085"
};

// Exact webhook test payload you want
window.DL_TEST_EMBED = {
    content: null,
    embeds: [
        {
            title: "DiscordLogger Webhook Test",
            description:
                "Hello, this is a test of your webhook to confirm if it works, if you are seeing this message, it worked.\n\n" +
                "If you did not request a webhook test, confirm with other members of your server if they created a webhook or used an already existing webhook URL, " +
                "if nobody in your server requested this, reset/delete the webhook URL",
            url: "https://discordlogger.godtiergamers.xyz",
            color: 5814783,
            author: {
                name: "DiscordLogger Webhook Test",
                url: "https://discordlogger.godtiergamers.xyz",
                icon_url: "https://files.godtiergamers.xyz/DiscordLogger-Logo-removebg.png"
            },
            footer: {
                text: "DiscordLogger Webhook Test",
                icon_url: "https://files.godtiergamers.xyz/DiscordLogger-Logo-removebg.png"
            },
            timestamp: new Date().toISOString(),
            thumbnail: {
                url: "https://files.godtiergamers.xyz/DiscordLogger-Logo-removebg.png"
            }
        }
    ],
    attachments: []
};
