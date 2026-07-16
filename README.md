# BetterCooking
A plugin that integrates with nexoitems and allows users to cook food in an interactive and fun way

PLUGIN CONCEPT: Cooking Pot / Meal Crafting System

CORE IDEA
A custom "pot" or "cooking station" block/GUI where players add ingredients
(using Nexo custom items) in a specific order and specific quantity — like a
recipe. Once the correct ingredients are added, the player plays through a
series of mini-games. If completed, the ingredients combine into a final
meal item (also a Nexo custom item).

KEY FEATURES

1. Ingredient Input
   - Player interacts with a station (block or GUI) and adds ingredients
     one at a time or in batches.
   - Ingredients must match a recipe: correct item, correct order, correct
     amount (e.g. 2x Flour, then 1x Egg, then 3x Sugar).
   - Should support adding multiple ingredients at once (e.g. dropping a
     stack of 3 sugar counts as one recipe step, not 3 separate steps).

2. Custom Recipes
   - Recipes are configurable (likely YAML config) — each recipe defines:
     - ordered list of ingredients + amounts
     - resulting meal item
     - which mini-games trigger for that recipe
   - Wrong ingredient/order/amount = recipe fails or resets.

3. Mini-games
   - After ingredients are validated, player goes through a sequence of
     mini-games (chopping, stirring, timing-based clicks, etc.).
   - Mini-games could be randomized per recipe or fixed per recipe type.
   - Success/failure on mini-games could affect meal quality (optional
     stretch goal — e.g. "burnt" vs "perfect" meal).

4. Final Output
   - On completing mini-games, ingredients are "consumed" and combined into
     a final meal — a custom Nexo item.
   - Meal item could have custom lore/stats depending on how well the
     mini-games went (optional).

5. Nexo Integration
   - All ingredients and final meals should reference Nexo custom item IDs,
     not vanilla items.

OPEN QUESTIONS FOR DEV
   - GUI type: custom inventory GUI vs physical block interaction?
   - Config format: YAML recipe files, one per recipe or one master file?
   - Mini-game style: simple click-timing, or more complex interactions?
   - Should failed recipes return ingredients, partially refund, or lose them?
   - Meal quality tiers: yes/no, and if yes, how many tiers?
