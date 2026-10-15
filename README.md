# CC: Servers & Networking

Enterprise infrastructure for CC:Tweaked on NeoForge 1.21.1: NAS storage with
hot-swap drives, office network cabling and wall jacks, and (coming) network
switches, server racks and blades.

**Status: v0.3.7 (test build).** Requires CC:Tweaked 1.120.x.

## Getting it

Every push to `main` is built by GitHub Actions and published on the
[Releases page](https://github.com/MADDXSTUDIOS/CCSN/releases) as a test build.
Drop the jar in a NeoForge 1.21.1 instance alongside CC:Tweaked 1.120.x.

## NAS and drives

- **NAS**: a chassis with 4 hot-swap drive bays. Right-click it with a drive to
  slot it into the first free bay, or right-click empty-handed to open the bay GUI.
- **Drives**: Iron (T1, 4 MiB), Gold (T2, 16 MiB), Diamond (T3, 64 MiB).
  Capacities are in the server config. A drive gets its own storage the first
  time it goes into a NAS and keeps its files wherever it goes.
- **Chipsets**: Iron, Gold and Diamond chipsets are the crafting ingredient for
  every tiered item.
- Every drive mounts on every attached computer (directly or over wired
  modems), named after the NAS and bay it's in: `nas_0_bay1`, `nas_1_bay3`...
  A NAS touching the computer uses its side: `nas_left_bay2`. The bay number
  in the name always matches the bay, whatever order drives went in.
- Breaking the NAS pops the drives out, like a chest.

Files are stored on the server in `world/computercraft/ccsn_drives/<drive id>/`.

## Network cable and wall jacks

Still one normal CC wired network: computers and devices plug in with CC's grey
cable and wired modems as usual, and see each other exactly as they would on
plain CC cable. Our cable is the structured cabling in the walls between them.

- **Network Cable** comes in 16 colours. A colour only joins the same colour;
  different colours never join.
- **Up to 6 runs share one block** (server config `max_runs_per_block`, 1-8):
  right-click a cable (or the block it sits on) with a colour it doesn't carry
  yet to add that run. Every colour has its own fixed lane, so runs line up
  block to block whatever they share a block with, and they never pass through
  each other at turns. Breaking the block drops one cable per run.
- **Covers** fit round the runs in a cable block (all inside its own block space):
  - **Cable Tray**: galvanised ladder tray with rungs and slotted rails, runs
    visible. Becomes a slatted riser going up or down. Holds itself up: a
    stand on a floor, brackets (or clips, for risers) to a wall next to it,
    drop rods from a ceiling.
  - **Cable Trunking**: closed white duct, runs hidden. Sits flush against a
    wall or floor next to it, and fills in the bend where it turns round the
    corner of a wall. Ends are only capped where they're open to the air (or
    where two lengths of a different shape meet), so it joins trays and other
    cable cleanly.
  - **Disguise**: right-click a cable with a plain full block (bricks, planks,
    stone, concrete...) and it looks and acts like that block: solid, blocks
    light. Sneak + right-click to place the block next to the cable instead.
  - **Network Wrench**: sneak + right-click a cable to take its cover off (you
    get it back); right-click to see its runs and cover, or a jack's status.
- **Wall Jack**: goes on a wall, floor or ceiling. The back plugs into network
  cable directly behind it, **or through the wall**: run the cable behind a
  one-block wall and put the jack on the room side. CC grey cable or a wired
  modem plugs into its front or any of its 4 sides, shown by a patch lead (in
  CC's own cable texture) with an RJ45 plug in the socket.
  Its label strip shows the colour of the run it's on (dark = not on a run).
  On a bundle it uses the first run; **sneak + right-click** the jack with a
  cable of another colour in the bundle to switch it to that run.
  Jacks next to each other never connect to each other.
- **Status check:** right-click a jack with an empty hand to see its run, what's
  plugged in, and which devices it can see on the network.
- CC's grey cable and modems never connect to our cable directly; the wall
  jack is the only way in.

Example: printer + wired modem → grey cable → wall jack → red run → wall jack
→ grey cable → computer + wired modem. Right-click both modems as normal.

## NAS Lua API (peripheral type `nas`)

Bays are numbered 1-4, top to bottom.

| Function | Returns |
|---|---|
| `getBayCount()` | `4` |
| `getDrive(bay)` | `{ bay, tier, capacity, free, mount, id, label }` or `nil` if empty |
| `getDrives()` | Table of the above, keyed by bay number |
| `getMountPath(bay)` | Where that drive is mounted on this computer, e.g. `"nas_0_bay2"` |
| `setDriveLabel(bay, text?)` | Label a drive (shows as the item name) |
| `getLabel()` / `setLabel(text?)` | The NAS's own label |

**Events** (sent to every attached computer):

| Event | Arguments |
|---|---|
| `nas_drive_insert` | peripheral name, bay |
| `nas_drive_eject` | peripheral name, bay |

## Testing

Get the test program onto a computer:
```
wget https://raw.githubusercontent.com/MADDXSTUDIOS/CCSN/main/lua/nastest.lua nastest
```
- `nastest` writes and reads a file on every drive in every attached NAS.
- `nastest watch` prints insert/eject events live while you swap drives.

## Development

- `tools/gen_assets.py` generates every texture, model and the NAS blockstate.
- `tools/preview_models.py out.png` renders the real model files to check them.
- `tools/gen_net_assets.py` generates the networking textures, models and blockstates.
- `tools/geocheck/run.sh` compiles the real cable geometry against small
  Minecraft stubs and proves no two strands (or a strand and the tray) ever
  overlap; `tools/geocheck/run.sh scenes out/` renders preview scenes with
  `tools/zrender.py`.
- See `DESIGN.md` for the full plan and roadmap.
