# ArkEnchants

Custom enchantments for Paper 1.21.8+, configured in AdvancedEnchantments' format, built to live next to
ItemsAdder. It is a separate plugin from ArkcronistGenerator and builds on its own:

```
cd arkenchants
mvn package        # target/ArkEnchants-1.0.0.jar
```

## Why it does not break ItemsAdder

- No packets, no ProtocolLib, no resource pack.
- Enchants live in the item's PersistentDataContainer (`arkenchants:enchants`).
- It only adds its own lore lines at the top and remembers which ones they were; the item model, custom model
  data and the lore other plugins put there are left alone. If another plugin rewrites the lore, the lines come
  back the next time the item is held.

## Moving over from AdvancedEnchantments

On first start, if `plugins/AdvancedEnchantments/enchantments.yml` and `groups.yml` exist, they are copied into
`plugins/ArkEnchants/`. The console then says how many enchants loaded and lists any type or effect it does
not support. Items already enchanted by AdvancedEnchantments have their enchant lore lines ("Harvest III")
turned into ArkEnchants data when a player joins or picks the item (`convert-advancedenchantments-lore`).

## What is supported

- Types: ATTACK, ATTACK_MOB, DEFENSE, DEFENSE_MOB, DEFENSE_PROJECTILE, SHOOT, SHOOT_MOB, MINING, KILL_MOB,
  KILL_PLAYER, DEATH, FALL_DAMAGE, FIRE, EXPLOSION, EFFECT_STATIC, HELD, REPEATING, ELYTRA_FLY, RIGHT_CLICK,
  CATCH_FISH, HOOK_ENTITY, BITE_HOOK, ITEM_BREAK.
- Targets: @Victim, @Attacker, @Self, @Aoe{radius,target}, @Block, @Trench{r}, @Tunnel{radiuscustom,mode},
  @Veinmine{limit}. Extra blocks are broken through the player, so protection plugins still decide.
- Level settings: chance, cooldown, conditions (`%stop%`, `%allow%`, `%force%`, `%chance%+N`), per-effect
  `<chance>` and `<condition>`, `<random number>`, `<random word>`, `<math>`, WAIT.
- Effects: every effect listed in `Effects.KNOWN` (the 61 used by the stock AdvancedEnchantments file).
- Books with success/destroy rates (drag onto the item), mystery books per group, `/enchanter` shop paid in XP
  levels, soul tracker, anvil merging of two enchanted items.

Not included: armor sets, custom weapons, gkits, tinkerer, alchemist, scrolls/dust, slot increasers, the HORN
type and REMOVE_ENCHANT.

## Commands

| Command | Permission |
| --- | --- |
| `/arkenchants give <player> <enchant> [level] [success] [destroy]` | arkenchants.admin |
| `/arkenchants mystery <player> <group> [amount]` | arkenchants.admin |
| `/arkenchants apply <enchant> [level]` / `remove <enchant\|all>` | arkenchants.admin |
| `/arkenchants tracker [player]`, `list [group]`, `reload` | arkenchants.admin |
| `/arkenchants info` | everyone |
| `/enchanter` (`/ce`) | arkenchants.enchanter (default: everyone) |
