const S7InvalidIsForge = Platform.isForge()
const S7InvalidGroupRepository = Java.loadClass('com.starskyxiii.collapsible_groups.group.GroupRepository')
const S7InvalidMinecraft = Java.loadClass('net.minecraft.client.Minecraft')
const S7InvalidContainerScreen = Java.loadClass('net.minecraft.client.gui.screens.inventory.AbstractContainerScreen')

function s7InvalidAssert(condition, message) {
  if (!condition) throw new Error('[CG-S7-NBT-AUDIT] assertion failed: ' + message)
}

function s7ExpectError(action, exactMessage) {
  let actual = null
  try {
    action()
  } catch (error) {
    actual = error.javaException && error.javaException.getMessage
      ? String(error.javaException.getMessage())
      : error.getMessage
        ? String(error.getMessage())
        : String(error.message)
  }
  s7InvalidAssert(actual === exactMessage, 'expected error "' + exactMessage + '" but got "' + actual + '"')
}

function s7InvalidNames() {
  const names = []
  S7InvalidGroupRepository.getAllIncludingScripted().forEach(group => names.push(String(group.name())))
  return names
}

CGEvents.groups(event => {
  event.source('cg_s7:invalid_builder_runtime', source => {
    s7ExpectError(() => source.nbt('1b'), 'nbt() requires a valid compound SNBT value')
    s7ExpectError(() => source.nbt('{value:1b} trailing'), 'nbt() requires a valid compound SNBT value')
    s7ExpectError(() => source.nbtPath('', '1b'), 'nbtPath() requires a valid NBT path')
    s7ExpectError(() => source.nbtPath('value[*]', '1b'), 'nbtPath() requires a valid NBT path')
    s7ExpectError(() => source.nbtPath('value', 'bad trailing'), 'nbtPath() requires a valid SNBT value')
    s7ExpectError(() => source.nbtPath('value', '[1b,2]'), 'nbtPath() requires a valid SNBT value')
    s7ExpectError(() => source.nbtPath('value', '[B;1]'), 'nbtPath() requires a valid SNBT value')

    source.add(
      'cg_s7:valid_peer_after_rejections',
      'S7 Valid Peer After Rejections',
      source.nbtPath('value', '1b')
    )
  })
  console.info('[CG-S7-NBT-AUDIT] phase=02-builder-rejections result=PASS loader=' + (S7InvalidIsForge ? 'forge' : 'fabric') + ' scalar-root=REJECT trailing-root=REJECT empty-path=REJECT wildcard-path=REJECT trailing-value=REJECT heterogeneous-list=REJECT wrong-array-element=REJECT legal-peer=registered')
})

let s7InvalidTicks = 0
let s7InvalidDone = false
let s7InvalidArmed = false
ClientEvents.tick(event => {
  if (s7InvalidDone) return
  if (!s7InvalidArmed) {
    if (S7InvalidMinecraft.getInstance().player === null || !(S7InvalidMinecraft.getInstance().screen instanceof S7InvalidContainerScreen)) return
    s7InvalidArmed = true
    console.info('[CG-S7-NBT-AUDIT] phase=02-publication state=ARMED trigger=inventory-screen-open')
  }
  s7InvalidTicks++
  const names = s7InvalidNames()
  const ready = names.includes('S7 Valid Peer After Rejections')
  if (!ready && s7InvalidTicks < 400) return
  s7InvalidDone = true
  s7InvalidAssert(ready, 'phase 02 publication did not become ready within 400 ticks')
  s7InvalidAssert(!names.includes('S7 NBT Root'), 'phase 01 root group survived source omission')
  s7InvalidAssert(!names.includes('S7 NBT Quoted Path'), 'phase 01 quoted path group survived source omission')
  s7InvalidAssert(!names.includes('S7 NBT Nested Logic'), 'phase 01 nested logic group survived source omission')
  console.info('[CG-S7-NBT-AUDIT] phase=02-publication result=PASS rejected-builders=not-published legal-peer=present omitted-v1=absent')
})
