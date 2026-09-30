CGEvents.groups(event => {
 console.info('[CG-BRIDGE] publication-failure');
 event.source('bridge:alpha', source => source.add('bridge:partial','Bridge Script Must Never Publish',
  source.any([source.itemId('minecraft:diamond'),source.itemId('minecraft:emerald')])));
 throw new Error('cg-bridge-intentional-publication-error');
});
console.info('[CG-BRIDGE] fixture-failure loaded');
