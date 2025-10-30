window.DL_PROXY_URL = window.DL_PROXY_URL || "";

/**
 * Map: plugin version -> config schema version
 * If multiple plugin versions share the same config (e.g. v9),
 * just point them all to the same configVersion.
 */
window.DL_VERSIONS = {
    "2.1.5": { configVersion: "v9" },
    // "2.1.6": { configVersion: "v9" },
    // "2.2.0": { configVersion: "v10" },
};

/**
 * Per-config-version metadata.
 * NOTE:
 * - The generator must fetch BOTH:
 *    1) templateUrl  → the YAML skeleton with ASCII + placeholders
 *    2) optionsUrl   → the list of categories/logs (with per-log defaults!)
 * - If you add a new config version, add it here and drop the files in the same folder.
 */
window.DL_CONFIGS = {
    "v9": {
        templateUrl: "/docs/assets/configs/v9/config.template.yml",
        downloadUrl: "/docs/assets/configs/v9/config.yml",
        optionsUrl: "/docs/assets/configs/v9/options.json"
    }

    // "v10": {
    //     templateUrl: "/docs/assets/configs/v10/config.template.yml",
    //     downloadUrl: "/docs/assets/configs/v10/config.yml",
    //     optionsUrl: "/docs/assets/configs/v10/options.json"
    // }
};

/**
 * Exact webhook test payload you wanted.
 * Kept here so the generator can import it without hardcoding.
 */
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
