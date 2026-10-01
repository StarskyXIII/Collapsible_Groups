CGEvents.groups(event => console.info('[CG-BRIDGE] publication-namespace'));
RecipeViewerEvents.groupEntries('item', event => event.group('@minecraft','bridge:namespace','Bridge Script Vanilla Items'));
RecipeViewerEvents.groupEntries('fluid', event => event.group('@minecraft','bridge:namespace','Bridge Script Vanilla Fluids'));
console.info('[CG-BRIDGE] fixture-namespace loaded');
