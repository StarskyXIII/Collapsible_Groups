const healingPotion = Item.of('minecraft:potion', '{Potion:"minecraft:healing"}')
const swiftnessPotion = Item.of('minecraft:potion', '{Potion:"minecraft:swiftness"}')
const plainStone = Item.of('minecraft:stone')
const taggedStone = Item.of('minecraft:stone', '{cg_p6_probe:1b}')
const P6IsForge = Platform.isForge()
const P6FilterCompiler = Java.loadClass('com.starskyxiii.collapsible_groups.compat.kubejs.KubeJs6FilterCompiler')
const P6GroupDefinition = Java.loadClass('com.starskyxiii.collapsible_groups.group.GroupDefinition')
const P6GroupRepository = Java.loadClass('com.starskyxiii.collapsible_groups.group.GroupRepository')
const P6GroupBridge = Java.loadClass('com.starskyxiii.collapsible_groups.compat.kubejs.KubeJSGroupBridge')
const P6ScriptedGroupStore = Java.loadClass('com.starskyxiii.collapsible_groups.group.ScriptedGroupStore')
const P6Minecraft = Java.loadClass('net.minecraft.client.Minecraft')
const P6ContainerScreen = Java.loadClass('net.minecraft.client.gui.screens.inventory.AbstractContainerScreen')
let cgP6DirectThrow = false

function p6Assert(condition, message) {
  if (!condition) throw new Error('[CG-P6-AUDIT] assertion failed: ' + message)
}

function p6Names() {
  const names = []
  P6GroupRepository.getAllIncludingScripted().forEach(group => names.push(String(group.name())))
  return names
}

const strictResult = P6FilterCompiler.compileItem(healingPotion.strongNBT())
const directStackResult = P6FilterCompiler.compileItem(healingPotion)
const directPlainStackResult = P6FilterCompiler.compileItem(plainStone)
const vanillaIngredientResult = P6FilterCompiler.compileItem(Ingredient.of(healingPotion))
const strictNoNbtResult = P6FilterCompiler.compileItem(plainStone.strongNBT())
const weakResult = P6FilterCompiler.compileItem(healingPotion.weakNBT())
const customResult = P6FilterCompiler.compileItem(
  Ingredient.custom(Ingredient.of('minecraft:potion'), stack => true)
)
const customOverStrictResult = P6FilterCompiler.compileItem(
  Ingredient.custom(healingPotion.strongNBT(), stack => true)
)
p6Assert(String(directStackResult.getClass().getSimpleName()) === 'ExactStack', 'direct stack must lower to ExactStack')
const directStackProbe = P6GroupDefinition.of('cg_p6:direct_stack_probe', 'P6 Direct Stack Probe', directStackResult)
p6Assert(directStackProbe.matches(healingPotion), 'direct stack must match itself')
p6Assert(!directStackProbe.matches(swiftnessPotion), 'direct stack must reject different NBT')
p6Assert(String(directPlainStackResult.getClass().getSimpleName()) === 'ExactStack', 'direct plain stack must lower to ExactStack')
const directPlainStackProbe = P6GroupDefinition.of('cg_p6:direct_plain_stack_probe', 'P6 Direct Plain Stack Probe', directPlainStackResult)
p6Assert(directPlainStackProbe.matches(plainStone), 'direct plain stack must match itself')
p6Assert(!directPlainStackProbe.matches(taggedStone), 'direct plain stack must preserve NBT absence')
p6Assert(String(vanillaIngredientResult.getClass().getSimpleName()) === 'Id', 'vanilla ingredient must lower to item ID')
const vanillaIngredientProbe = P6GroupDefinition.of('cg_p6:vanilla_ingredient_probe', 'P6 Vanilla Ingredient Probe', vanillaIngredientResult)
p6Assert(vanillaIngredientProbe.matches(healingPotion), 'vanilla ingredient must match healing potion ID')
p6Assert(vanillaIngredientProbe.matches(swiftnessPotion), 'vanilla ingredient must ignore potion NBT')
if (P6IsForge) {
  p6Assert(strictResult === null, 'Forge strongNBT must be rejected because its semantics cannot be represented losslessly')
  p6Assert(strictNoNbtResult === null, 'Forge strongNBT without NBT must be rejected because its semantics cannot be represented losslessly')
} else {
  p6Assert(strictResult !== null, 'Fabric strongNBT must compile')
  p6Assert(String(strictResult.getClass().getSimpleName()) === 'ExactStack', 'Fabric strongNBT must lower to ExactStack')
  var strictProbe = P6GroupDefinition.of('cg_p6:strict_runtime_probe', 'P6 Strict Runtime Probe', strictResult)
  p6Assert(strictProbe.matches(healingPotion), 'Fabric strongNBT must match the tagged healing potion')
  p6Assert(!strictProbe.matches(swiftnessPotion), 'Fabric strongNBT must reject the tagged swiftness potion')
  p6Assert(String(strictNoNbtResult.getClass().getSimpleName()) === 'ExactStack', 'Fabric strongNBT without NBT must lower to ExactStack')
  var strictNoNbtProbe = P6GroupDefinition.of('cg_p6:strict_no_nbt_probe', 'P6 Strict No NBT Probe', strictNoNbtResult)
  p6Assert(strictNoNbtProbe.matches(plainStone), 'Fabric strongNBT without NBT must match plain stack')
  p6Assert(!strictNoNbtProbe.matches(taggedStone), 'Fabric strongNBT without NBT must reject tagged stack')
}
p6Assert(weakResult === null, 'weakNBT must be rejected')
p6Assert(customResult === null, 'arbitrary custom predicate must be rejected')
p6Assert(customOverStrictResult === null, 'custom predicate over a strict base must be rejected')
console.info('[CG-P6-AUDIT] phase=01-native-compile result=PASS loader=' + (P6IsForge ? 'forge' : 'fabric') + ' directStack=exact directPlainStack=absence-exact vanillaIngredient=id nativeStrongNBT=' + (P6IsForge ? 'REJECT' : 'healing-only') + ' nativeStrongNoNBT=' + (P6IsForge ? 'REJECT' : 'absence-exact') + ' weakNBT=REJECT customPredicate=REJECT customOverStrictBase=REJECT')

CGEvents.groups(event => {
  event.source('cg_p6:native_ingredients', source => {
    source.exact(
      'cg_p6:exact_healing',
      'P6 Exact Healing Only',
      healingPotion
    )

    source.ingredient(
      'cg_p6:strong_nbt_healing',
      'P6 Native Strict NBT Healing Only',
      healingPotion.strongNBT()
    )

    source.ingredient(
      'cg_p6:weak_nbt_rejected',
      'P6 REJECT Weak NBT',
      healingPotion.weakNBT()
    )
    source.ingredient(
      'cg_p6:custom_predicate_rejected',
      'P6 REJECT Custom Predicate',
      Ingredient.custom(Ingredient.of('minecraft:potion'), stack => true)
    )

    source.item(
      'cg_p6:legal_peer',
      'P6 Legal Peer Emerald',
      'minecraft:emerald'
    )
  })

  event.source('cg_p6:logic', source => {
    source.add(
      'cg_p6:oak_or_birch_not_spruce',
      'P6 Oak or Birch, Not Spruce',
      source.all([
        source.any([
          source.itemId('minecraft:oak_planks'),
          source.itemId('minecraft:birch_planks'),
          source.itemId('minecraft:spruce_planks')
        ]),
        source.not(source.itemId('minecraft:spruce_planks'))
      ])
    )
  })

  event.source('cg_p6:fluids', source => {
    source.add(
      'cg_p6:water_lava',
      'P6 Water and Lava',
      source.any([
        source.fluidId('minecraft:water'),
        source.fluidId('minecraft:lava')
      ])
    )
  })

  event.source('cg_p6:replaceable', source => {
    source.add(
      'cg_p6:replaceable_pair',
      'P6 Replaceable Stone Pair v1',
      source.any([
        source.itemId('minecraft:stone'),
        source.itemId('minecraft:cobblestone')
      ])
    )
  })

  event.source('cg_p6:readd', source => {
    source.add(
      'cg_p6:readd_anchor',
      'P6 Readd Anchor v1',
      source.any([
        source.exactItem(swiftnessPotion),
        source.itemId('minecraft:amethyst_shard')
      ])
    )
  })

  if (cgP6DirectThrow) {
    event.source('cg_p6:direct_post', source => {
      source.item('cg_p6:direct_partial', 'P6 MUST NOT PUBLISH Direct Partial', 'minecraft:diamond')
    })
    throw new Error('cg-p6-intentional-direct-post-failure')
  }

  event.source('cg_p6:direct_post', source => {
    source.item('cg_p6:direct_baseline', 'P6 Direct Post Baseline', 'minecraft:gold_ingot')
  })
})

console.info('[CG-P6] fixture=01-v1 registered sources=native_ingredients,logic,fluids,replaceable,readd')

let cgP6Phase01Ticks = 0
let cgP6Phase01Done = false
let cgP6Phase01Armed = false
ClientEvents.tick(event => {
  if (cgP6Phase01Done) return
  if (!cgP6Phase01Armed) {
    if (P6Minecraft.getInstance().player === null || !(P6Minecraft.getInstance().screen instanceof P6ContainerScreen)) return
    cgP6Phase01Armed = true
    console.info('[CG-P6-AUDIT] phase=01-publication state=ARMED trigger=inventory-screen-open')
  }
  cgP6Phase01Ticks++
  const names = p6Names()
  const nativeStrictReady = P6IsForge
    ? !names.includes('P6 Native Strict NBT Healing Only')
    : names.includes('P6 Native Strict NBT Healing Only')
  const ready = names.includes('P6 Exact Healing Only') &&
    nativeStrictReady &&
    names.includes('P6 Legal Peer Emerald') &&
    names.includes('P6 Oak or Birch, Not Spruce') &&
    names.includes('P6 Water and Lava') &&
    names.includes('P6 Replaceable Stone Pair v1') &&
    names.includes('P6 Readd Anchor v1') &&
    names.includes('P6 Direct Post Baseline')
  if (!ready && cgP6Phase01Ticks < 400) return
  cgP6Phase01Done = true
  p6Assert(ready, 'phase 01 publication did not become ready within 400 ticks')
  p6Assert(names.includes('P6 Exact Healing Only'), 'portable exact healing group missing')
  p6Assert(P6IsForge
    ? !names.includes('P6 Native Strict NBT Healing Only')
    : names.includes('P6 Native Strict NBT Healing Only'), 'loader-native strict group has the wrong publication state')
  p6Assert(!names.includes('P6 REJECT Weak NBT'), 'weakNBT group published')
  p6Assert(!names.includes('P6 REJECT Custom Predicate'), 'custom predicate group published')
  p6Assert(names.includes('P6 Legal Peer Emerald'), 'legal peer was discarded')
  p6Assert(names.includes('P6 Oak or Birch, Not Spruce'), 'boolean logic group missing')
  p6Assert(names.includes('P6 Water and Lava'), 'fluid group missing')
  p6Assert(names.includes('P6 Replaceable Stone Pair v1'), 'replaceable v1 group missing')
  p6Assert(names.includes('P6 Readd Anchor v1'), 'readd v1 group missing')
  const checkpoint = P6ScriptedGroupStore.publicationCheckpoint()
  const baselineNames = p6Names().join('\n')
  cgP6DirectThrow = true
  P6GroupBridge.applyGroups()
  const failedNames = p6Names().join('\n')
  p6Assert(failedNames === baselineNames, 'failed direct post changed the published repository')
  p6Assert(!failedNames.includes('P6 MUST NOT PUBLISH Direct Partial'), 'failed direct post published its partial source')
  p6Assert(!P6ScriptedGroupStore.markAppliedAfter(checkpoint), 'failed direct post was marked applied')
  cgP6DirectThrow = false
  P6GroupBridge.applyGroups()
  p6Assert(p6Names().filter(name => name === 'P6 Direct Post Baseline').length === 1, 'direct post baseline did not recover once')
  console.info('[CG-P6-AUDIT] phase=01-publication result=PASS exactHealing=present nativeStrict=' + (P6IsForge ? 'absent' : 'present') + ' weak=absent custom=absent legalPeer=present logic=present fluids=present replaceable=v1 readd=v1 directPostAtomic=true')
})
