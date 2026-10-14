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
- **Public API first:** NAS, switch, cables and jacks use CC's public API.
  Only blades need CC internals.
- **Config everything:** capacities and recipes are configurable so pack
  makers can balance them.

## Blocks and items

### NAS (Network Attached Storage) ✅ v0.1 (tested)
- Mounts writable storage on attached computers, directly or over a network.
- Several computers can share one NAS.
- Data and label survive breaking and re-placing.
- Multiple NAS on one computer mount as `nas`, `nas2`, `nas3`...
- Always drops when broken (by hand too); in creative it drops like a shulker
  box once it has storage assigned.
- **v0.2:** becomes a chassis with **4 drive bays** (GUI).
  - Drives are items that hold their own data and move between NASes.
  - Tiers: **T1 4 MiB, T2 16 MiB, T3 64 MiB** (configurable).
  - Each drive mounts as its own folder.
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

### Network Switch (v0.3)
- **Half-height (1U) block**, 8 ports in two rows of 4.
- **Port 1 = uplink** (amber).
- Each port: status LED (green active, red disabled, dark empty) and a
  VLAN colour pixel.
- **GUI:** per-port on/off, labels, traffic counters, VLAN colour.
- **VLANs:** only ports with the same colour talk to each other.
- **Peripheral:** Lua can toggle ports, read stats and so on.
- Block network cables connect to the **back** only (uplink); individual
  ports take **patch cables**.

### Network Cable (block cable, v0.3)
- Thin (about 3 px) coloured strands.
- **Up to 4 colours in ONE block space.** Right-click an existing cable with
  another colour to add it. Strands sit side by side.
- **Each colour only connects to the same colour.** Different networks share
  a block space but never merge.
- **Disguise:** right-click with a full plain block (stone bricks, planks,
  concrete...) and the cable looks exactly like that block and behaves as a
  solid block. Wrench + sneak-right-click removes the disguise. Holding the
  wrench shows disguised cables as outlines.
  - Full plain blocks only (no chests, machines, panes, slabs).
  - Connected-texture blocks from other mods may not connect perfectly.

### Patch Cable (item, v0.3)
- Not a block: a hanging cable between two points (Immersive Engineering
  style), max about 16 blocks.
- Click a **specific port** (detected by where on the face you click, like the
  vanilla chiseled bookshelf), then click the other end.
- One patch cable = one port.

### Wall Jack and Patch Panel (v0.3)
- **Wall Jack:** small plate on a wall or floor. Connects to the cable block
  **directly behind it** (including disguised cables inside the wall).
  Patch cable from jack to computer.
- **Patch Panel:** the comms-cupboard version, many jacks in one (half-height).

### Later
- **v0.5:** Router/Gateway, Wireless Access Point, Print Server, cable trays
  and conduit (decorative runs).
- **Later:** fibre (long distance; unloaded chunks are the hard part), badge
  or card reader (tap to log in, tied to directory accounts), chunk-loading
  racks (config option).

## Typical office

Computer → patch cable → Wall Jack → coloured cable inside a disguised wall →
Patch Panel → short patch cable → Switch port. Small setups can patch
computers straight into the switch.

## Roadmap

| Version | Contents |
|---|---|
| v0.1 | NAS ✅ tested in-game (v0.1.3) |
| v0.2 | NAS drive bays and T1–T3 drives |
| v0.3 | Network Switch, coloured cables (bundling and disguise), patch cables, Wall Jack, Patch Panel |
| v0.4 | Server Rack and T1–T3 blades |
| v0.5 | Router, Wireless Access Point, Print Server, cable trays and conduit |
| Later | Fibre, badge reader, chunk-loading racks |

## Builds

Every push to `main` builds on GitHub Actions. The jar is published on the
Releases page as a "Test build".
