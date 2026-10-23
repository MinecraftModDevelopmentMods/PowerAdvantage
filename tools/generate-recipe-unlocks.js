// Rebuild the checked-in recipe-book advancements; Node is not needed for normal builds.
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const properties = fs.readFileSync(path.join(root, 'gradle.properties'), 'utf8');
const mod = /^mod_id=(.+)$/m.exec(properties)[1].trim();
const data = path.join(root, 'src/main/resources/data', mod);
const recipes = path.join(data, 'recipes');
const output = path.join(data, 'advancements/recipes');
const check = process.argv.includes('--check');
const ignored = new Set(['item:minecraft:stick', 'item:minecraft:string',
  'item:minecraft:feather', 'tag:forge:rods/wooden']);

function choices(value) {
  if (Array.isArray(value)) return value.flatMap(choices);
  if (value && value.tag) return [{tag: value.tag}];
  if (value && value.item) return [{item: value.item}];
  throw new Error('Unsupported recipe ingredient: ' + JSON.stringify(value));
}

const expected = new Set();
for (const file of fs.readdirSync(recipes).filter(name => name.endsWith('.json')).sort()) {
  const recipe = JSON.parse(fs.readFileSync(path.join(recipes, file), 'utf8'));
  if (!['minecraft:crafting_shaped', 'minecraft:crafting_shapeless', 'minecraft:smelting'].includes(recipe.type)) continue;
  const inputs = choices(recipe.key ? Object.values(recipe.key) : recipe.ingredients || recipe.ingredient);
  const materials = inputs.filter(value => !ignored.has(Object.entries(value)[0].join(':')));
  const unique = [...new Map((materials.length ? materials : inputs).map(value => [JSON.stringify(value), value])).values()];
  const id = `${mod}:${file.slice(0, -5)}`;
  const criteria = {};
  unique.forEach((ingredient, index) => {
    criteria[`has_ingredient_${index + 1}`] = {
      trigger: 'minecraft:inventory_changed', conditions: {items: [ingredient]}
    };
  });
  criteria.has_recipe = {trigger: 'minecraft:recipe_unlocked', conditions: {recipe: id}};
  const advancement = {
    parent: 'minecraft:recipes/root', criteria,
    requirements: [Object.keys(criteria)], rewards: {recipes: [id]}
  };
  const text = JSON.stringify(advancement, null, 2) + '\n';
  const target = path.join(output, file);
  expected.add(file);
  if (check) {
    if (!fs.existsSync(target) || fs.readFileSync(target, 'utf8').replace(/\r\n/g, '\n') !== text) {
      throw new Error('Missing/stale recipe unlock: ' + target);
    }
  } else {
    fs.mkdirSync(output, {recursive: true});
    fs.writeFileSync(target, text);
  }
}
for (const file of fs.existsSync(output) ? fs.readdirSync(output) : []) {
  if (file.endsWith('.json') && !expected.has(file)) throw new Error('Unexpected recipe unlock: ' + file);
}
console.log(`${mod}: ${expected.size} recipe unlocks ${check ? 'verified' : 'generated'}`);
