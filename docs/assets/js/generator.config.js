window.DL_PROXY_URL = "";

window.DL_BASE = "/assets/configs";

window.DL_VERSIONS = {
    "2.1.5": { configVersion: "v9" },
    // "2.1.6": { configVersion: "v9" },
};

window.DL_CONFIGS = {
    "v9": {
        // must exist: docs/assets/configs/v9/config.template.yml
        templateUrl: "/assets/configs/v9/config.template.yml",

        // must exist: docs/assets/configs/v9/options.json
        optionsUrl: "/assets/configs/v9/options.json"
    }
};

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
