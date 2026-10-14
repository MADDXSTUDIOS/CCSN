# CC: Servers & Networking

Enterprise infrastructure for CC:Tweaked on NeoForge 1.21.1: NAS storage,
network switches, server racks and tiered hardware.

**Status: v0.1.0, first block (NAS) only. Untested build.**

## First-time setup (Windows)

1. Install **IntelliJ IDEA Community** (free) from jetbrains.com.
2. Install **Java 21** (Eclipse Temurin 21 JDK from adoptium.net).
3. Download the official **NeoForge 1.21.1 ModDevGradle MDK**:
   github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle → Code → Download ZIP.
4. From the MDK zip, copy these into this `ccsn` folder:
   - `gradlew`
   - `gradlew.bat`
   - the whole `gradle` folder
   (These are Gradle's launcher files. I couldn't generate them from my end.)
5. In IntelliJ: **File → Open** → pick the `ccsn` folder. Let it import
   (first time downloads Minecraft and takes a while).
6. Open the **Gradle** panel (elephant icon, right side) →
   `ccsn → Tasks → mod development → runClient`.

Minecraft launches with CC:Tweaked and CC: Servers & Networking loaded.

### If the import fails with a version error
Open the MDK's `build.gradle` and `gradle.properties` and copy over:
- the `net.neoforged.moddev` version → into this project's `build.gradle`
- `neo_version` → into this project's `gradle.properties`

Then reload Gradle. Anything else, paste the error back to Claude.

## Testing the NAS

1. Creative inventory → **CC: Servers & Networking** tab → **NAS**.
2. Place it next to an Advanced Computer.
3. Copy `lua/nastest.lua` onto the computer (or paste it in with `edit nastest`)
   and run `nastest`.
4. Break the NAS, place it somewhere else and check your test file survived.
5. Also test it over a wired modem network, and with two computers on the
   same network: both should see the same files.

## NAS Lua API (peripheral type `nas`)

| Function | Returns |
|---|---|
| `getId()` | Unique ID (survives breaking the block) |
| `getLabel()` / `setLabel(text?)` | Label, max 32 chars |
| `getMountPath()` | Where it's mounted on this computer, e.g. `nas` |
| `getCapacity()` | Total bytes (server config, default 32 MiB) |
| `getFreeSpace()` | Free bytes |

Files are stored on the server in `world/computercraft/ccsn_nas/<id>/`.

## Building a release jar
Gradle panel → `build → build`. The jar lands in `build/libs/`.
