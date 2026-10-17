# Handover: start here

For a fresh chat picking this project up. Read this first, then `DESIGN.md`
(the full design and roadmap) and `README.md` (what players see). All three
live on the **`dev`** branch, which is the source of truth. `main` lags behind
on purpose (see Workflow).

Written 17 Oct 2026 at the end of the first long build chat.

---

## 1. What this is

- **A CC:Tweaked add-on mod** for Minecraft **1.21.1, NeoForge**, requiring
  **CC:Tweaked 1.120.x**. Author: Aaron (xPhoenixLRDx), MADD X Studios
  (GitHub: MADDXSTUDIOS). Repo: `github.com/MADDXSTUDIOS/CCSN`.
- **Purpose:** real-world IT hardware for CC. Office and server-room
  networking, storage, better computers and AV. It is the hardware layer that
  **Phoenix OS** (Aaron's Windows/Active-Directory-style OS for CC, a separate
  project) runs on, but it is a **generic public mod**: anyone can use it, and
  anyone could build their own OS on it.
- **It replaces the old idea of using OpenComputers.** OC is off the table for
  good; Phoenix OS stays on CC.
- **Name:** currently "CC: Servers & Networking", mod ID `ccsn`. **Decided new
  name: "CC: Phoenix Pro Hardware & Networking", mod ID `phoenixhw`.** The
  rename has NOT been done yet (see Next steps). Phoenix family: Phoenix OS,
  Phoenix Music, Phoenix Pro.
- **Who tests:** Aaron does all testing in-game and reports with screenshots.
  Claude writes all code, models and textures. Aaron never runs a dev
  environment; he just downloads the jar CI builds.

## 2. Current state

| Version | What | Status |
|---|---|---|
| v0.1-0.2 | NAS with 4 hot-swap drive bays, 3 drive tiers, chipsets, bay GUI | Tested |
| v0.3.0-0.3.7 | Coloured network cable (16 colours, up to 6-8 runs per block), wall jack, cable tray, trunking, disguise, wrench | Tested |
| **v0.3.8** | **Network Switch** (16 colour ports, chaining, port lights that flicker with traffic) + **wrench x-ray** | **Built, on dev, NOT tested yet** |

- `main` is at v0.3.7 (last signed-off version). `dev` has v0.3.8 and every
  design decision since.
- Nothing is broken that we know of.
- **Recipes:** only the NAS, drives and chipsets have them so far (never
  checked in survival). Cable, jack, covers, wrench and switch have none yet:
  creative only. Aaron deliberately left recipes to the end (release
  checklist in DESIGN.md).

## 3. Decisions, with the reasons (and what we rejected)

**Direction and principles**
- **Ground rules (in DESIGN.md):** feel native to CC; upgraded blocks are
  *supersets* of CC's API and type (an advanced speaker is still a
  `"speaker"`); **hardware, not policy**: generic Lua APIs, no Phoenix-only
  functions, Phoenix OS is just one program using them. Aaron wants the
  Phoenix OS integration *light*: the mod is about improving CC hardware and
  adding networking, seamless with CC rather than a botch job.
- **Only "advance" a CC block where CC's version limits a real use case.**
  Covered: computer to Workstation, pocket computer to laptop, speaker to
  advanced speaker, disk drive to NAS, wired modem to jacks + switch, wireless
  modem to access points. Probably adding: higher-res monitor (signage) and an
  office printer (network print queue). Not doing: turtles, redstone relay.
- **Look:** CC's pixel-art language everywhere. Graphite for hardware, cream
  for fittings, galvanised steel for tray. New computers reuse CC's own
  computer art in a new casing colour (like CC's command computer).

**Networking (the big one; full detail in DESIGN.md "Network model")**
- Our coloured cable is structured cabling, **never** touching CC's grey cable
  or modems. The **wall jack is the only bridge** (Aaron: "no interaction at
  all for the CC grey cables").
- Colours keep runs apart; each run is its own network until a switch or jack
  joins it to something.
- **Two rules:** devices stay in their switch group (a room); rednet messages
  can be routed between groups. Hardware can only isolate by separate
  networks, because in CC anything on a network can use everything on it.
- **Fresh switch = one group** (works like a plain switch). Grouping and
  routing are set in the switch GUI or over Lua. Typical "room switch": room
  ports in one group, uplink port in another with routing on.
- Switch ports: **one per colour, 16** (Aaron chose this but wants it easy to
  change, hence `SwitchPorts` is the only place that decides what a port is).
  **CC kit can't plug into the switch directly** (jacks only). **Full block.**
  Lights **lit + flicker on traffic**.
- **Rejected:** hanging patch cables (grey cable covers it); per-user
  permissions in hardware; proxying peripheral calls across groups; full
  multi-site routing before the router.

**Cable geometry (v0.3.6, chosen from options)**
- Run crossings at turns: **option A, 1 px layered strands** (chosen over
  "hop over" variants). Exhaustively proven overlap-free by `tools/geocheck`.
- Trays against walls: **brackets to the wall** (chosen over snapping the tray
  to the wall), runs keep their lanes. Trunking widens to the wall instead.
- Runs per block: **default 6, server config 1-8**.

**Planned hardware decisions so far**
- **Server rack: 1 wide x 2 tall** (Aaron), so it doesn't look like the NAS.
- **Workstation:** CC advanced computer with a 102x38 screen (CC 1.120's
  `computercraft:terminal_size` component), graphite casing; needs our own
  screen class to draw it smaller (CC internals), so it ships with the rack in
  v0.4. Real pixel graphics maybe later, copying CraftOS-PC's graphics API.
- **Laptop:** like the Workstation but portable, wired modem + speaker +
  wireless modem built in, any colour (graphite default), usable in the hand on
  wireless via access points. Aaron wants it; plug-in method undecided.
- **Advanced speaker:** for Phoenix Music. CC's speaker squashes audio to 1-bit
  DFPWM before sending to players and fixes volume per queued chunk; ours
  sends better audio, live volume/pause/skip, synced speaker groups.
- **Access points:** cream and graphite versions.

**Name history** (so nobody re-litigates it): "CC: Enterprise & More" (didn't
feel right), "CC Phoenix Hardware Additions", "...Advanced Hardware &
Networking" ("Advanced" clashes with the Advanced Peripherals add-on and CC's
gold tier), then **"CC: Phoenix Pro Hardware & Networking"**, checked for
clashes on CurseForge and Modrinth.

## 4. Architecture

Java package `uk.maddx.ccsn` (will become the `phoenixhw` equivalent on rename).

- `CCSN.java`: mod entry; registers CC capabilities (NAS peripheral, jack wired
  element). Our cable and switch deliberately expose nothing to CC.
- `ModRegistry.java`: every block, item, block entity, menu, creative tab.
- `CCSNConfig.java`: server config (drive capacities, `cables.max_runs_per_block`).
- `nas/`: NAS block, block entity, menu, peripheral (`nas` type, mounts each
  drive as `nas_<name>_bay<N>`).
- `drive/DriveItem.java`: drive items (tier, storage UUID, label).
- `network/`:
  - `NetworkCableBlock(Entity)`, `NetworkCableItem`: runs, one CC wired node per
    colour; links to same-colour cable, jacks (direct or through one wall) and
    switches. `refreshConnections()` also works out solid/open/widen sides for
    the covers.
  - `CableLanes`: all cable geometry (strands, tray, risers, supports,
    trunking) as plain maths, plus the hitbox. `CableRender`: the record the
    server syncs and the client model draws from.
  - `WallJackBlock(Entity)`, `JackLink`: the bridge to CC grey cable/modems.
  - `NetworkSwitchBlock(Entity)`, `SwitchPorts`: the switch; one wired node,
    a CC `PacketReceiver` counting traffic, block events to flicker lights.
  - `NetworkScheduler`: deferred relinking on the server tick.
  - `CableCoverItem`, `WrenchItem`.
- `client/`: `CableBakedModel` (dynamic model, manual quad packing),
  `SwitchRenderer` (port lights), `WrenchXray` (runs through walls),
  `NasScreen`, `CCSNClient` (colours, model swap, renderers).
- Assets are **generated**: `tools/gen_assets.py` (NAS, drives, GUI) and
  `tools/gen_net_assets.py` (cable, jack, covers, switch). Edit the scripts,
  not the PNG/JSON files.
- Lua: `lua/nastest.lua` (writes/reads every drive; `nastest watch` shows
  hot-swap events).

## 5. Where everything lives

- **Repo:** `MADDXSTUDIOS/CCSN`. Branch **`dev`** = all work; **`main`** = last
  signed-off version.
- **Docs:** `HANDOVER.md` (this), `DESIGN.md` (design, network model, roadmap,
  look and feel, ground rules), `README.md` (player-facing).
- **Jars:** GitHub Releases. Every push to `dev` publishes a pre-release
  "Test build N (version, dev)".
- **CC source for reference:** clone `cc-tweaked/CC-Tweaked` at tag
  `v1.21.1-1.120.0` when you need to check how CC does something (wired
  networks, speaker, terminal sizes, models). Many decisions above came from
  reading it.
- **Concept renders** (range line-ups, Workstation screen mock-up) were made
  with throwaway scripts outside the repo; `tools/zrender.py` and
  `tools/geocheck/` are the reusable bits.

## 6. How we work

- **Loop:** Claude edits on `dev`, pushes, GitHub Actions builds, Claude
  downloads the newest jar and sends it to Aaron with short test steps; Aaron
  tests and replies (often with screenshots). One feature or fix set per build.
- **Finding the jar:** the releases list is NOT sorted by date. Sort by
  `created_at` and take the newest. In the cloud session, `gh release download`
  was blocked (GraphQL); downloading the asset through the REST API worked:
  `gh api -H "Accept: application/octet-stream" <asset url> > file.jar`.
- **No local build:** the session couldn't reach NeoForge's maven, so **CI is
  the compiler**. Read code carefully before pushing. For cable/tray geometry,
  `tools/geocheck/run.sh` compiles the real geometry against tiny Minecraft
  stubs, proves no overlaps, and `run.sh scenes out/` renders previews.
- **Branches:** `main` only moves (merge `dev` into it) when Aaron signs a
  version off. Public release = tag `v<mod_version>` on `main` (CI refuses a
  tag that doesn't match `gradle.properties`). `.md`-only pushes don't build.
- **Version:** bump `mod_version` in `gradle.properties` for each test build;
  update README status and the DESIGN roadmap row.
- **Commits** are authored as `MADDXSTUDIOS <MADDXSTUDIOS@users.noreply.github.com>`.
  Never force-push or rewrite history.
- **Aaron's style:** casual, blunt, British, swearing fine. Short bold-led
  bullets, a blunt bottom line. He works shifts (LGV driver), often late at
  night; wrap up cleanly when he's done. He likes seeing things (renders,
  mock-ups) before committing to big changes. Ask before expensive or
  ambiguous builds; just do small clear ones.
- **Testing gotcha seen before:** creative middle-click copies of a used drive
  share its storage (same as CC floppies); that's expected, not a bug.

## 7. Open questions

- Server rack: 4 or 8 blade slots now it's 2 tall?
- Laptop: dock (A) or a patch lead from a jack beside it (B)? Claude leans B.
- Advanced speaker: its Lua API. Waiting on Phoenix Music's list of CC speaker
  limitations to shape it.
- What the router and access points actually do (v0.5).
- Switch: do chained switches share groups like a trunk? Which group does the
  switch's own Lua peripheral appear on? Ports per colour vs per side+colour.
- Monitor and office printer: confirm they make the list.
- Whether to lock a switch's settings to a management computer (set with the
  wrench), or leave all rules to software.
- GUI test programs (NAS browser, switch tester) are planned for later; they
  double as simple examples for Phoenix OS.

## 8. Next steps, in order

1. **Aaron tests v0.3.8** (switch joins runs, front doesn't connect, chaining,
   lights in each port's colour, amber flicker on rednet, status message,
   wrench x-ray). Fix anything, then merge to `main` when signed off.
2. **Rename build:** `ccsn` to `phoenixhw` and "CC: Phoenix Pro Hardware &
   Networking". Map every old `ccsn:` ID to `phoenixhw:` (NeoForge registry
   aliases) so test worlds carry over; keep reading NAS files from
   `world/computercraft/ccsn_drives/`; rename package, assets, config, docs,
   and the GitHub repo (GitHub redirects). Ship it alone and have Aaron load an
   old world.
3. **v0.3.9 managed switch:** groups, message routing between groups, GUI
   (ports to groups, routing, port on/off, labels, counters), generic Lua API.
   Design the Lua API in DESIGN.md first, following the ground rules.
4. v0.3.10 patch panel; v0.4 server rack + blades + Workstation (+ laptop);
   v0.5 router, access points, print server; advanced speaker, monitor and
   printer slotted in where they fit.

## 9. Glossary

- **Run:** one colour of our network cable; its own network. **Bundle:** several
  runs in one block. **Lane:** a colour's fixed position in the block.
- **Cover:** tray, trunking or disguise round a cable block. **Riser:** a tray
  going up or down. **Supports:** feet, brackets, clips, drop rods.
- **Jack / wall jack:** faceplate bridging our cable to CC grey cable/modems.
  **Patch lead:** the grey cable drawn into a jack.
- **Port:** a switch input (one per colour now). **Group:** a set of switch ports
  forming one network (a room / VLAN). **Routing:** passing rednet messages
  between groups. **Uplink / backbone:** the link to the comms cupboard where
  Phoenix Core, file servers and intranet live.
- **NAS:** storage block with 4 bays; **drive** tiers T1 4 MiB, T2 16 MiB, T3
  64 MiB. Mounts named `nas_<name>_bay<N>`.
- **Blade:** a server computer item living in the rack. **Workstation:** the
  big-screen computer. **Phoenix OS:** Aaron's OS (Phoenix Station = client,
  Phoenix Core = server). **Phoenix Music:** Aaron's music program (separate
  repo), the reason for the advanced speaker.
- **x-ray:** holding the wrench shows runs through walls.
- **CableRender:** the record describing one cable block for drawing and hitbox.

---

## Project instructions (paste into the Claude Project)

```
Project: CC: Phoenix Pro Hardware & Networking (mod ID phoenixhw; still named ccsn in
code until the rename build). CC:Tweaked add-on, NeoForge 1.21.1, CC 1.120.x.
Repo: github.com/MADDXSTUDIOS/CCSN. Always work on and read the dev branch.

Start every chat by reading HANDOVER.md, then DESIGN.md, on dev. They are the
source of truth; update them as decisions are made.

Workflow: edit on dev, push, GitHub Actions builds a pre-release test jar. Find the
newest release by created_at, download the jar, send it to Aaron with short test
steps. Aaron does all testing. main only moves when Aaron signs a version off;
public releases are v* tags on main. No local Gradle: CI is the compiler, so read
code carefully. Commits as MADDXSTUDIOS <MADDXSTUDIOS@users.noreply.github.com>.

Ground rules: feel native to CC; upgraded blocks are supersets of CC's API and type;
hardware not policy (generic Lua APIs, no Phoenix-only functions; Phoenix OS is just
one program using them); CC pixel-art style, graphite hardware, cream fittings.
Ask before expensive or ambiguous builds; show renders/mock-ups for visual changes.

Tone: casual, blunt, British, swearing fine. Short bold-led bullets, blunt bottom line.
```
