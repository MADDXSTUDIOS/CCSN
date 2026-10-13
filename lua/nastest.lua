-- CC: Servers & Networking - NAS test
-- Put a NAS next to a computer (or on a wired modem network) and run: nastest

local nas = peripheral.find("nas")
if not nas then
  printError("No NAS found. Place one next to this computer or connect it with a wired modem.")
  return
end

local function kb(n) return string.format("%.1f KB", n / 1024) end

print("NAS found: " .. peripheral.getName(nas))
print("ID:       " .. nas.getId())
print("Label:    " .. tostring(nas.getLabel()))
print("Mounted:  /" .. tostring(nas.getMountPath()))
print("Capacity: " .. kb(nas.getCapacity()))
print("Free:     " .. kb(nas.getFreeSpace()))

-- Write, read back, and check free space drops
local path = fs.combine(nas.getMountPath(), "ccsn_test.txt")
local before = nas.getFreeSpace()
local f = fs.open(path, "w")
f.write("Hello from CC: Servers & Networking! " .. os.date())
f.close()

local r = fs.open(path, "r")
print("Read back: " .. r.readAll())
r.close()
print("Free after write: " .. kb(nas.getFreeSpace()) .. " (was " .. kb(before) .. ")")

nas.setLabel("NAS 1")
print("New label: " .. nas.getLabel())

print("")
print("All good. Now break the NAS, place it somewhere else,")
print("and check " .. path .. " is still there.")
