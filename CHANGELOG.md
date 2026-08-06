# Changelog

## [2.2.1](https://github.com/GodTierGamers/DiscordLogger/compare/v2.2.0...v2.2.1) (2026-08-06)


### 🐛 Fixes

* **badge:** use shields' stock GitHub downloads badge ([#152](https://github.com/GodTierGamers/DiscordLogger/issues/152)) ([cfa4a18](https://github.com/GodTierGamers/DiscordLogger/commit/cfa4a184f3251cae7186c470d91526e7e105d298))
* **docs:** update the Jekyll toolchain gems to clear 23 advisories ([#160](https://github.com/GodTierGamers/DiscordLogger/issues/160)) ([158795c](https://github.com/GodTierGamers/DiscordLogger/commit/158795cafb97de276522ff94082063b5712f83e4))
* **release:** bump the docs version inside the release PR ([#153](https://github.com/GodTierGamers/DiscordLogger/issues/153)) ([bc53631](https://github.com/GodTierGamers/DiscordLogger/commit/bc53631d9ed805232d8a678bc0064dc2cd648d5f))
* **release:** send Modrinth the project id, not the slug ([#150](https://github.com/GodTierGamers/DiscordLogger/issues/150)) ([0597372](https://github.com/GodTierGamers/DiscordLogger/commit/05973729edd75fc3e0b3806fed21a04cb748a4dc))
* **release:** tag Modrinth versions as paper and purpur ([#155](https://github.com/GodTierGamers/DiscordLogger/issues/155)) ([b4897bc](https://github.com/GodTierGamers/DiscordLogger/commit/b4897bc410f7d5bdb8f68a30210c84151d2ff35a))


### 📝 Docs

* record the block-logging design and two silent failures in TODO ([#157](https://github.com/GodTierGamers/DiscordLogger/issues/157)) ([0d05774](https://github.com/GodTierGamers/DiscordLogger/commit/0d05774abad7742ed10e828419cbba6d8bf6d45d))
* record the post-2.2.0 plan in TODO ([#154](https://github.com/GodTierGamers/DiscordLogger/issues/154)) ([01090ae](https://github.com/GodTierGamers/DiscordLogger/commit/01090ae1580336fdc2fe9818fe8e1f57cd892db6))
* record the Velocity design and its two prerequisites in TODO ([#156](https://github.com/GodTierGamers/DiscordLogger/issues/156)) ([fad0f22](https://github.com/GodTierGamers/DiscordLogger/commit/fad0f22eeeb16a5bdb9d756cc52f375ce6ec1f8b))


### 🧰 Maintenance

* bump org.apache.maven.plugins:maven-surefire-plugin from 3.5.2 to 3.5.6 ([#158](https://github.com/GodTierGamers/DiscordLogger/issues/158)) ([1819eaf](https://github.com/GodTierGamers/DiscordLogger/commit/1819eafd0ad10ebbae80a6711ef8205266b015e6))
* bump org.junit.jupiter:junit-jupiter from 5.11.4 to 6.1.2 ([#159](https://github.com/GodTierGamers/DiscordLogger/issues/159)) ([ae2b761](https://github.com/GodTierGamers/DiscordLogger/commit/ae2b761065a9e789e783032d8c7f91b28fd4c90a))

## [2.2.0](https://github.com/GodTierGamers/DiscordLogger/compare/v2.1.6...v2.2.0) (2026-08-03)


### ✨ Features

* add bStats metrics and fix badge sync markers breaking Markdown ([#123](https://github.com/GodTierGamers/DiscordLogger/issues/123)) ([a7dc518](https://github.com/GodTierGamers/DiscordLogger/commit/a7dc5185c54136d8aedb69bff78c152e367afc57))
* add SEO metadata, sitemap and social previews to the website ([#125](https://github.com/GodTierGamers/DiscordLogger/issues/125)) ([2ae2c98](https://github.com/GodTierGamers/DiscordLogger/commit/2ae2c98ae80e51346b508ec3de9a4a1ff9cb2216))
* **command:** add /discordlogger webhook, and prefix the nightly chat notice ([#130](https://github.com/GodTierGamers/DiscordLogger/issues/130)) ([b2e4dcd](https://github.com/GodTierGamers/DiscordLogger/commit/b2e4dcd41507c20f8a2109c749e0707d27f641b0))
* **config:** refuse to downgrade a newer config, and add /discordlogger regen ([#129](https://github.com/GodTierGamers/DiscordLogger/issues/129)) ([3a82f8b](https://github.com/GodTierGamers/DiscordLogger/commit/3a82f8b194e26cb45bd252992bb4e9bdf52ea99f))
* **death:** optionally include coordinates in death messages ([#133](https://github.com/GodTierGamers/DiscordLogger/issues/133)) ([8c37f90](https://github.com/GodTierGamers/DiscordLogger/commit/8c37f90c3eeee6fda524b9ebad18e751e5d725d9))
* **docs:** offer only stable config schemas, with a footnote instead of a toggle ([#134](https://github.com/GodTierGamers/DiscordLogger/issues/134)) ([ce810e3](https://github.com/GodTierGamers/DiscordLogger/commit/ce810e349ca66337afba6b8ac413ef015404d6da))
* **docs:** redesign the site and bring every page up to date ([#147](https://github.com/GodTierGamers/DiscordLogger/issues/147)) ([4c68e36](https://github.com/GodTierGamers/DiscordLogger/commit/4c68e36fa753bf334aea116db414536990a7ff34))
* fail with a clear message on non-Paper servers instead of a stack trace ([#112](https://github.com/GodTierGamers/DiscordLogger/issues/112)) ([f765519](https://github.com/GodTierGamers/DiscordLogger/commit/f765519a9845b7a8831e93ed06e0b33bd5788994))
* **filters:** add nine more filters covering the noisiest events ([#146](https://github.com/GodTierGamers/DiscordLogger/issues/146)) ([c673c24](https://github.com/GodTierGamers/DiscordLogger/commit/c673c242b3b5b44ef8b1863af652f2a9629c613a))
* **filters:** skip specific commands, players, worlds and chat content ([#140](https://github.com/GodTierGamers/DiscordLogger/issues/140)) ([3dc0df8](https://github.com/GodTierGamers/DiscordLogger/commit/3dc0df872855332739fc24e7482eefdd5d25a8af))
* **join:** flag Bedrock players on join ([#136](https://github.com/GodTierGamers/DiscordLogger/issues/136)) ([4ebc550](https://github.com/GodTierGamers/DiscordLogger/commit/4ebc550490f8ab4d3e530b7cb1cadc2b6cf8c6e7))
* **lang:** move every player-facing string into lang.yml ([#142](https://github.com/GodTierGamers/DiscordLogger/issues/142)) ([0bf3779](https://github.com/GodTierGamers/DiscordLogger/commit/0bf3779ec070742b575052b0be2281686a3d2895))
* overhaul release workflow with release-please and a nightly beta channel ([#85](https://github.com/GodTierGamers/DiscordLogger/issues/85)) ([db719a3](https://github.com/GodTierGamers/DiscordLogger/commit/db719a3f515447611925914829c9d42919911892))
* propagate version values from pom.xml to every location automatically ([#122](https://github.com/GodTierGamers/DiscordLogger/issues/122)) ([09c72a2](https://github.com/GodTierGamers/DiscordLogger/commit/09c72a2cf5763f97cedc44b03f983f2650e8e2eb))
* queue webhook sends and respect Discord rate limits ([#118](https://github.com/GodTierGamers/DiscordLogger/issues/118)) ([6ec28df](https://github.com/GodTierGamers/DiscordLogger/commit/6ec28df2f0dfacb96fe520c6f61d1fbf414cb691))
* **release:** publish stable releases to Modrinth and Hangar automatically ([#126](https://github.com/GodTierGamers/DiscordLogger/issues/126)) ([8a9f80d](https://github.com/GodTierGamers/DiscordLogger/commit/8a9f80ded5f1b5a41254fa42240a865b4ac97469))
* rewrite config generator with per-version isolation and site-wide beta awareness ([#104](https://github.com/GodTierGamers/DiscordLogger/issues/104)) ([ba290f3](https://github.com/GodTierGamers/DiscordLogger/commit/ba290f3e349b1ddf4e4d1aa1443c7077c0e19052))
* **webhook:** route individual events to their own Discord channel ([#138](https://github.com/GodTierGamers/DiscordLogger/issues/138)) ([39eea41](https://github.com/GodTierGamers/DiscordLogger/commit/39eea41ddff5c7f540eef6c5efbbdffe29e0de0e))


### 🐛 Fixes

* default teleport, gamemode and explosion logging to on ([#110](https://github.com/GodTierGamers/DiscordLogger/issues/110)) ([bc6c707](https://github.com/GodTierGamers/DiscordLogger/commit/bc6c70759d8755a8e0b06144d010a00af5c7cb5d))
* **release:** explain Modrinth's 401 and document the scope --check-auth needs ([#127](https://github.com/GodTierGamers/DiscordLogger/issues/127)) ([996e8d0](https://github.com/GodTierGamers/DiscordLogger/commit/996e8d0dc76441d12164352b8ab789935c8ce576))
* **release:** stamp lang.yml's trailer alongside config.yml ([#145](https://github.com/GodTierGamers/DiscordLogger/issues/145)) ([f81104a](https://github.com/GodTierGamers/DiscordLogger/commit/f81104af1614ecf6b70bcc9f51b5a010417e5ec1))
* remove invalid Liquid filter syntax from nav active-state check ([#111](https://github.com/GodTierGamers/DiscordLogger/issues/111)) ([8faa144](https://github.com/GodTierGamers/DiscordLogger/commit/8faa1443b83749f14e93b1093abae805a5d96ae0))
* sync stale generator hint in shipped config, add drift guard + dictionary ([#106](https://github.com/GodTierGamers/DiscordLogger/issues/106)) ([da7f0ba](https://github.com/GodTierGamers/DiscordLogger/commit/da7f0ba92af63cb37586f7d3f2837c2da807ff52))


### 📝 Docs

* add maintainer-owned TODO.md and its protocol ([#109](https://github.com/GodTierGamers/DiscordLogger/issues/109)) ([4d1ccde](https://github.com/GodTierGamers/DiscordLogger/commit/4d1ccdee094cad0a311f3c64a22b782bcc4c1691))
* bring the public docs up to what 2.2.0 actually ships ([#149](https://github.com/GodTierGamers/DiscordLogger/issues/149)) ([892a9f0](https://github.com/GodTierGamers/DiscordLogger/commit/892a9f04db096e8a41e0232ec966ebc016dcd4de))
* define when a config schema version opens and freezes ([#117](https://github.com/GodTierGamers/DiscordLogger/issues/117)) ([984574d](https://github.com/GodTierGamers/DiscordLogger/commit/984574d34bc498a4340aeaff2e62a1aeef74f680))
* drop Spigot references and stop hardcoding versions in docs pages ([#113](https://github.com/GodTierGamers/DiscordLogger/issues/113)) ([806f351](https://github.com/GodTierGamers/DiscordLogger/commit/806f351630a42225d78337dbc082e7500d71174c))
* drop the website redesign and docs pass from TODO ([#148](https://github.com/GodTierGamers/DiscordLogger/issues/148)) ([a043d6a](https://github.com/GodTierGamers/DiscordLogger/commit/a043d6a53acdab3a7aa85dbbf988f2d2cad17b13))
* expand the version-reference audit into a categorised task ([#121](https://github.com/GodTierGamers/DiscordLogger/issues/121)) ([238e478](https://github.com/GodTierGamers/DiscordLogger/commit/238e4787ee29d3bde9316f6e73bf5d92ec10c3bc))
* refresh README, CONTRIBUTING, and issue templates for the new workflow ([#101](https://github.com/GodTierGamers/DiscordLogger/issues/101)) ([0775a16](https://github.com/GodTierGamers/DiscordLogger/commit/0775a16a453f18f12226e5801822f9fea5316646))
* release PR merges require explicit maintainer instruction ([55de63c](https://github.com/GodTierGamers/DiscordLogger/commit/55de63c1554778f45af3cc5ad638a30cc9ae573c))
* remove completed rate limiter item from TODO.md ([#119](https://github.com/GodTierGamers/DiscordLogger/issues/119)) ([abd5e62](https://github.com/GodTierGamers/DiscordLogger/commit/abd5e62ffd00766838c55e0365232078570fe978))
* remove hand-typed version examples and close out the version audit ([#124](https://github.com/GodTierGamers/DiscordLogger/issues/124)) ([bdb2e3d](https://github.com/GodTierGamers/DiscordLogger/commit/bdb2e3d2eb2c46c01bc80511c8f0c9eee4680142))
* remove the completed Bedrock indicator from TODO.md ([#137](https://github.com/GodTierGamers/DiscordLogger/issues/137)) ([011cac7](https://github.com/GodTierGamers/DiscordLogger/commit/011cac75b6cbd7e1b5eb54d18e2697f92c7b0d71))
* remove the completed lang.yml item from TODO.md ([#143](https://github.com/GodTierGamers/DiscordLogger/issues/143)) ([eeaf402](https://github.com/GodTierGamers/DiscordLogger/commit/eeaf40214427fa8c3763430c597f458b79622e87))
* remove the completed log filtering item from TODO.md ([#141](https://github.com/GodTierGamers/DiscordLogger/issues/141)) ([64b7e74](https://github.com/GodTierGamers/DiscordLogger/commit/64b7e7408e0eae31a67f2f53826f9626df058d9a))
* remove the completed webhook routing item from TODO.md ([#139](https://github.com/GodTierGamers/DiscordLogger/issues/139)) ([acb8d59](https://github.com/GodTierGamers/DiscordLogger/commit/acb8d5910a9281500eb4b8d7eb4bee743af03deb))
* split AGENTS.md into AI-only reference, add human-facing ARCHITECTURE.md ([#107](https://github.com/GodTierGamers/DiscordLogger/issues/107)) ([264fd89](https://github.com/GodTierGamers/DiscordLogger/commit/264fd89bd4323ec18ce821016ea81f763ccf254a))
* stop leaking repo internals into user-facing config files ([#114](https://github.com/GodTierGamers/DiscordLogger/issues/114)) ([829f7e6](https://github.com/GodTierGamers/DiscordLogger/commit/829f7e68d2768dafe486f7859c98dab9951e0098))


### 🧰 Maintenance

* bump actions/checkout from 4 to 7 ([#97](https://github.com/GodTierGamers/DiscordLogger/issues/97)) ([af0fa84](https://github.com/GodTierGamers/DiscordLogger/commit/af0fa840af1d9e636f5014ee3d19a7816bd141ed))
* bump actions/setup-java from 4 to 5 ([#96](https://github.com/GodTierGamers/DiscordLogger/issues/96)) ([256e5ed](https://github.com/GodTierGamers/DiscordLogger/commit/256e5edafd1c8239ff7cecbb9e1ae3f27281ae93))
* bump actions/upload-artifact from 4 to 7 ([#90](https://github.com/GodTierGamers/DiscordLogger/issues/90)) ([c073c9a](https://github.com/GodTierGamers/DiscordLogger/commit/c073c9a5165926e2885b10144a7e1794e645309b))
* bump amannn/action-semantic-pull-request from 5 to 6 ([#93](https://github.com/GodTierGamers/DiscordLogger/issues/93)) ([fbbde9a](https://github.com/GodTierGamers/DiscordLogger/commit/fbbde9aee7c59bc8d8fe26e3ce5cffee85cf6644))
* bump dorny/paths-filter from 3 to 4 ([#88](https://github.com/GodTierGamers/DiscordLogger/issues/88)) ([93274b8](https://github.com/GodTierGamers/DiscordLogger/commit/93274b8998ba07cfc53cbf8953f45f42261f1f76))
* bump googleapis/release-please-action from 4 to 5 ([#86](https://github.com/GodTierGamers/DiscordLogger/issues/86)) ([56c0cc4](https://github.com/GodTierGamers/DiscordLogger/commit/56c0cc4b932651d7d666e552c13c735417782768))
* bump org.apache.maven.plugins:maven-compiler-plugin from 3.13.0 to 3.15.0 ([#94](https://github.com/GodTierGamers/DiscordLogger/issues/94)) ([57f37d9](https://github.com/GodTierGamers/DiscordLogger/commit/57f37d9315fc6c08ecc129c14346c6af20a2d7d1))
* bump org.apache.maven.plugins:maven-shade-plugin from 3.5.0 to 3.6.2 ([#92](https://github.com/GodTierGamers/DiscordLogger/issues/92)) ([5516c81](https://github.com/GodTierGamers/DiscordLogger/commit/5516c818c36094b0d26ca8f2cac1417244afb997))
* bump org.yaml:snakeyaml from 2.2 to 2.6 ([#87](https://github.com/GodTierGamers/DiscordLogger/issues/87)) ([c16b891](https://github.com/GodTierGamers/DiscordLogger/commit/c16b891e1c42f6a21af2c305817187daf2de08c0))
* bump softprops/action-gh-release from 2 to 3 ([#95](https://github.com/GodTierGamers/DiscordLogger/issues/95)) ([a0de6cf](https://github.com/GodTierGamers/DiscordLogger/commit/a0de6cfb92749660ab7f899bd209965b94d7f1d4))
* bump webrick from 1.9.1 to 1.9.2 in /docs ([#89](https://github.com/GodTierGamers/DiscordLogger/issues/89)) ([dca3624](https://github.com/GodTierGamers/DiscordLogger/commit/dca362471d37f4bcad0bd7de64e28c1ea9316f7c))
* **docs:** delete the unused webhook-test proxy Worker ([#128](https://github.com/GodTierGamers/DiscordLogger/issues/128)) ([a556ef7](https://github.com/GodTierGamers/DiscordLogger/commit/a556ef783e3a494a5f6f6c9b5e95ace159c6fb2c))
* release 2.2.0 ([#100](https://github.com/GodTierGamers/DiscordLogger/issues/100)) ([bc54444](https://github.com/GodTierGamers/DiscordLogger/commit/bc544449766e48a90ac45de95da076aa7d7cd5fe))
* remove the automated test suite ([#144](https://github.com/GodTierGamers/DiscordLogger/issues/144)) ([a8b761e](https://github.com/GodTierGamers/DiscordLogger/commit/a8b761e29c64a6e8fc4e646f4963c86e5213379c))
* reset release spec (main) ([042c9ba](https://github.com/GodTierGamers/DiscordLogger/commit/042c9ba9b8f59cfb20c2e94962299a8dfdd8fdd3))
* retarget next release to 2.1.7 ([3f05438](https://github.com/GodTierGamers/DiscordLogger/commit/3f054387013f02a93778ccd80e4ad6b080085e0a))
* retarget next release to 2.2.0 ([#120](https://github.com/GodTierGamers/DiscordLogger/issues/120)) ([e76a957](https://github.com/GodTierGamers/DiscordLogger/commit/e76a9579e9ed786a855edbbb9263a0c46cae7edd))
* retarget next release to 2.2.0 and record planned work ([#116](https://github.com/GodTierGamers/DiscordLogger/issues/116)) ([65c0b79](https://github.com/GodTierGamers/DiscordLogger/commit/65c0b79f914fe47a18ff0e52a6ebd4e5f5c8dfd1))
* stop generating and tracking dependency-reduced-pom.xml ([#108](https://github.com/GodTierGamers/DiscordLogger/issues/108)) ([998ce60](https://github.com/GodTierGamers/DiscordLogger/commit/998ce600d4d48f984f39a7f27509eefcb584f3b5))
* update Paper API to 26.2 and target Java 25 ([#115](https://github.com/GodTierGamers/DiscordLogger/issues/115)) ([04edb62](https://github.com/GodTierGamers/DiscordLogger/commit/04edb6278140165484d43fb1fd22e23bc9187533))


### ⚙️ CI/CD

* disable release-please SNAPSHOT flow, pin plain v-tags ([#99](https://github.com/GodTierGamers/DiscordLogger/issues/99)) ([134735a](https://github.com/GodTierGamers/DiscordLogger/commit/134735afac2453916429f95a89feed0d6ef67939))
* honor Release-As footers in nightly version computation ([#103](https://github.com/GodTierGamers/DiscordLogger/issues/103)) ([bcd6973](https://github.com/GodTierGamers/DiscordLogger/commit/bcd6973bbfa99c5798bcd02fe5499341016f47cc))
