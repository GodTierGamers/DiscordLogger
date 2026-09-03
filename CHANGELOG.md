# Changelog

## [2.5.0](https://github.com/GodTierGamers/DiscordLogger/compare/v2.4.1...v2.5.0) (2026-09-03)


### ✨ Features

* **bedrock:** read the platform strings from lang, and cover the path ([#244](https://github.com/GodTierGamers/DiscordLogger/issues/244)) ([5f8314c](https://github.com/GodTierGamers/DiscordLogger/commit/5f8314c7820bfda6beeb8cbf124c41ffbfe37e45))


### 🐛 Fixes

* **ci:** let the server JDK install without a signature ([#242](https://github.com/GodTierGamers/DiscordLogger/issues/242)) ([46e08f1](https://github.com/GodTierGamers/DiscordLogger/commit/46e08f1bb739fdd47f8256c630ecd0239d1f652c))
* **plain-text:** stop printing config keys, doubled titles and unseparated fields ([#240](https://github.com/GodTierGamers/DiscordLogger/issues/240)) ([18b1218](https://github.com/GodTierGamers/DiscordLogger/commit/18b1218e5e47b9f53b3dbd4de630b7bf214a96f7))

## [2.4.1](https://github.com/GodTierGamers/DiscordLogger/compare/v2.4.0...v2.4.1) (2026-09-02)


### 🐛 Fixes

**If your server stopped offering updates, this is the release that fixes it.** The update checker built its Minecraft version from `Bukkit.getBukkitVersion()` and cut at the first dash, which on a recent Paper build leaves `26.2.build.71` — not a Minecraft version, and matching nothing in any listing. Affected servers were told the newest release did not support them and went quiet. Two separate faults are fixed here:

* **update:** offer updates again on servers whose version carries a build number ([#239](https://github.com/GodTierGamers/DiscordLogger/pull/239))

* **listing:** drop [HR][/HR] from the Spigot description ([#235](https://github.com/GodTierGamers/DiscordLogger/issues/235)) ([fe40053](https://github.com/GodTierGamers/DiscordLogger/commit/fe4005314e779da0b8a8eb0c3941e0bf737d9a94))
* **release:** claim minor lines on Hangar, not every patch version ([#234](https://github.com/GodTierGamers/DiscordLogger/issues/234)) ([ede62a4](https://github.com/GodTierGamers/DiscordLogger/commit/ede62a4364b95e2e5f9533a1bc5a898fdad0f145))
* **release:** send Hangar and CurseForge version ids they will accept ([#232](https://github.com/GodTierGamers/DiscordLogger/issues/232)) ([d62375b](https://github.com/GodTierGamers/DiscordLogger/commit/d62375be039e150cdb3b500ebe538d3f8cf309b5))
* **release:** survive Hangar rejecting a version, and see CurseForge's catalogue ([#230](https://github.com/GodTierGamers/DiscordLogger/issues/230)) ([21ba09d](https://github.com/GodTierGamers/DiscordLogger/commit/21ba09d33b6a5c480cd61711fb99b48c4f4fbfd6))
* **update:** read the release's own game versions, not the next release's ([#239](https://github.com/GodTierGamers/DiscordLogger/issues/239)) ([bb53c65](https://github.com/GodTierGamers/DiscordLogger/commit/bb53c6586dfbf436d3bd6b26cd709e6ba123ea17))


### 📝 Docs

* link the SpigotMC listing ([#236](https://github.com/GodTierGamers/DiscordLogger/issues/236)) ([25aa791](https://github.com/GodTierGamers/DiscordLogger/commit/25aa7914535d7875971e266c48a2084a50d8cbc4))
* **listing:** add the SpigotMC description ([#233](https://github.com/GodTierGamers/DiscordLogger/issues/233)) ([e6b15f0](https://github.com/GodTierGamers/DiscordLogger/commit/e6b15f0e5d6afe226767d399147fb5b6e2d94311))


### 🧰 Maintenance

* bump actions/cache from 4 to 6 ([#229](https://github.com/GodTierGamers/DiscordLogger/issues/229)) ([676ef18](https://github.com/GodTierGamers/DiscordLogger/commit/676ef18977075065cef1989dc3dc0636598e110d))
* bump actions/checkout from 4 to 7 ([#224](https://github.com/GodTierGamers/DiscordLogger/issues/224)) ([bc32528](https://github.com/GodTierGamers/DiscordLogger/commit/bc32528f1fb31b7a64e871977fb85bfdcfee9ec3))
* bump actions/download-artifact from 4 to 8 ([#222](https://github.com/GodTierGamers/DiscordLogger/issues/222)) ([70a1650](https://github.com/GodTierGamers/DiscordLogger/commit/70a16503d3140317ae16e759aa85df8c16357801))
* bump actions/setup-java from 4 to 6 ([#228](https://github.com/GodTierGamers/DiscordLogger/issues/228)) ([ba5a2d6](https://github.com/GodTierGamers/DiscordLogger/commit/ba5a2d6e4563f395bb63487757f15542df29f992))
* bump actions/upload-artifact from 4 to 7 ([#226](https://github.com/GodTierGamers/DiscordLogger/issues/226)) ([5981513](https://github.com/GodTierGamers/DiscordLogger/commit/5981513a60b35ea5d90c70130953ae5e44217b89))
* bump com.google.code.gson:gson from 2.10.1 to 2.14.0 ([#227](https://github.com/GodTierGamers/DiscordLogger/issues/227)) ([7e9e551](https://github.com/GodTierGamers/DiscordLogger/commit/7e9e551f1f7d54ac1ad6c5c67d39ae50b2bdce6f))
* bump net.kyori:adventure-bom from 4.21.0 to 4.26.1 ([#238](https://github.com/GodTierGamers/DiscordLogger/issues/238)) ([87b7e0c](https://github.com/GodTierGamers/DiscordLogger/commit/87b7e0c9045b9691187de05f83412a5d2c36cb7f))
* **deps:** move the whole Adventure line to 4.21, and refuse 5.x ([#237](https://github.com/GodTierGamers/DiscordLogger/issues/237)) ([4202c88](https://github.com/GodTierGamers/DiscordLogger/commit/4202c886ff61d407485f0b1efcd79a5e87b28fb6))
* **release:** print the whole CurseForge mapping, not the first twelve ([058d40b](https://github.com/GodTierGamers/DiscordLogger/commit/058d40b91794a092101428912ffcabe6237ec4ea))

## [2.4.0](https://github.com/GodTierGamers/DiscordLogger/compare/v2.3.1...v2.4.0) (2026-09-01)


### ✨ Features

* drop the Paper requirement, supporting Bukkit 1.8.0 and up ([#211](https://github.com/GodTierGamers/DiscordLogger/issues/211)) ([0f384a2](https://github.com/GodTierGamers/DiscordLogger/commit/0f384a2a7f6c843d8d4a6e12996254cf1edd652a))
* **metrics:** track downloads per source alongside the bStats data ([#209](https://github.com/GodTierGamers/DiscordLogger/issues/209)) ([9ba5d9a](https://github.com/GodTierGamers/DiscordLogger/commit/9ba5d9a95f061f3a355c9db6af61d9a484368b7f))


### 🐛 Fixes

* **embeds:** put the version back in the footer ([#210](https://github.com/GodTierGamers/DiscordLogger/issues/210)) ([c2942e9](https://github.com/GodTierGamers/DiscordLogger/commit/c2942e955b845f4f85b909d56aa9e82d7887e348))
* **embeds:** use Strings.isBlank so the footer version compiles to Java 8 ([#213](https://github.com/GodTierGamers/DiscordLogger/issues/213)) ([a21dabf](https://github.com/GodTierGamers/DiscordLogger/commit/a21dabf2f0828fcd0ddf54902fa0e0eb80eae728))


### 📝 Docs

* **agents:** correct the working agreement after the Bukkit port ([#219](https://github.com/GodTierGamers/DiscordLogger/issues/219)) ([3e44268](https://github.com/GodTierGamers/DiscordLogger/commit/3e442684492808b88885e4f1973a39178836b0c3))
* correct the platform claims after the Bukkit port ([#216](https://github.com/GodTierGamers/DiscordLogger/issues/216)) ([c152497](https://github.com/GodTierGamers/DiscordLogger/commit/c15249780241a153ecff7e3b92e4ff4d08fd01b9))
* link CurseForge, and clear the shipped items from TODO ([#218](https://github.com/GodTierGamers/DiscordLogger/issues/218)) ([5162265](https://github.com/GodTierGamers/DiscordLogger/commit/5162265ec4c34afa333e8c86713f07e0e88d6f34))
* **listing:** drop the inline images ([#217](https://github.com/GodTierGamers/DiscordLogger/issues/217)) ([3ebaf17](https://github.com/GodTierGamers/DiscordLogger/commit/3ebaf1707988d42131b66f4343a166abbd73ba56))


### 🧰 Maintenance

* **acceptance:** drop the temporary push trigger ([#214](https://github.com/GodTierGamers/DiscordLogger/issues/214)) ([3733284](https://github.com/GodTierGamers/DiscordLogger/commit/3733284643e0b8a762d9eed3615979eacd7e4e42))
* **deps:** fix the stale paper-api ignore, and pin snakeyaml deliberately ([#221](https://github.com/GodTierGamers/DiscordLogger/issues/221)) ([4965c56](https://github.com/GodTierGamers/DiscordLogger/commit/4965c568927675f9d6f4ef7d2f2f91e7a6437fcf))
* name the version keys for Minecraft, not Paper ([#220](https://github.com/GodTierGamers/DiscordLogger/issues/220)) ([be51d83](https://github.com/GodTierGamers/DiscordLogger/commit/be51d8315843e6d08d6ea2e38575d80e16da1de5))
* **release:** let a feature bump the minor version ([#215](https://github.com/GodTierGamers/DiscordLogger/issues/215)) ([4c3e93d](https://github.com/GodTierGamers/DiscordLogger/commit/4c3e93d4207b1c35849563f238edd29a19d9fa7f))

## [2.3.1](https://github.com/GodTierGamers/DiscordLogger/compare/v2.3.0...v2.3.1) (2026-08-28)


### ✨ Features

* add status, test and doctor diagnostics commands ([#198](https://github.com/GodTierGamers/DiscordLogger/issues/198)) ([59bc31c](https://github.com/GodTierGamers/DiscordLogger/commit/59bc31cf49f34b8995d8853f6c637b41420d4604))
* check the configured webhooks still exist at startup ([#187](https://github.com/GodTierGamers/DiscordLogger/issues/187)) ([66a17b7](https://github.com/GodTierGamers/DiscordLogger/commit/66a17b72843f6b84f4a51d8776636e0473716fae))
* **docs:** hide unreleased schemas, and mark superseded ones as legacy ([#195](https://github.com/GodTierGamers/DiscordLogger/issues/195)) ([ecedc8a](https://github.com/GodTierGamers/DiscordLogger/commit/ecedc8a78768c53b6231ed3d9883fcb418c9f346))
* expand PlaceholderAPI placeholders in lang.yml ([#197](https://github.com/GodTierGamers/DiscordLogger/issues/197)) ([e186369](https://github.com/GodTierGamers/DiscordLogger/commit/e1863696a6bde40ea8523513ce1e916d9f2d5d23))
* export bStats charts to CSV, and catch charts that discard their data ([#179](https://github.com/GodTierGamers/DiscordLogger/issues/179)) ([5577f71](https://github.com/GodTierGamers/DiscordLogger/commit/5577f712d108dcc55108ad1f53747b92ce304a96))
* log any plugin's commands with your own events ([#194](https://github.com/GodTierGamers/DiscordLogger/issues/194)) ([5dc0213](https://github.com/GodTierGamers/DiscordLogger/commit/5dc02131c2d9ec2284901148a43f2108bd23016f))
* **metrics:** measure which permission plugin a server runs ([#192](https://github.com/GodTierGamers/DiscordLogger/issues/192)) ([7c09f3a](https://github.com/GodTierGamers/DiscordLogger/commit/7c09f3acb7659f6215168100b96618e0e47ee61f))
* stay silent about vanished players ([#191](https://github.com/GodTierGamers/DiscordLogger/issues/191)) ([5cf6b04](https://github.com/GodTierGamers/DiscordLogger/commit/5cf6b04958ee2e37f2e671905227e0735e871e8e))
* tell staff in game when logging has stopped working ([#205](https://github.com/GodTierGamers/DiscordLogger/issues/205)) ([ed2b296](https://github.com/GodTierGamers/DiscordLogger/commit/ed2b296e1649ba9c2eac39687cbbbd6adf6e0c96))


### 🐛 Fixes

* **commands:** strip Bukkit's plugin:command duplicates from tab-completion ([#190](https://github.com/GodTierGamers/DiscordLogger/issues/190)) ([2699343](https://github.com/GodTierGamers/DiscordLogger/commit/26993432f698d2f0ad7f78219bf7ba6f293134b5))
* **config:** custom: {} could not take rules, and migration corrupted the fix ([#202](https://github.com/GodTierGamers/DiscordLogger/issues/202)) ([dfbc85a](https://github.com/GodTierGamers/DiscordLogger/commit/dfbc85a811e61e7c93716cab1b41e5f4cac625d1))
* **death:** make /kill read as a command on Paper 1.19 ([#175](https://github.com/GodTierGamers/DiscordLogger/issues/175)) ([6f0335c](https://github.com/GodTierGamers/DiscordLogger/commit/6f0335cb9d737fa8f35b0b2f088b742e9d040cdd))
* **docs:** the website's lang.yml said it shipped with 2.1.6 ([#177](https://github.com/GodTierGamers/DiscordLogger/issues/177)) ([4c667c2](https://github.com/GodTierGamers/DiscordLogger/commit/4c667c25f44bef152daa575ec7736ae09722ce12))
* **embeds:** footer named a version, and bans ignored embeds.author ([#201](https://github.com/GodTierGamers/DiscordLogger/issues/201)) ([888b1c6](https://github.com/GodTierGamers/DiscordLogger/commit/888b1c6ada22f4b0c39e398473794ccd81eb9bd4))
* **metrics:** drop two charts that duplicate bStats defaults ([#172](https://github.com/GodTierGamers/DiscordLogger/issues/172)) ([b727524](https://github.com/GodTierGamers/DiscordLogger/commit/b72752459e1e6cafceba42827da0531ebacd48bc))
* **metrics:** proxy_mode reported every Paper server as proxied ([#186](https://github.com/GodTierGamers/DiscordLogger/issues/186)) ([7d6927c](https://github.com/GodTierGamers/DiscordLogger/commit/7d6927c76de28f8116287ac1ccef856be85a046c))
* **metrics:** the store recorded blank rows and a wrong denominator ([#189](https://github.com/GodTierGamers/DiscordLogger/issues/189)) ([6d9b51e](https://github.com/GodTierGamers/DiscordLogger/commit/6d9b51efe29d61d920f72a5bcab41d4b8a4fb2e9))
* **metrics:** webhook_configured reported "Not installed" ([#181](https://github.com/GodTierGamers/DiscordLogger/issues/181)) ([6a10ba2](https://github.com/GodTierGamers/DiscordLogger/commit/6a10ba20fc0c5c566d934492ab0beff562baa46b))
* **moderation:** log bans on servers running a punishment plugin ([#206](https://github.com/GodTierGamers/DiscordLogger/issues/206)) ([789cf0d](https://github.com/GodTierGamers/DiscordLogger/commit/789cf0dcbdd2e0a9ba0fdc189b27c1c7e5bf8f3e))
* **seo:** stop robots.txt blocking the generator's own assets ([#176](https://github.com/GodTierGamers/DiscordLogger/issues/176)) ([fc3b99e](https://github.com/GodTierGamers/DiscordLogger/commit/fc3b99eef3e0660c76cbef9cc5ebf40ea51958af))
* **update:** do not recommend a release this server cannot run ([#207](https://github.com/GodTierGamers/DiscordLogger/issues/207)) ([5bd9630](https://github.com/GodTierGamers/DiscordLogger/commit/5bd96302e337f36e2c3df8d84a30610099f9b758))


### 📝 Docs

* AGENTS.md described a version of this repo that no longer exists ([#178](https://github.com/GodTierGamers/DiscordLogger/issues/178)) ([c2b50da](https://github.com/GodTierGamers/DiscordLogger/commit/c2b50da06d701bc01b6d1a4a8c2b03407cd0728b))
* bring every doc up to date with the last two weeks of work ([#208](https://github.com/GodTierGamers/DiscordLogger/issues/208)) ([8a8c6a4](https://github.com/GodTierGamers/DiscordLogger/commit/8a8c6a4186528165dfbe63092058aa30b1ccf013))
* correct two TODO entries that shipping made wrong ([#174](https://github.com/GodTierGamers/DiscordLogger/issues/174)) ([0297bd6](https://github.com/GodTierGamers/DiscordLogger/commit/0297bd66a844b6ad39a730b40cc0498f7b65c5ed))
* drop two TODO entries that have shipped ([#196](https://github.com/GodTierGamers/DiscordLogger/issues/196)) ([2017b41](https://github.com/GodTierGamers/DiscordLogger/commit/2017b413e5d03e30d85b08ab04544b032d7229e0))
* publish the gallery screenshots, and show them in the README ([#203](https://github.com/GodTierGamers/DiscordLogger/issues/203)) ([6783a63](https://github.com/GodTierGamers/DiscordLogger/commit/6783a630326b568f1e57b18555343031ebda42d7))
* re-encode the banner from source, and keep the source in the repo ([#204](https://github.com/GodTierGamers/DiscordLogger/issues/204)) ([5f921aa](https://github.com/GodTierGamers/DiscordLogger/commit/5f921aadde2da0580bb80be6fde477176afdb51f))
* record the pure-Bukkit question, and correct why it was declined ([#182](https://github.com/GodTierGamers/DiscordLogger/issues/182)) ([b578211](https://github.com/GodTierGamers/DiscordLogger/commit/b578211797a15323efd505060c46bb23fc339969))
* record the update-checker compatibility gap ([#185](https://github.com/GodTierGamers/DiscordLogger/issues/185)) ([5503ade](https://github.com/GodTierGamers/DiscordLogger/commit/5503ade67956daa986f39d41c340306bf80943cd))


### 🧰 Maintenance

* bump actions/setup-python from 6 to 7 ([#200](https://github.com/GodTierGamers/DiscordLogger/issues/200)) ([68b2d6e](https://github.com/GodTierGamers/DiscordLogger/commit/68b2d6eed0792d44947e3937bf26d67b9965d799))
* retarget the release to 2.3.1 ([#183](https://github.com/GodTierGamers/DiscordLogger/issues/183)) ([48e7abc](https://github.com/GodTierGamers/DiscordLogger/commit/48e7abcbf3ec77d7a5f05393ee87aba6a1fb1df0))
* stop commit types deciding the version ([#184](https://github.com/GodTierGamers/DiscordLogger/issues/184)) ([b7caac4](https://github.com/GodTierGamers/DiscordLogger/commit/b7caac4af2faac60ae77848a68d4aeb6f2a38678))


### ⚙️ CI/CD

* snapshot the bStats pie charts twice an hour ([#188](https://github.com/GodTierGamers/DiscordLogger/issues/188)) ([70d17f3](https://github.com/GodTierGamers/DiscordLogger/commit/70d17f3d498219c637385aaea072b5bdbb5a140b))
* store one row per poll instead of one row per slice ([#193](https://github.com/GodTierGamers/DiscordLogger/issues/193)) ([267160a](https://github.com/GodTierGamers/DiscordLogger/commit/267160a4d465c8470fe1247f08798f96d2d1bd63))

## [2.3.0](https://github.com/GodTierGamers/DiscordLogger/compare/v2.2.0...v2.3.0) (2026-08-14)


### ✨ Features

* **metrics:** measure what people do with the plugin, not who uses it ([#166](https://github.com/GodTierGamers/DiscordLogger/issues/166)) ([a51f2ab](https://github.com/GodTierGamers/DiscordLogger/commit/a51f2abfbfe55086314bc58ae5b9d09b42efff1a))
* **metrics:** report whether sending actually works ([#167](https://github.com/GodTierGamers/DiscordLogger/issues/167)) ([4a4d075](https://github.com/GodTierGamers/DiscordLogger/commit/4a4d07513434a51c6ca60670f706f71b79817151))


### 🐛 Fixes

* **badge:** use shields' stock GitHub downloads badge ([#152](https://github.com/GodTierGamers/DiscordLogger/issues/152)) ([cfa4a18](https://github.com/GodTierGamers/DiscordLogger/commit/cfa4a184f3251cae7186c470d91526e7e105d298))
* **docs:** update the Jekyll toolchain gems to clear 23 advisories ([#160](https://github.com/GodTierGamers/DiscordLogger/issues/160)) ([158795c](https://github.com/GodTierGamers/DiscordLogger/commit/158795cafb97de276522ff94082063b5712f83e4))
* **release:** bump the docs version inside the release PR ([#153](https://github.com/GodTierGamers/DiscordLogger/issues/153)) ([bc53631](https://github.com/GodTierGamers/DiscordLogger/commit/bc53631d9ed805232d8a678bc0064dc2cd648d5f))
* **release:** send Modrinth the project id, not the slug ([#150](https://github.com/GodTierGamers/DiscordLogger/issues/150)) ([0597372](https://github.com/GodTierGamers/DiscordLogger/commit/05973729edd75fc3e0b3806fed21a04cb748a4dc))
* **release:** tag Modrinth versions as paper and purpur ([#155](https://github.com/GodTierGamers/DiscordLogger/issues/155)) ([b4897bc](https://github.com/GodTierGamers/DiscordLogger/commit/b4897bc410f7d5bdb8f68a30210c84151d2ff35a))
* support Paper 1.19.4 and newer, not just 26.x ([#164](https://github.com/GodTierGamers/DiscordLogger/issues/164)) ([252312c](https://github.com/GodTierGamers/DiscordLogger/commit/252312c552ae0dc6ac8e924e593ce0f0a7f1bd1d))


### 📝 Docs

* add a page for the queries every event generates ([ae15c0c](https://github.com/GodTierGamers/DiscordLogger/commit/ae15c0c91e601f651ee4a597ffe310f4f0ee6d2b))
* add five guides for features that had no page ([#171](https://github.com/GodTierGamers/DiscordLogger/issues/171)) ([c00b263](https://github.com/GodTierGamers/DiscordLogger/commit/c00b263a4da93ff0c4df31d3d5ab90a8daa98e04))
* add the three guides people actually search for ([#168](https://github.com/GodTierGamers/DiscordLogger/issues/168)) ([813e325](https://github.com/GodTierGamers/DiscordLogger/commit/813e3258458696c7c89570340f2a2a442b2aaf8a))
* fix the metadata search engines actually display ([#169](https://github.com/GodTierGamers/DiscordLogger/issues/169)) ([f6871c7](https://github.com/GodTierGamers/DiscordLogger/commit/f6871c7f6856dbecaaeb5582e160df5f2f82b049))
* record the API floors, the metrics, and close out finished TODO items ([#170](https://github.com/GodTierGamers/DiscordLogger/issues/170)) ([30751a5](https://github.com/GodTierGamers/DiscordLogger/commit/30751a55a38598269865fe935d9427a81676b63a))
* record the block-logging design and two silent failures in TODO ([#157](https://github.com/GodTierGamers/DiscordLogger/issues/157)) ([0d05774](https://github.com/GodTierGamers/DiscordLogger/commit/0d05774abad7742ed10e828419cbba6d8bf6d45d))
* record the post-2.2.0 plan in TODO ([#154](https://github.com/GodTierGamers/DiscordLogger/issues/154)) ([01090ae](https://github.com/GodTierGamers/DiscordLogger/commit/01090ae1580336fdc2fe9818fe8e1f57cd892db6))
* record the Velocity design and its two prerequisites in TODO ([#156](https://github.com/GodTierGamers/DiscordLogger/issues/156)) ([fad0f22](https://github.com/GodTierGamers/DiscordLogger/commit/fad0f22eeeb16a5bdb9d756cc52f375ce6ec1f8b))


### 🧰 Maintenance

* bump org.apache.maven.plugins:maven-surefire-plugin from 3.5.2 to 3.5.6 ([#158](https://github.com/GodTierGamers/DiscordLogger/issues/158)) ([1819eaf](https://github.com/GodTierGamers/DiscordLogger/commit/1819eafd0ad10ebbae80a6711ef8205266b015e6))
* bump org.junit.jupiter:junit-jupiter from 5.11.4 to 6.1.2 ([#159](https://github.com/GodTierGamers/DiscordLogger/issues/159)) ([ae2b761](https://github.com/GodTierGamers/DiscordLogger/commit/ae2b761065a9e789e783032d8c7f91b28fd4c90a))
* bump org.junit.jupiter:junit-jupiter from 6.1.2 to 6.1.3 ([#163](https://github.com/GodTierGamers/DiscordLogger/issues/163)) ([7a7b445](https://github.com/GodTierGamers/DiscordLogger/commit/7a7b445bcb4d6f8f53849e80a55ef896b42bd91e))
* stop Dependabot bumping the Paper API floor ([#165](https://github.com/GodTierGamers/DiscordLogger/issues/165)) ([9037822](https://github.com/GodTierGamers/DiscordLogger/commit/9037822b5ca0574f0b246b3a28dd7625af40bc7a))

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
