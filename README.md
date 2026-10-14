# CC: Servers & Networking

Enterprise infrastructure for CC:Tweaked on NeoForge 1.21.1: NAS storage with
hot-swap drives, and (coming) network switches, server racks and blades.

**Status: v0.2.0 (test build).** Requires CC:Tweaked 1.120.x.

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
  modems) as `nas`, `nas2`, `nas3`...
- Breaking the NAS pops the drives out, like a chest.

Files are stored on the server in `world/computercraft/ccsn_drives/<drive id>/`.

## NAS Lua API (peripheral type `nas`)

Bays are numbered 1-4, top to bottom.

| Function | Returns |
|---|---|
| `getBayCount()` | `4` |
| `getDrive(bay)` | `{ bay, tier, capacity, free, mount, id, label }` or `nil` if empty |
| `getDrives()` | Table of the above, keyed by bay number |
| `getMountPath(bay)` | Where that drive is mounted on this computer, e.g. `"nas2"` |
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
- See `DESIGN.md` for the full plan and roadmap.
