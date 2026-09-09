const S7_ROOT = '{probe:{mode:"same"},byteValue:1b}'
const S7_COMPLEX = '{profile:{settings:{mode:"alpha"}},byteValue:1b,shortValue:1s,intValue:1,longValue:1L,floatValue:1.0f,doubleValue:1.0d,stringValue:"1",list:[1b,2b],bytes:[B;1b,2b],ints:[I;3,4],longs:[L;5L,6L]}'
const S7_COMPLEX_UNEQUAL = '{profile:{settings:{mode:"beta"}},byteValue:1b,shortValue:1s,intValue:1,longValue:1L,floatValue:1.0f,doubleValue:1.0d,stringValue:"1",list:[1b,2b],bytes:[B;1b,2b],ints:[I;3,4],longs:[L;5L,6L]}'
const S7_COMPLEX_FORBIDDEN_EQUAL = '{profile:{settings:{mode:"alpha"}},byteValue:1b,shortValue:1s,intValue:1,longValue:1L,floatValue:1.0f,doubleValue:1.0d,stringValue:"1",list:[1b,2b],bytes:[B;1b,2b],ints:[I;3,4],longs:[L;5L,6L],forbidden:1b}'
const S7_COMPLEX_FORBIDDEN_UNEQUAL = '{profile:{settings:{mode:"alpha"}},byteValue:1b,shortValue:1s,intValue:1,longValue:1L,floatValue:1.0f,doubleValue:1.0d,stringValue:"1",list:[1b,2b],bytes:[B;1b,2b],ints:[I;3,4],longs:[L;5L,6L],forbidden:2b}'

const s7RootStone = Item.of('minecraft:stone', S7_ROOT)
const s7RootCount = s7RootStone.copy()
s7RootCount.setCount(17)
const s7RootDirt = Item.of('minecraft:dirt', S7_ROOT)
const s7RootExtra = Item.of('minecraft:stone', '{probe:{mode:"same"},byteValue:1b,extra:1b}')
const s7RootDifferent = Item.of('minecraft:stone', '{probe:{mode:"different"},byteValue:1b}')
const s7PlainStone = Item.of('minecraft:stone')
const s7Complex = Item.of('minecraft:stone', S7_COMPLEX)
const s7ComplexUnequal = Item.of('minecraft:stone', S7_COMPLEX_UNEQUAL)
const s7ComplexForbiddenEqual = Item.of('minecraft:stone', S7_COMPLEX_FORBIDDEN_EQUAL)
const s7ComplexForbiddenUnequal = Item.of('minecraft:stone', S7_COMPLEX_FORBIDDEN_UNEQUAL)
const s7IntInsteadOfByte = Item.of('minecraft:stone', '{byteValue:1}')
const s7BareString = Item.of('minecraft:stone', '{bareToken:1xb}')
const s7Emerald = Item.of('minecraft:emerald')

const S7IsForge = Platform.isForge()
const S7GroupDefinition = Java.loadClass('com.starskyxiii.collapsible_groups.group.GroupDefinition')
const S7GroupRepository = Java.loadClass('com.starskyxiii.collapsible_groups.group.GroupRepository')
const S7Filters = Java.loadClass('com.starskyxiii.collapsible_groups.group.filter.Filters')
const S7CompiledFilter = Java.loadClass('com.starskyxiii.collapsible_groups.group.filter.CompiledFilter')
const S7HasComponent = Java.loadClass('com.starskyxiii.collapsible_groups.group.filter.GroupFilter$HasComponent')
const S7ItemView = Java.loadClass('com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView')
const S7Minecraft = Java.loadClass('net.minecraft.client.Minecraft')
const S7ContainerScreen = Java.loadClass('net.minecraft.client.gui.screens.inventory.AbstractContainerScreen')
const S7CompoundTag = Java.loadClass('net.minecraft.nbt.CompoundTag')

const s7QuotedKeysRoot = new S7CompoundTag()
const s7EmptyKeyChild = new S7CompoundTag()
s7EmptyKeyChild.putShort('', 7)
s7QuotedKeysRoot.put('a.b:c', s7EmptyKeyChild)
const s7QuotedKeys = Item.of('minecraft:stone', s7QuotedKeysRoot)

function s7Assert(condition, message) {
  if (!condition) throw new Error('[CG-S7-NBT-AUDIT] assertion failed: ' + message)
}

function s7Probe(filter, stack) {
  return S7GroupDefinition.of('cg_s7:runtime_probe', 'S7 Runtime Probe', filter).matches(stack)
}

function s7Find(name) {
  let found = null
  S7GroupRepository.getAllIncludingScripted().forEach(group => {
    if (String(group.name()) === name) found = group
  })
  return found
}

CGEvents.groups(event => {
  event.source('cg_s7:nbt_runtime', source => {
    s7Assert(s7Complex.getNbtString() !== 'null', 'complex Item.of sample must retain its parsed native tag')
    s7Assert(s7QuotedKeys.getNbtString() !== 'null', 'programmatic quoted-key sample must retain its native tag')

    const root = source.nbt(S7_ROOT)
    s7Assert(String(root.getClass().getSimpleName()) === 'Nbt', 'source.nbt must return the native Nbt filter through Rhino')
    s7Assert(s7Probe(root, s7RootStone), 'root must match the exact complete tag')
    s7Assert(s7Probe(root, s7RootCount), 'root must ignore stack count')
    s7Assert(s7Probe(root, s7RootDirt), 'root must ignore item ID')
    s7Assert(!s7Probe(root, s7RootExtra), 'root must reject an extra tag member')
    s7Assert(!s7Probe(root, s7RootDifferent), 'root must reject a different tag member')
    s7Assert(!s7Probe(root, s7PlainStone), 'root must reject an absent tag')

    const nested = source.nbtPath('profile.settings.mode', '"alpha"')
    s7Assert(String(nested.getClass().getSimpleName()) === 'NbtPath', 'source.nbtPath must return the native NbtPath filter through Rhino')
    s7Assert(s7Probe(nested, s7Complex), 'nested compound path must match')
    s7Assert(!s7Probe(nested, s7ComplexUnequal), 'nested compound path must reject an unequal value')
    s7Assert(!s7Probe(nested, s7PlainStone), 'nested compound path must reject a missing intermediate')
    s7Assert(!s7Probe(source.nbtPath('profile.settings.mode[0]', '"alpha"'), s7Complex), 'path must reject a wrong intermediate container')

    const scalarCases = [
      ['byteValue', '1b'],
      ['shortValue', '1s'],
      ['intValue', '1'],
      ['longValue', '1L'],
      ['floatValue', '1.0f'],
      ['doubleValue', '1.0d'],
      ['stringValue', '"1"']
    ]
    scalarCases.forEach(pair => s7Assert(s7Probe(source.nbtPath(pair[0], pair[1]), s7Complex), 'typed scalar must match: ' + pair[0]))
    s7Assert(!s7Probe(source.nbtPath('byteValue', '1b'), s7IntInsteadOfByte), 'byte and int with equal magnitude must remain distinct')
    s7Assert(!s7Probe(source.nbtPath('intValue', '1b'), s7Complex), 'int value must reject a byte expectation')
    s7Assert(s7Probe(source.nbtPath('bareToken', '1xb'), s7BareString), 'native bare non-numeric token must remain a valid string value')

    s7Assert(s7Probe(source.nbtPath('list', '[1b,2b]'), s7Complex), 'ordered typed list must match')
    s7Assert(!s7Probe(source.nbtPath('list', '[2b,1b]'), s7Complex), 'ordered typed list must reject reordering')
    s7Assert(!s7Probe(source.nbtPath('list', '[1b]'), s7Complex), 'ordered typed list must reject a missing element')
    s7Assert(!s7Probe(source.nbtPath('list', '[1b,2b,3b]'), s7Complex), 'ordered typed list must reject an extra element')
    s7Assert(!s7Probe(source.nbtPath('list', '[1,2]'), s7Complex), 'typed list must reject different element type')
    s7Assert(s7Probe(source.nbtPath('list[1]', '2b'), s7Complex), 'path through list must match indexed member')
    s7Assert(!s7Probe(source.nbtPath('list[2]', '2b'), s7Complex), 'path through list must reject out-of-range index')

    s7Assert(s7Probe(source.nbtPath('bytes', '[B;1b,2b]'), s7Complex), 'byte array must match')
    s7Assert(s7Probe(source.nbtPath('ints', '[I;3,4]'), s7Complex), 'int array must match')
    s7Assert(s7Probe(source.nbtPath('longs', '[L;5L,6L]'), s7Complex), 'long array must match')
    s7Assert(!s7Probe(source.nbtPath('bytes', '[1b,2b]'), s7Complex), 'byte array must reject a list')
    s7Assert(!s7Probe(source.nbtPath('bytes', '[I;1,2]'), s7Complex), 'byte array must reject an int array')
    s7Assert(s7Probe(source.nbtPath('bytes[1]', '2b'), s7Complex), 'path through byte array must match indexed member')
    s7Assert(s7Probe(source.nbtPath('ints[1]', '4'), s7Complex), 'path through int array must match indexed member')
    s7Assert(s7Probe(source.nbtPath('longs[1]', '6L'), s7Complex), 'path through long array must match indexed member')
    s7Assert(s7Probe(source.nbtPath('["a.b:c"][""]', '7s'), s7QuotedKeys), 'JSON-quoted keys must match unusual and empty names')
    s7Assert(!s7Probe(source.nbtPath('["a.b:c"][""]', '7'), s7QuotedKeys), 'quoted empty-name short must reject an int expectation')

    const missingOrUnequal = source.not(source.nbtPath('profile.settings.mode', '"alpha"'))
    s7Assert(s7Probe(missingOrUnequal, s7PlainStone), 'NOT path must match missing data')
    s7Assert(s7Probe(missingOrUnequal, s7ComplexUnequal), 'NOT path must match a present unequal value')
    s7Assert(!s7Probe(missingOrUnequal, s7Complex), 'NOT path must reject a present equal value')

    const unavailable = new S7HasComponent('minecraft:custom_name', '{}')
    const unavailableResult = S7CompiledFilter.compile(S7Filters.not(unavailable)).evaluate(new S7ItemView(s7Complex))
    s7Assert(String(unavailableResult) === 'UNAVAILABLE', 'NOT must preserve unavailable rather than matching it')
    const invalidRootResult = S7CompiledFilter.compile(S7Filters.nbt('1b')).evaluate(new S7ItemView(s7Complex))
    const invalidPathResult = S7CompiledFilter.compile(S7Filters.nbtPath('value[*]', '1b')).evaluate(new S7ItemView(s7Complex))
    const notInvalidPathResult = S7CompiledFilter.compile(S7Filters.not(S7Filters.nbtPath('value[*]', '1b'))).evaluate(new S7ItemView(s7Complex))
    s7Assert(String(invalidRootResult) === 'UNAVAILABLE', 'programmatic invalid root NBT must evaluate unavailable')
    s7Assert(String(invalidPathResult) === 'UNAVAILABLE', 'programmatic invalid NBT path must evaluate unavailable')
    s7Assert(String(notInvalidPathResult) === 'UNAVAILABLE', 'NOT must preserve programmatic invalid NBT path as unavailable')

    const mixedAny = source.any([
      source.nbtPath('profile.settings.mode', '"alpha"'),
      source.itemId('minecraft:emerald')
    ])
    s7Assert(s7Probe(mixedAny, s7Complex), 'mixed any must match its NBT path child')
    s7Assert(s7Probe(mixedAny, s7Emerald), 'mixed any must match its item ID child')
    s7Assert(!s7Probe(mixedAny, s7PlainStone), 'mixed any must reject when no child matches')

    const typedAll = source.all([
      source.nbtPath('byteValue', '1b'),
      source.nbtPath('ints[1]', '4')
    ])
    s7Assert(s7Probe(typedAll, s7Complex), 'all must match when every child matches')
    s7Assert(!s7Probe(typedAll, s7IntInsteadOfByte), 'all must reject when one child fails')

    const nestedLogic = source.all([
      mixedAny,
      source.not(source.nbtPath('forbidden', '1b'))
    ])
    s7Assert(s7Probe(nestedLogic, s7Complex), 'nested logic must match when forbidden path is missing')
    s7Assert(s7Probe(nestedLogic, s7ComplexForbiddenUnequal), 'nested logic must match when forbidden path is unequal')
    s7Assert(!s7Probe(nestedLogic, s7ComplexForbiddenEqual), 'nested logic must reject when forbidden path is equal')

    source.add('cg_s7:root', 'S7 NBT Root', root)
    source.add('cg_s7:quoted_path', 'S7 NBT Quoted Path', source.nbtPath('["a.b:c"][""]', '7s'))
    source.add('cg_s7:nested_logic', 'S7 NBT Nested Logic', nestedLogic)
  })
  console.info('[CG-S7-NBT-AUDIT] phase=01-rhino-semantics result=PASS loader=' + (S7IsForge ? 'forge' : 'fabric') + ' root=id-count-independent byte-int=distinct bare-token=string nested=true list=typed-ordered arrays=BIL quoted-keys=true not=missing+unequal unavailable=foreign+invalid-preserved logic=any+all+nested')
})

let s7Phase01Ticks = 0
let s7Phase01Done = false
let s7Phase01Armed = false
ClientEvents.tick(event => {
  if (s7Phase01Done) return
  if (!s7Phase01Armed) {
    if (S7Minecraft.getInstance().player === null || !(S7Minecraft.getInstance().screen instanceof S7ContainerScreen)) return
    s7Phase01Armed = true
    console.info('[CG-S7-NBT-AUDIT] phase=01-publication state=ARMED trigger=inventory-screen-open')
  }
  s7Phase01Ticks++
  const root = s7Find('S7 NBT Root')
  const quoted = s7Find('S7 NBT Quoted Path')
  const logic = s7Find('S7 NBT Nested Logic')
  const ready = root !== null && quoted !== null && logic !== null
  if (!ready && s7Phase01Ticks < 400) return
  s7Phase01Done = true
  s7Assert(ready, 'phase 01 publication did not become ready within 400 ticks')
  s7Assert(root.matches(s7RootStone) && root.matches(s7RootCount) && root.matches(s7RootDirt), 'published root group lost ID/count-independent equality')
  s7Assert(!root.matches(s7RootExtra) && !root.matches(s7PlainStone), 'published root group accepted extra or missing NBT')
  s7Assert(quoted.matches(s7QuotedKeys) && !quoted.matches(s7PlainStone), 'published quoted path group has the wrong match set')
  s7Assert(logic.matches(s7Complex) && logic.matches(s7ComplexForbiddenUnequal), 'published nested logic group lost missing/unequal NOT behavior')
  s7Assert(!logic.matches(s7ComplexForbiddenEqual) && !logic.matches(s7PlainStone), 'published nested logic group accepted an equal forbidden value or unrelated item')
  console.info('[CG-S7-NBT-AUDIT] phase=01-publication result=PASS groups=root,quoted-path,nested-logic published-match-table=exact')
})
