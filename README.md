# BBS LX

An animation studio for Minecraft: build a scene, animate it, preview it, and export it to video with sound.

This is a fork of [BBS mod](https://github.com/mchorse/bbs-mod) by McHorse, which is **archived**. Upstream stopped at version 1.7.7 in December 2025 and takes no more fixes, which is why this fork exists: it is where the remaining work happens.

The code, and the credit for it, is McHorse's. Released under the MIT license, see [LICENSE](LICENSE). Pull requests go here, not to upstream.

## Requirements

| | |
| --- | --- |
| Minecraft | 1.20.4 |
| Fabric Loader | 0.15.0 or newer |
| Fabric API | 0.91.1+1.20.4 |
| Java | 17 |

## Installing

Drop the jar into `mods/` alongside the Fabric API.

Your work lives in `config/bbs/`: films in `config/bbs/assets/films`, models in `config/bbs/assets/models`, and settings in `config/bbs/settings`. **Back that folder up.** It is the only copy.

## What is in here

Starting from 1.7.7, the focus so far has been on keeping what you already have safe and working:

* **Saves are atomic.** A file is written to a temporary file and moved into place, so an interrupted save leaves the previous version intact instead of a half-written one. This matters most with autosave, which happens without you doing anything.
* **Ids and asset paths stay inside their folders.** A name coming from the network cannot write or read outside `config/bbs` or the assets folder.
* **Sizes that arrive over the network are checked** before anything is allocated, so a crafted packet cannot take the server down.
* **A film cannot run commands with the rights of an operator.** The command clip of a film runs with the permissions of the player who asked for it, so a player without permissions cannot make the server do what an operator can.
* **A file that lies about its size cannot make the game reserve hundreds of megabytes.** Sounds, voxel models and images are read for what they really are, and the limits are far above a real file.
* **Silent data loss was made visible.** An unreadable clip is now reported instead of quietly dropped, because autosave would otherwise write the film back without it and turn a temporary loss into a permanent one. The same goes for a camera expression the parser refuses: it is kept as text instead of being erased by the next save.
* **A model keeps its animations** when it is written to a file, which it did not before.
* **Film backups are reachable.** Saving already kept previous versions in a folder next to each film; you can now list them and restore one from the film list.

On the horizon: keyframe layers and automatic keyframing, and the model and texture editors, which are where the gap against current community versions is widest.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). It carries the code style rules that all inherited code follows, plus what this fork adds: pull requests are mandatory through a protected `main`, the four CI checks have to pass, and claims of "works" have to say whether they were checked in game or not.

Because this mod renders, handles input and patches Minecraft internals with mixins, a green build proves only that the Java parses. Changes are verified in game before they are considered done.

## Credits

* **McHorse** — BBS mod, the original and the source of this fork.
* [BBS mod on Modrinth](https://modrinth.com/mod/bbs-mod/) and on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/bbs-mod), for the reference pages.
* Everyone listed in the `authors` and `contributors` blocks of `fabric.mod.json`, and the translators whose work ships in the assets.

The attribution in `fabric.mod.json` — the authors, the contributors and the contact — is left exactly as upstream wrote it. Only the name and the description are this fork's.

## Third-party licenses

The jar carries only this mod's own code and its own assets, under the MIT license above. The libraries it needs are separate and carry their own licenses:

| | License |
| --- | --- |
| Fabric Loader, Fabric API | Apache-2.0 |
| Sodium | LGPL-3.0 |
| Iris, Distant Horizons | LGPL-3.0 |
| Indium | Apache-2.0 |

Iris, Distant Horizons and Indium are optional and are only compiled against.