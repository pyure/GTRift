# GT Rift

A [GregTech-Modern (GTCEu)](https://github.com/GregTechCEu/GregTech-Modern) addon adding a
player-triggered mob invasion event (the "Rift Beacon"): charge a machine-tiered multiblock, hold off
a timed wave of invading mobs, and feed what they drop back into GT's own ore-processing chain.

Largely implemented through my preferred AI-assisted ideate/design/plan/implement life cycle.

## Status

Early, actively-developed solo project (`0.1.0`) — not yet published to CurseForge/Modrinth. Expect
rough edges.

## Requirements

- Minecraft 1.20.1
- Forge 47.3.0+
- GregTech-Modern (GTCEu) 7.5.3 — get it from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/gregtech-modern)
  or [Modrinth](https://modrinth.com/mod/gtceu)

## Building from source

No release has been published yet, so building from source is currently the only way to get a
working jar.

```bash
git clone https://github.com/pyure/GTRift.git
cd GTRift
./gradlew build
```

The built jar lands in `build/libs/`.

## Credits

Music ("Blood Moon Advance") by tektoon — also me, under a different handle.

## License

MIT — see [LICENSE](LICENSE).
