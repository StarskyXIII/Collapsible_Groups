const P6GroupRepository = Java.loadClass('com.starskyxiii.collapsible_groups.group.GroupRepository')
const P6ScriptedGroupStore = Java.loadClass('com.starskyxiii.collapsible_groups.group.ScriptedGroupStore')
const P6Minecraft = Java.loadClass('net.minecraft.client.Minecraft')
const P6ContainerScreen = Java.loadClass('net.minecraft.client.gui.screens.inventory.AbstractContainerScreen')
let cgP6ThrowObserved = false

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
      'cg_p6:must_not_publish',
      'P6 MUST NOT PUBLISH Diamond Pair',
      source.any([
        source.itemId('minecraft:diamond'),
        source.itemId('minecraft:diamond_block')
      ])
    )
  })

  cgP6ThrowObserved = true
  throw new Error('cg-p6-intentional-groups-post-failure')

  event.source('cg_p6:unreachable', source => {
    source.item('cg_p6:unreachable_group', 'P6 MUST NOT REACH', 'minecraft:netherite_ingot')
  })
})

console.info('[CG-P6] fixture=04-intentional-throw listener registered')

let cgP6Phase04Ticks = 0
let cgP6Phase04Done = false
let cgP6Phase04Armed = false
ClientEvents.tick(event => {
  if (cgP6Phase04Done) return
  if (!cgP6Phase04Armed) {
    if (P6Minecraft.getInstance().player === null || !(P6Minecraft.getInstance().screen instanceof P6ContainerScreen)) return
    cgP6Phase04Armed = true
    console.info('[CG-P6-AUDIT] phase=04-thrown-post state=ARMED trigger=inventory-screen-open')
  }
  cgP6Phase04Ticks++
  if (!cgP6ThrowObserved && cgP6Phase04Ticks < 400) return
  cgP6Phase04Done = true
  p6Assert(cgP6ThrowObserved, 'intentional listener was not invoked within 400 ticks')
  const names = p6Names()
  p6Assert(!names.includes('P6 MUST NOT PUBLISH Diamond Pair'), 'partial candidate published')
  p6Assert(!names.includes('P6 MUST NOT REACH'), 'unreachable source published')
  p6Assert(!P6ScriptedGroupStore.isApplied(), 'failed publication was marked applied')
  console.info('[CG-P6-AUDIT] phase=04-thrown-post result=PASS partial=absent unreachable=absent applied=false')
})
