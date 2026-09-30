CGEvents.groups(event => {
 console.info('[CG-BRIDGE] publication-v1');
 event.source('bridge:alpha', source => source.add('bridge:planks','Bridge Script Planks',
  source.any([source.itemId('minecraft:oak_planks'),source.itemId('minecraft:birch_planks')])));
 event.source('bridge:beta', source => source.add('bridge:blocks','Bridge Script Stone Pair',
  source.any([source.itemId('minecraft:stone'),source.itemId('minecraft:cobblestone')])));
});
RecipeViewerEvents.groupEntries('item', event => event.group(['minecraft:coal','minecraft:redstone'],'bridge:legacy','Bridge Script Legacy'));
console.info('[CG-BRIDGE] fixture-v1 loaded');
