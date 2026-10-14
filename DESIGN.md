# CC: Servers & Networking: Design Notes

Mod ID `ccsn` · NeoForge 1.21.1 · CC:Tweaked add-on · Author: MADD X Studios

Enterprise infrastructure for CC:Tweaked: server racks, blade servers, NAS
storage and proper office networking. Generic and public; any world-specific
branding is done with a resource pack in the user's own modpack.

## Ground rules

- **CC version lock:** the mod requires a specific CC:Tweaked minor range
  (currently `1.120.x`). Anything else gets NeoForge's "requires CC:Tweaked"
  screen at boot. The range is bumped when a new CC release is tested.
- **Safety net:** if blade code can't hook into CC internals, only blades
  switch off (the rack GUI says "incompatible CC version"). Everything else
  keeps working.
- **Public API first:** NAS, switch, cables and jacks use CC's public API
  (wired nodes and the wired element capability).
  Only blades need CC internals.
- **Config everything:** capacities and recipes are configurable so pack
  makers can balance them.

## Blocks and items

### NAS (Network Attached Storage) ✅ v0.1 + v0.2 (tested)
- Mounts writable storage on attached computers, directly or over a network.
- Several computers can share one NAS.
- Data and label survive breaking and re-placing.
- Always drops when broken (by hand too); in creative it drops like a shulker
  box once it has storage assigned.
- **v0.2:** becomes a chassis with **4 drive bays** (GUI).
  - Drives are items that hold their own data and move between NASes.
  - Tiers: **T1 4 MiB, T2 16 MiB, T3 64 MiB** (configurable).
  - Each drive mounts as its own folder named after its NAS and bay:
    `nas_0_bay1`, `nas_left_bay2`. Names don't move on reboot or when other
    drives are swapped. (v0.2.0 used `nas`, `nas2`... in insert order, which
    shuffled on reboot with several NASes; fixed in v0.2.1.)
  - Hot-swap events sent to connected computers.
  - Front LEDs per bay.
  - Later: mirror mode (two bays copy each other).
- NAS drives are NAS-only; blades don't use them.

### Server Rack and Blades (v0.4)
- **Single block** (OC style, not a multi-block), **4 blade slots**.
- **Blades are items that are full CC computers.** They keep their computer
  ID, so their files follow the blade between racks.
- Blade internal storage is for the OS and software only; user data lives on
  the NAS.
  - **T1:** basic (black and white), 1 MiB
  - **T2:** advanced (colour), 2 MiB
  - **T3:** advanced, 4 MiB, plus **one expansion slot for network cards
    only** (wireless or ender modem)
- **Auto-boot** when the rack loads.
- **Internal backplane network:** all blades in a rack can talk to each other
  over normal rednet/modem messages.
- **The rack's back face** joins the rack to the outside network.
- **Per-blade "internal only" toggle:** the blade still talks to its
  rack-mates but is invisible to the outside network.
- **Rack GUI:** slots, power on/off/reboot per blade, status lights,
  **Open Console** to use a blade's terminal (no separate KVM block).
- **Front model:** per-blade LEDs (green running, red crashed, off).
- Uses CC internals, so the version lock and safety net apply.

### How networking works (v0.3)

Everything is still **one normal CC wired network**. Computers and devices
(printers, speakers, monitors, NAS...) plug in with CC's own grey cable and
wired modems, and every computer sees every device exactly as it would with
plain CC cable. Our blocks are the tidy office infrastructure in between:

- **CC grey cable = the patch lead** from a device to the wall.
- **Our coloured cable = the structured cabling** in walls, floors and trunking.
- **The switch = the hub** in the comms cupboard that joins the runs.

Rules:
- **Our cable never touches anything of CC's directly.** No grey cable, no
  modems. It isn't exposed to CC at all.
- **The wall jack is the only bridge.** Its back plugs into our cable behind
  it; its front takes CC grey cable or a wired modem.
- **Colours keep runs apart; they are not separate networks.** Like real
  ethernet, each jack has its own run back to the switch. Runs sharing a block
  never merge mid-wall, so the switch knows which jack is on which port.
- **The switch joins runs** into one network by default. Switches can chain
  to extend the network. VLANs are an optional extra to keep rooms apart.
- A run can also go straight jack to jack, no switch (point to point).
- Our own blocks (NAS, switch, rack) will later plug straight into our cable
  without a CC modem: CC's public API lets a wired node share peripherals.

### Network Cable (block)
- Thin (3 px) strand, 16 dye colours, one item per colour.
- Same colour joins same colour; different colours never join.
- **Later (bundling):** up to 4 colours in ONE block space.
- **Later (disguise):** right-click with a full plain block (stone bricks,
  planks, concrete...) and the cable looks and behaves like that block. Wrench
  + sneak-right-click removes it; holding the wrench shows hidden runs.
  - Full plain blocks only (no chests, machines, panes, slabs).

### Wall Jack
- Faceplate on a wall, floor or ceiling, fitted to the block behind it.
- Back: our cable directly behind it, or **through one solid block** (cable run
  behind a one-block wall). The cable shows a stub into the wall. Later,
  disguised cable inside the wall itself also counts as "directly behind".
- Front and 4 sides (never the back): CC grey cable or a CC wired modem. A grey
  patch lead is drawn from the socket to each one so it visibly plugs in.
- Jacks never connect to each other, so runs side by side stay apart.
- Coloured label strip shows which run it's on (dark = not on a run).
- Empty-hand right-click: status (run, through the wall or not, what's plugged
  in, devices visible on the network).

### Network Switch
- The hub: runs come in on several faces, so it has more ports than one cable
  block can carry. Switch to switch extends the network.
- Front: status LED per port. Port 1 uplink.
- **Extras (later build):** GUI with per-port on/off, labels, traffic counters,
  VLAN colours; Lua peripheral to control ports and read stats.

### Patch Panel
- Comms-cupboard version of many jacks in one (half-height).

### Hanging patch cables
- Dropped for now: grey cable plus our runs cover everything. Maybe later for looks.

### Later
- **v0.5:** Router/Gateway, Wireless Access Point, Print Server, cable trays
  and conduit (decorative runs).
- **Later:** fibre (long distance; unloaded chunks are the hard part), badge
  or card reader (tap to log in, tied to directory accounts), chunk-loading
  racks (config option).

## Typical office

Computer + modem → grey cable → Wall Jack → coloured run inside a disguised
wall → Switch (or Patch Panel → Switch) in the comms cupboard. Every computer
sees every device as on a normal CC network. Small setups can run jack to jack.

## Roadmap

| Version | Contents |
|---|---|
| v0.1 | NAS ✅ tested in-game (v0.1.3) |
| v0.2 | NAS drive bays, 3D drives, chipsets, bay GUI, hot-swap ✅ tested in-game (v0.2.1) |
| v0.3.0 | Coloured network cable + Wall Jack 🧪 (v0.3.1: through-wall, side plugs, status) |
| v0.3.x | Bundling (up to 4 runs per block) + disguise + wrench |
| v0.3.x | Network Switch (hub, ports, chaining) |
| v0.3.x | Patch Panel, switch extras (VLANs, GUI, Lua) |
| v0.4 | Server Rack and T1–T3 blades |
| v0.5 | Router, Wireless Access Point, Print Server, cable trays and conduit |
| Later | Fibre, badge reader, chunk-loading racks |

## Release checklist

Done once before a public release, not every version:

- Survival playthrough of every recipe (chipsets, drives, NAS and anything
  added since). Nothing uncraftable, nothing too cheap.
- Default config values sanity-checked.
- Fresh-world test with only CC:Tweaked and CCSN installed.

## Builds

Every push to `main` builds on GitHub Actions. The jar is published on the
Releases page as a "Test build".
