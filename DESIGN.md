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

### Network model (agreed 2026-10-17)

**What this mod is for:** the hardware layer Phoenix OS is built on. It
replaces the idea of using OpenComputers entirely (OC is off the table).
Phoenix OS's needs drive the design; everything stays generic so anyone can
use it without Phoenix OS.

**The CC rules everything hangs on:**
- Anything on a wired network can see and use every device on it. There are
  no permissions in between.
- One modem = one network. A computer can sit on several networks by having
  several modems.
- Anything else touching two networks (cable, jack, switch port) merges them.
- So "isolated" in CC can only mean "on a separate network"; the question is
  what may cross between networks.

**Three layers:**
1. **Cabling (mod):** each run (colour) is a separate network. Bundling lets
   several networks share one route. A jack decides which network the device
   plugged into it joins; re-patching a jack moves a device between networks.
2. **Switch (mod):** groups its ports into networks and passes messages
   between groups where allowed (below). GUI + Lua API.
3. **Phoenix OS (software):** rooms, users, permissions, who can print where,
   the server manager's view of every room. Drives switches through Lua.

**The two rules:**
1. **Devices stay in their group.** Printers, speakers, monitors... are only
   visible on their own group's network.
2. **Messages can cross.** Modem/rednet messages (logins, file requests, print
   jobs, intranet pages) are passed between groups the switch is told to route.

**The switch:**
- Every port (see `SwitchPorts`; one per colour for now) belongs to a group.
  Same group = one network (devices and messages). Different groups = separate
  networks; messages pass between them only where routing is turned on.
- **Out of the box every port is in one group**, so a fresh switch works like
  a plain switch. Colours only identify the ports. Splitting ports into groups
  and turning routing on is done in the switch GUI, or by Phoenix OS over Lua.
- **Room switch** (the typical managed setup): the room's ports in the room
  group; the uplink port (to the comms cupboard / backbone) in its own group
  with routing on. The room's devices stay in the room; its computers still
  reach Active Directory, file servers and intranet hosts by messages.
- **Backbone:** Phoenix Core (directory, logins, policies), file servers,
  intranet hosting. The NAS sits on a storage group only Core / the file
  server is on, so room computers get files through Core's login (messages),
  never by mounting the NAS directly.
- **Server manager:** the switch reports which devices are on which group and
  lets Core move ports between groups, turn ports and routing on and off, and
  read traffic counters. Core sees every room without merging them.
- **How (implementation):** one wired node per group; each port links to its
  group's node. Each group node has a packet listener (as the traffic lights
  already use) and re-sends what it hears onto the nodes of groups it routes
  to (each group element is a CC WiredSender). Never relay a packet a CCSN
  switch sent (one hop, so no loops between switches); rednet drops its own
  duplicates.

**Use cases this covers:**
- **Office rooms:** isolated rooms, still on Active Directory / NAS / intranet.
- **Shared office building:** each tenant its own group, bundled through the
  same trunking, nothing routed between them.
- **Phoenix Manor AV:** indoor and outdoor speakers as two groups. For a show,
  move the outdoor port into the indoor group (GUI, or Core over Lua), or
  re-patch the outdoor jacks onto the indoor run by hand.
- **Printing to another office:** Phoenix OS sends the job as a message to a
  print server or computer in that room, which prints it. Permissions are
  Phoenix OS's call.

**Not doing (over-engineering):**
- Per-user permissions in hardware: the switch knows groups, Phoenix OS knows
  users.
- Using a device in another group directly (proxying peripheral calls across
  networks): send a message to something in that group instead.
- Multi-site routing before the router (v0.5): routing between groups on one
  switch is enough to start.

**Still open:**
- How chained switches share groups (does a link between switches carry one
  group, or all of them like a trunk?).
- Which group the switch's own Lua peripheral shows up on (a chosen
  management group?).
- Ports per colour vs per side and colour: revisit if a room switch needs two
  runs of the same colour.

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
- **Colours keep runs apart.** Like real ethernet, each jack has its own run
  back to the switch. Runs sharing a block never merge mid-wall, so the switch
  knows which jack is on which port. Each run is its own network until a
  switch (or a jack) joins it to something.
- **The switch joins runs** into one network by default. Switches can chain
  to extend the network. Groups (VLANs) keep rooms apart: see the network
  model above.
- A run can also go straight jack to jack, no switch (point to point).
- Our own blocks (NAS, switch, rack) will later plug straight into our cable
  without a CC modem: CC's public API lets a wired node share peripherals.

### Network Cable (block)
- 16 dye colours, one item per colour. Same colour joins same colour;
  different colours never join.
- **Bundling (v0.3.2, v0.3.6):** up to 6 runs in ONE block space (server
  config `cables.max_runs_per_block`, 1-8). Right-click a cable (or the block
  it sits on) with a new colour to add it. Each colour has a fixed lane point
  from a 4x4 Latin square, so runs line up block to block. Strands are 1 px
  and layered by direction (v0.3.6): X arms at the lane point (y, z), Z arms
  one pixel up (x, y+1), Y arms one pixel across (x+1, z+1). Lanes are 2 px
  apart, so no strand passes through another, straight or turning, for any
  set of colours (checked exhaustively against the real code). Data lives in
  the block entity as a `CableRender` record; a dynamic model draws it.
  Breaking drops one item per run.
- A jack on a bundle uses one run: the first, or the one picked by sneak +
  right-clicking the jack with that colour's cable (plain right-click with a
  cable never touches the jack, so laying cable past it is safe).
- **Covers (v0.3.5)**, one per cable block, all inside the block's own 1x1:
  - **Cable Tray** item: galvanised ladder tray round the runs (still
    visible), following bends and junctions. Real rungs across the tray, slotted
    side rails (posts every 4 px, lined up block to block), deep enough (rails
    to 12.5 px, v0.3.7) that every run sits inside it. Going up or down it
    becomes a slatted riser, continuous between blocks, open on the sides runs
    leave by. Supports (v0.3.6): a strut on two feet on a floor (a riser stands
    on its corner posts), cantilever brackets to a wall beside it (clips for
    risers), or a strut on drop rods from a ceiling.
  - **Cable Trunking** item: closed white duct, runs hidden. Widened to sit
    flush against any wall, floor or ceiling next to it; where it turns round
    the outside corner of a wall the bend is filled in to match (v0.3.7). An
    end is only capped where it's open to the air, or where it meets trunking
    of a different shape (say a run carrying on past the end of a wall); where
    it meets more cable it's left open (drawn hollow, runs visible inside).
  - **Disguise**: right-click with a plain full block (no block entity, solid
    render, full collision: stone bricks, planks, logs, concrete...). The block
    gets a `disguised` state so it's solid, blocks light and has full
    collision; the model draws the disguise block's own model (its tints too).
    Sneak + right-click places the block normally instead.
  - **Network Wrench**: sneak + right-click takes the cover off and gives it
    back; right-click shows runs + cover (or a jack's status).
  - Breaking the cable drops its runs plus the cover.
  - Holding the wrench (either hand, v0.3.8) shows every run within 24 blocks
    as coloured outlines through walls, floors and disguises.

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

### Network Switch (v0.3.8)
- Full block, port panel on the front (faces whoever places it).
- **Ports: one per colour, 16 in all** (decided; may change with use cases).
  Every run of network cable touching any side but the front plugs into the
  port for its colour, and the switch joins them all into one network. What a
  port is lives in one place (`SwitchPorts`), so it can change later (per side,
  fewer ports, numbered jacks) without touching the rest.
- A run passing along a switch's side is plugged in too (cable joins whatever
  it touches); route round it, or turn the port off once the GUI exists.
- **Chaining:** switches touching each other (not by their fronts) join; so do
  switches with a run between them.
- **CC kit can't plug in directly** (decided): wall jacks stay the only way in.
- **Lights:** each port has a link light in its colour (lit while a run is
  plugged in) and an amber activity light that flickers while modem messages
  cross the network. The switch listens on its node for every packet (CC's
  PacketReceiver), and sends clients a block event at most every 4 ticks.
  Peripheral calls over the network (reading a NAS, say) aren't messages, so
  they don't flicker it.
- Right-click empty-handed (or with the wrench): ports in use, chained
  switches, devices on the network.
- **Next build (managed switch):** groups and message routing as in the
  network model above; GUI with ports to groups, routing, per-port on/off,
  labels, traffic counters; Lua peripheral for Phoenix OS.

### Patch Panel
- Comms-cupboard version of many jacks in one (half-height).

### Hanging patch cables
- Dropped for now: grey cable plus our runs cover everything. Maybe later for looks.

### Later
- **v0.5:** Router/Gateway, Wireless Access Point, Print Server.
- **Later:** fibre (long distance; unloaded chunks are the hard part), badge
  or card reader (tap to log in, tied to directory accounts), chunk-loading
  racks (config option).

## Typical office

Computer + modem → grey cable → Wall Jack → coloured run inside a disguised
wall → room switch (or Patch Panel → switch) → uplink to the comms cupboard /
backbone (Phoenix Core, file server, intranet). The room's devices stay in the
room; its computers reach the backbone by messages. Small setups can skip the
switch (one network, every computer sees every device) or run jack to jack.

## Roadmap

| Version | Contents |
|---|---|
| v0.1 | NAS ✅ tested in-game (v0.1.3) |
| v0.2 | NAS drive bays, 3D drives, chipsets, bay GUI, hot-swap ✅ tested in-game (v0.2.1) |
| v0.3.0 | Coloured network cable + Wall Jack ✅ tested in-game (v0.3.1: through-wall, side plugs, status) |
| v0.3.2 | Bundling (up to 4 runs per block) ✅ tested in-game (two NASes on separate runs, drive swaps across them) |
| v0.3.5 | Covers: disguise, cable tray, trunking + wrench ✅ tested in-game (fix list below came out of it) |
| v0.3.6 | Polish: fix list 1-6 and 8-12 (layered strands, ladder tray, risers, supports, trunking ends, 6-8 runs per block) ✅ tested in-game |
| v0.3.7 | Trunking fills outside corners and caps mismatched joins; deeper tray so runs sit inside the rails ✅ tested in-game |
| v0.3.8 | Network Switch (16 colour ports, chaining, port lights with traffic) + wrench x-ray 🧪 |
| v0.3.9 | Managed switch: groups (VLANs), message routing between groups, GUI, Lua API (see network model) |
| v0.3.10 | Patch Panel |
| v0.4 | Server Rack and T1–T3 blades |
| v0.5 | Router, Wireless Access Point, Print Server |
| Later | Fibre, badge reader, chunk-loading racks |

## Fix list (cosmetic, from v0.3.5 testing; done in v0.3.6, 7 in v0.3.8)

1. ✅ **Runs cut through each other at turns.** Fixed lanes stop overlap when runs
   are parallel, but two colours at the same height (e.g. yellow and light
   grey) cross where one turns. Fix: the turning run hops over the other
   inside the block; block-edge positions stay the same so joins still line up.
2. ✅ **Tray riser seam:** stacked vertical riser blocks show a gap (walls stop at
   y 3-4 where the floor is skipped). Make riser walls continuous.
3. ✅ **Runs poke through riser walls** where a horizontal run leaves a vertical
   riser. Riser walls need an opening on sides with a horizontal arm.
4. ✅ **Trays and risers float away from the wall:** they sit in the middle of
   the block, leaving a gap to the wall they run along. Chosen fix: brackets
   to the wall (trays) and clips (risers), runs keep their lanes; trunking is
   widened to the wall. (Jacks sitting in a stub of trunking/tray is fine.)
5. ✅ **RJ45 plug top too rainbow:** tone the wire colours down so it reads as a
   clear plug with gold contacts.
8. ✅ **Trunking end caps show closed** where the trunking butts up against
   another block or a different cover (e.g. a tray riser). Only cap an end
   that's open to the air.
6. ✅ **Tray steel texture reads as stone:** make it look like galvanised sheet
   steel (smoother, fewer speckles, edge highlights).
7. ✅ (v0.3.8) holding the wrench shows hidden runs through walls.
9. ✅ **Open ladder tray floor:** the dark stripes on the tray floor are painted
   on. Make the floor real rungs (separate 2 px bars with actual gaps) so you
   can see the cables through it from below. Rungs must always run ACROSS the
   tray: right now the painted stripes follow the texture's direction, so a
   tray running east-west shows lengthwise stripes while north-south shows
   rungs.
10. ✅ **Riser floats at the bottom:** the bottom block of a riser stops 3 px
    above the block it stands on. When there's a solid block underneath, add
    feet / a base flange down to it (like the drop rods do for ceilings).
11. ✅ **Gaps between tray side-rail panels:** along a long tray run the side
    rails show regular gaps (cables visible through them). Looks good, so make
    it deliberate and consistent: slotted/perforated side rails on every tray
    piece (straights, bends, junctions, risers), evenly spaced, instead of
    accidental gaps.
12. ✅ **More runs per block:** default 6, server config 1-8 (agreed). Lanes
    already give all 16 colours their own spot. The render key won't fit a
    single long at 8 runs (16 + 8x6 + 3 bits = 67), so switch it to a small
    record (colours int + masks long + cover bits). Do item 1 (hops at turns)
    properly first, as more runs means more crossings.

## Release checklist

Done once before a public release, not every version:

- Survival playthrough of every recipe (chipsets, drives, NAS and anything
  added since). Nothing uncraftable, nothing too cheap.
- Default config values sanity-checked.
- Fresh-world test with only CC:Tweaked and CCSN installed.

## Builds

- **`dev`**: all work goes here. Every push builds on GitHub Actions and the
  jar is published on the Releases page as a pre-release "Test build N".
- **`main`**: the last version tested in-game and signed off. `dev` is merged
  in only when a version passes testing. Pushes are built to check them but
  nothing is published.
- **Releases**: tag a commit on `main` as `v<mod_version>` (e.g. `v0.4.0`) when
  it's ready to go public; that builds and publishes a full release. The tag
  must match `mod_version` in gradle.properties or the build fails.
- Changes that only touch `.md` files don't trigger a build.
