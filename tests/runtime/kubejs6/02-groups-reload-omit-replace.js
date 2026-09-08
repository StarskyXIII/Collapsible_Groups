const P6GroupRepository = Java.loadClass('com.starskyxiii.collapsible_groups.group.GroupRepository')
const P6Minecraft = Java.loadClass('net.minecraft.client.Minecraft')
const P6ContainerScreen = Java.loadClass('net.minecraft.client.gui.screens.inventory.AbstractContainerScreen')

function p6Assert(condition, message) {
  if (!condition) throw new Error('[CG-P6-AUDIT] assertion failed: ' + message)
}

function p6Names() {
  const names = []
  P6GroupRepository.getAllIncludingScripted().forEach(group => names.push(String(group.name())))
  return names
}

CGEvents.groups(event => {
  event.source('cg_p6:replaceable', source => {
    source.add(
      'cg_p6:replaceable_pair',
      'P6 Replaceable Dirt Pair v2',
      source.any([
        source.itemId('minecraft:dirt'),
        source.itemId('minecraft:cobblestone')
      ])
    )
  })
})

console.info('[CG-P6] fixture=02-reload retained=replaceable omitted=native_ingredients,logic,fluids,readd')

let cgP6Phase02Ticks = 0
let cgP6Phase02Done = false
let cgP6Phase02Armed = false
ClientEvents.tick(event => {
  if (cgP6Phase02Done) return
  if (!cgP6Phase02Armed) {
    if (P6Minecraft.getInstance().player === null || !(P6Minecraft.getInstance().screen instanceof P6ContainerScreen)) return
    cgP6Phase02Armed = true
    console.info('[CG-P6-AUDIT] phase=02-omit-replace state=ARMED trigger=inventory-screen-open')
  }
  cgP6Phase02Ticks++
  const names = p6Names()
  if (!names.includes('P6 Replaceable Dirt Pair v2') && cgP6Phase02Ticks < 400) return
  cgP6Phase02Done = true
  p6Assert(names.includes('P6 Replaceable Dirt Pair v2'), 'phase 02 publication did not become ready within 400 ticks')
  p6Assert(names.includes('P6 Replaceable Dirt Pair v2'), 'replacement v2 group missing')
  p6Assert(!names.includes('P6 Replaceable Stone Pair v1'), 'replacement v1 survived')
  p6Assert(!names.includes('P6 Exact Healing Only'), 'omitted portable exact group survived')
  p6Assert(!names.includes('P6 Native Strict NBT Healing Only'), 'omitted native strict group survived')
  p6Assert(!names.includes('P6 Oak or Birch, Not Spruce'), 'omitted logic source survived')
  p6Assert(!names.includes('P6 Water and Lava'), 'omitted fluid source survived')
  p6Assert(!names.includes('P6 Readd Anchor v1'), 'omitted readd source survived')
  console.info('[CG-P6-AUDIT] phase=02-omit-replace result=PASS retained=replaceable-v2 omittedSources=absent')
})
