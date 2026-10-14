-- CC: Servers & Networking - NAS test
-- Tests every NAS attached to this computer, directly or over wired modems.
-- Usage: nastest

local nases = { peripheral.find("nas") }
if #nases == 0 then
  printError("No NAS found. Place one next to this computer or connect it with a wired modem.")
  return
end

local function kb(n) return string.format("%.1f KB", n / 1024) end

print(("Found %d NAS"):format(#nases))
local passed = 0

for i, nas in ipairs(nases) do
  local name = peripheral.getName(nas)
  print("")
  print(("[%d] %s"):format(i, name))

  local mount = nas.getMountPath()
  if not mount then
    printError("  NOT MOUNTED on this computer")
  else
    print("  Mounted:  /" .. mount)
    print("  ID:       " .. nas.getId():sub(1, 8) .. "...")
    print("  Label:    " .. tostring(nas.getLabel()))
    print("  Free:     " .. kb(nas.getFreeSpace()) .. " of " .. kb(nas.getCapacity()))

    local path = fs.combine(mount, "ccsn_test.txt")
    local f = fs.open(path, "w")
    f.write("Hello from " .. name .. " at " .. os.date())
    f.close()

    local r = fs.open(path, "r")
    local text = r.readAll()
    r.close()

    if text:find(name, 1, true) then
      print("  Write/read: OK")
      if not nas.getLabel() then nas.setLabel("NAS " .. i) end
      passed = passed + 1
    else
      printError("  Write/read: FAILED")
    end
  end
end

print("")
if passed == #nases then
  print(("All %d NAS passed."):format(passed))
else
  printError(("%d of %d NAS passed."):format(passed, #nases))
end
