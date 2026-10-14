-- CC: Servers & Networking - NAS test (v0.2, drive bays)
-- Tests every drive in every NAS attached to this computer.
-- Usage: nastest          run the test
--        nastest watch    print drive insert/eject events live (Ctrl+T to stop)

local args = { ... }

if args[1] == "watch" then
  print("Watching for drive swaps. Ctrl+T to stop.")
  while true do
    local event, name, bay = os.pullEvent()
    if event == "nas_drive_insert" then
      local d = peripheral.call(name, "getDrive", bay)
      print(("%s bay %d: inserted T%d drive, mounted at /%s"):format(name, bay, d.tier, tostring(d.mount)))
    elseif event == "nas_drive_eject" then
      print(("%s bay %d: drive removed"):format(name, bay))
    end
  end
end

local nases = { peripheral.find("nas") }
if #nases == 0 then
  printError("No NAS found. Place one next to this computer or connect it with a wired modem.")
  return
end

local function size(n)
  if n >= 1024 * 1024 then return ("%.1f MiB"):format(n / 1024 / 1024) end
  return ("%.1f KiB"):format(n / 1024)
end

local drivesTested, passed = 0, 0

for _, nas in ipairs(nases) do
  local name = peripheral.getName(nas)
  print(("%s (%s)"):format(name, nas.getLabel() or "no label"))

  for bay = 1, nas.getBayCount() do
    local d = nas.getDrive(bay)
    if not d then
      print(("  Bay %d: empty"):format(bay))
    else
      drivesTested = drivesTested + 1
      local line = ("  Bay %d: T%d  /%s  %s free of %s"):format(
        bay, d.tier, tostring(d.mount), size(d.free or 0), size(d.capacity))
      if not d.mount then
        printError(line .. "  NOT MOUNTED")
      else
        local path = fs.combine(d.mount, "ccsn_test.txt")
        local stamp = name .. " bay " .. bay .. " " .. os.date()
        local f = fs.open(path, "w"); f.write(stamp); f.close()
        local r = fs.open(path, "r"); local text = r.readAll(); r.close()
        if text == stamp then
          print(line .. "  OK")
          passed = passed + 1
          if not d.label then nas.setDriveLabel(bay, ("Drive %d"):format(bay)) end
        else
          printError(line .. "  WRITE/READ FAILED")
        end
      end
    end
  end
end

print("")
if drivesTested == 0 then
  printError("No drives found. Put a drive in a NAS (right-click it with a drive).")
elseif passed == drivesTested then
  print(("All %d drive(s) passed."):format(passed))
else
  printError(("%d of %d drive(s) passed."):format(passed, drivesTested))
end
