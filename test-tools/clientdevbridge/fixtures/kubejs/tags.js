CGEvents.groups(event => console.info('[CG-BRIDGE] publication-tags'));
RecipeViewerEvents.groupEntries('item', event => event.group('#minecraft:planks','bridge:tags','Bridge Script Tagged Planks'));
RecipeViewerEvents.groupEntries('fluid', event => event.group('#minecraft:water','bridge:water','Bridge Script Tagged Water'));
console.info('[CG-BRIDGE] fixture-tags loaded');
