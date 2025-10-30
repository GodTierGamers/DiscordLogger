// -----------------------------------------------------------------------------
// DiscordLogger – generator.config.js
// This file is meant to be tiny and environment-aware.
// If your site is served from /docs, set window.DL_BASE = '/docs' *here*.
// If it's served from the root (/), leave it as ''.
// -----------------------------------------------------------------------------
window.DL_BASE = window.DL_BASE || '';   // change to '/docs' if deployed under /docs

// Optional: proxy URL for webhook tests (your Cloudflare Worker /relay endpoint)
window.DL_PROXY_URL = window.DL_PROXY_URL || "";

// Map plugin → config schema
window.DL_VERSIONS = {
    "2.1.5": { configVersion: "v9" },
    // add next ones here:
    // "2.1.6": { configVersion: "v10" },
    // "2.2.0": { configVersion: "v10" },
};

// Per-config assets (this is what generator.js is trying to fetch)
window.DL_CONFIGS = {
    v9: {
        // these paths MUST match where you actually put the files in the built site
        optionsUrl:  window.DL_BASE + "/assets/configs/v9/options.json",
        templateUrl: window.DL_BASE + "/assets/configs/v9/config.template.yml"
    }
    // add future versions here:
    // v10: {
    //     optionsUrl:  window.DL_BASE + "/assets/configs/v10/options.json",
    //     templateUrl: window.DL_BASE + "/assets/configs/v10/config.template.yml"
    // }
};

// Exact webhook test payload you want (used by generator.js)
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
