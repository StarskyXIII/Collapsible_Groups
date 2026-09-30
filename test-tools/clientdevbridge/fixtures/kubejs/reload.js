CGEvents.groups(event => {
 console.info('[CG-BRIDGE] publication-reload');
 event.source('bridge:beta', source => source.add('bridge:blocks','Bridge Script Dirt Pair',
  source.any([source.itemId('minecraft:dirt'),source.itemId('minecraft:cobblestone')])));
});
RecipeViewerEvents.groupEntries('item', event => event.group(['minecraft:coal','minecraft:redstone'],'bridge:legacy','Bridge Script Legacy'));
console.info('[CG-BRIDGE] fixture-reload loaded');
