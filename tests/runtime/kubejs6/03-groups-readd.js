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

  event.source('cg_p6:readd', source => {
    source.add(
      'cg_p6:readd_anchor',
      'P6 Readd Anchor v3',
      source.any([
        source.itemId('minecraft:gold_ingot'),
        source.itemId('minecraft:iron_ingot')
      ])
    )
  })
})

console.info('[CG-P6] fixture=03-readd restored=readd retained=replaceable')

let cgP6Phase03Ticks = 0
let cgP6Phase03Done = false
let cgP6Phase03Armed = false
ClientEvents.tick(event => {
  if (cgP6Phase03Done) return
  if (!cgP6Phase03Armed) {
    if (P6Minecraft.getInstance().player === null || !(P6Minecraft.getInstance().screen instanceof P6ContainerScreen)) return
    cgP6Phase03Armed = true
    console.info('[CG-P6-AUDIT] phase=03-readd state=ARMED trigger=inventory-screen-open')
  }
  cgP6Phase03Ticks++
  const names = p6Names()
  const ready = names.includes('P6 Replaceable Dirt Pair v2') && names.includes('P6 Readd Anchor v3')
  if (!ready && cgP6Phase03Ticks < 400) return
  cgP6Phase03Done = true
  p6Assert(ready, 'phase 03 publication did not become ready within 400 ticks')
  p6Assert(names.filter(name => name === 'P6 Replaceable Dirt Pair v2').length === 1, 'replaceable v2 must exist once')
  p6Assert(names.filter(name => name === 'P6 Readd Anchor v3').length === 1, 're-added source must exist once')
  p6Assert(!names.includes('P6 Readd Anchor v1'), 'old readd group survived')
  console.info('[CG-P6-AUDIT] phase=03-readd result=PASS replaceable-v2=count1 readd-v3=count1')
})
