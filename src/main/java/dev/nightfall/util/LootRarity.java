package dev.nightfall.util;

import java.util.Set;

/**
 * Central Fortnite-style loot rarity mapping for vanilla Minecraft item IDs.
 * IDs are taken from the Nightfall survival loot table; Common is the fallback
 * for other vanilla IDs, while non-vanilla items return -1 so callers can keep
 * their regular color behavior.
 */
public final class LootRarity {
   private static final String NAMESPACE = "minecraft:";

   private static final Set<String> UNCOMMON = ids(
      "amethyst_block,amethyst_cluster,amethyst_shard,anvil,axolotl_bucket,blackstone_slab,blackstone_stairs,blackstone_wall,blast_furnace,book,bookshelf,bowl,brewing_stand,bucket,calibrated_sculk_sensor,campfire,chainmail_boots,chainmail_chestplate,chainmail_helmet,chainmail_leggings,chipped_anvil,chiseled_bookshelf,chiseled_copper,chiseled_quartz_block,chiseled_stone_bricks,clock,cobblestone_slab,cobblestone_stairs,cobblestone_wall,cod_bucket,comparator,compass,copper_bars,copper_block,copper_boots,copper_bulb,copper_chain,copper_chest,copper_chestplate,copper_door,copper_golem_statue,copper_grate,copper_helmet,copper_hoe,copper_horse_armor,copper_ingot,copper_lantern,copper_leggings,copper_nautilus_armor,copper_nugget,copper_ore,copper_pickaxe,copper_shovel,copper_torch,copper_trapdoor,cracked_polished_blackstone_bricks,cracked_stone_bricks,cut_copper,cut_copper_slab,cut_copper_stairs,cut_red_sandstone_slab,cut_sandstone_slab,damaged_anvil,deepslate_copper_ore,deepslate_emerald_ore,deepslate_lapis_ore,deepslate_redstone_ore,disc_fragment_5,dispenser,dripstone_block,dropper,emerald,emerald_block,emerald_ore,enchanting_table,end_stone_brick_slab,end_stone_brick_stairs,end_stone_brick_wall,end_stone_bricks,ender_chest,experience_bottle,exposed_chiseled_copper,exposed_copper,exposed_copper_bars,exposed_copper_bulb,exposed_copper_chain,exposed_copper_chest,exposed_copper_door,exposed_copper_golem_statue,exposed_copper_grate,exposed_copper_lantern,exposed_copper_trapdoor,exposed_cut_copper,exposed_cut_copper_slab,exposed_cut_copper_stairs,filled_map,fishing_rod,flint_and_steel,furnace,furnace_minecart,glass_bottle,glow_ink_sac,glowstone_dust,gold_block,gold_ingot,gold_nugget,golden_boots,golden_chestplate,golden_helmet,golden_hoe,golden_horse_armor,golden_leggings,golden_nautilus_armor,golden_pickaxe,golden_shovel,honey_bottle,honeycomb,honeycomb_block,hopper,hopper_minecart,ink_sac,iron_block,iron_boots,iron_chestplate,iron_helmet,iron_hoe,iron_horse_armor,iron_ingot,iron_leggings,iron_nautilus_armor,iron_nugget,iron_pickaxe,iron_shovel,jack_o_lantern,lantern,lapis_block,lapis_lazuli,lapis_ore,lava_bucket,leather,leather_boots,leather_chestplate,leather_helmet,leather_horse_armor,leather_leggings,map,milk_bucket,mossy_cobblestone_slab,mossy_cobblestone_stairs,mossy_cobblestone_wall,mossy_stone_brick_slab,mossy_stone_brick_stairs,mossy_stone_brick_wall,mossy_stone_bricks,nether_quartz_ore,observer,oxidized_chiseled_copper,oxidized_copper,oxidized_copper_bars,oxidized_copper_bulb,oxidized_copper_chain,oxidized_copper_chest,oxidized_copper_door,oxidized_copper_golem_statue,oxidized_copper_grate,oxidized_copper_lantern,oxidized_copper_trapdoor,oxidized_cut_copper,oxidized_cut_copper_slab,oxidized_cut_copper_stairs,paper,piston,polished_blackstone_brick_slab,polished_blackstone_brick_stairs,polished_blackstone_brick_wall,polished_blackstone_bricks,polished_blackstone_button,polished_blackstone_pressure_plate,polished_blackstone_slab,polished_blackstone_stairs,polished_blackstone_wall,powder_snow_bucket,pufferfish_bucket,quartz,quartz_block,quartz_bricks,quartz_pillar,quartz_slab,quartz_stairs,raw_copper,raw_copper_block,raw_gold,raw_gold_block,raw_iron,raw_iron_block,red_sandstone_slab,red_sandstone_stairs,red_sandstone_wall,redstone,redstone_block,redstone_lamp,redstone_ore,redstone_torch,repeater,salmon_bucket,sandstone_slab,sandstone_stairs,sandstone_wall,sculk,sculk_catalyst,sculk_sensor,sculk_shrieker,sculk_vein,sea_lantern,shears,silence_armor_trim_smithing_template,smoker,smooth_quartz,smooth_quartz_slab,smooth_quartz_stairs,smooth_red_sandstone_slab,smooth_red_sandstone_stairs,smooth_sandstone_slab,smooth_sandstone_stairs,smooth_stone_slab,soul_campfire,soul_lantern,soul_torch,sticky_piston,stone_brick_slab,stone_brick_stairs,stone_brick_wall,stone_bricks,stone_button,stone_hoe,stone_pickaxe,stone_pressure_plate,stone_shovel,stone_slab,stone_stairs,tadpole_bucket,torch,torchflower,torchflower_seeds,tropical_fish_bucket,water_bucket,waxed_chiseled_copper,waxed_copper_bars,waxed_copper_block,waxed_copper_bulb,waxed_copper_chain,waxed_copper_chest,waxed_copper_door,waxed_copper_golem_statue,waxed_copper_grate,waxed_copper_lantern,waxed_copper_trapdoor,waxed_cut_copper,waxed_cut_copper_slab,waxed_cut_copper_stairs,waxed_exposed_chiseled_copper,waxed_exposed_copper,waxed_exposed_copper_bars,waxed_exposed_copper_bulb,waxed_exposed_copper_chain,waxed_exposed_copper_chest,waxed_exposed_copper_door,waxed_exposed_copper_golem_statue,waxed_exposed_copper_grate,waxed_exposed_copper_lantern,waxed_exposed_copper_trapdoor,waxed_exposed_cut_copper,waxed_exposed_cut_copper_slab,waxed_exposed_cut_copper_stairs,waxed_oxidized_chiseled_copper,waxed_oxidized_copper,waxed_oxidized_copper_bars,waxed_oxidized_copper_bulb,waxed_oxidized_copper_chain,waxed_oxidized_copper_chest,waxed_oxidized_copper_door,waxed_oxidized_copper_golem_statue,waxed_oxidized_copper_grate,waxed_oxidized_copper_lantern,waxed_oxidized_copper_trapdoor,waxed_oxidized_cut_copper,waxed_oxidized_cut_copper_slab,waxed_oxidized_cut_copper_stairs,waxed_weathered_chiseled_copper,waxed_weathered_copper,waxed_weathered_copper_bars,waxed_weathered_copper_bulb,waxed_weathered_copper_chain,waxed_weathered_copper_chest,waxed_weathered_copper_door,waxed_weathered_copper_golem_statue,waxed_weathered_copper_grate,waxed_weathered_copper_lantern,waxed_weathered_copper_trapdoor,waxed_weathered_cut_copper,waxed_weathered_cut_copper_slab,waxed_weathered_cut_copper_stairs,weathered_chiseled_copper,weathered_copper,weathered_copper_bars,weathered_copper_bulb,weathered_copper_chain,weathered_copper_chest,weathered_copper_door,weathered_copper_golem_statue,weathered_copper_grate,weathered_copper_lantern,weathered_copper_trapdoor,weathered_cut_copper,weathered_cut_copper_slab,weathered_cut_copper_stairs,wooden_hoe,wooden_pickaxe,wooden_shovel,writable_book,written_book"
   );

   private static final Set<String> RARE = ids(
      "blaze_powder,blaze_rod,coast_armor_trim_smithing_template,deepslate_diamond_ore,diamond,diamond_ore,dune_armor_trim_smithing_template,enchanted_book,ender_eye,ender_pearl,eye_armor_trim_smithing_template,ghast_tear,goat_horn,golden_apple,heart_of_the_sea,host_armor_trim_smithing_template,magma_cream,music_disc_11,music_disc_13,music_disc_5,music_disc_blocks,music_disc_cat,music_disc_chirp,music_disc_far,music_disc_lava_chicken,music_disc_mall,music_disc_mellohi,music_disc_relic,music_disc_stal,music_disc_strad,music_disc_tears,music_disc_wait,music_disc_ward,name_tag,nautilus_shell,ominous_bottle,phantom_membrane,prismarine_crystals,prismarine_shard,rabbit_foot,raiser_armor_trim_smithing_template,recovery_compass,rib_armor_trim_smithing_template,saddle,sentry_armor_trim_smithing_template,shaper_armor_trim_smithing_template,slime_ball,sniffer_egg,snout_armor_trim_smithing_template,spire_armor_trim_smithing_template,sponge,trial_key,turtle_scute,vex_armor_trim_smithing_template,ward_armor_trim_smithing_template,wayfinder_armor_trim_smithing_template,wet_sponge,wild_armor_trim_smithing_template,wither_rose"
   );

   private static final Set<String> EPIC = ids(
      "ancient_debris,beacon,black_shulker_box,blue_shulker_box,bolt_armor_trim_smithing_template,breeze_rod,brown_shulker_box,conduit,cyan_shulker_box,diamond_block,diamond_boots,diamond_chestplate,diamond_helmet,diamond_hoe,diamond_horse_armor,diamond_leggings,diamond_nautilus_armor,diamond_pickaxe,diamond_shovel,echo_shard,flow_armor_trim_smithing_template,gray_shulker_box,green_shulker_box,light_blue_shulker_box,light_gray_shulker_box,lime_shulker_box,magenta_shulker_box,netherite_scrap,netherite_upgrade_smithing_template,ominous_trial_key,orange_shulker_box,pink_shulker_box,purple_shulker_box,red_shulker_box,shulker_box,tide_armor_trim_smithing_template,white_shulker_box,wind_charge,yellow_shulker_box"
   );

   private static final Set<String> LEGENDARY = ids(
      "dragon_head,end_crystal,heavy_core,music_disc_creator,music_disc_creator_music_box,music_disc_otherside,music_disc_pigstep,music_disc_precipice,nether_star,netherite_block,netherite_boots,netherite_chestplate,netherite_helmet,netherite_hoe,netherite_horse_armor,netherite_ingot,netherite_leggings,netherite_nautilus_armor,netherite_pickaxe,netherite_shovel,shulker_shell,totem_of_undying,wither_skeleton_skull"
   );

   private static final Set<String> MYTHIC = ids(
      "dragon_egg,elytra,enchanted_golden_apple"
   );

   private LootRarity() {
   }

   private static Set<String> ids(String csv) {
      return Set.of(csv.split(","));
   }

   /**
    * Returns the Fortnite-style tier, or null for non-vanilla item IDs.
    */
   public static String tier(String itemId) {
      String path = path(itemId);
      if (path == null) return null;
      if (MYTHIC.contains(path)) return "Mythic";
      if (LEGENDARY.contains(path)) return "Legendary";
      if (EPIC.contains(path)) return "Epic";
      if (RARE.contains(path)) return "Rare";
      if (UNCOMMON.contains(path)) return "Uncommon";
      return "Common";
   }

   /**
    * Returns the tier color as RGB, or -1 when the item is not a vanilla item.
    */
   public static int color(String itemId) {
      String tier = tier(itemId);
      if (tier == null) return -1;
      return switch (tier) {
         case "Mythic" -> 0xEAB308;
         case "Legendary" -> 0xF59E0B;
         case "Epic" -> 0xA855F7;
         case "Rare" -> 0x3B82F6;
         case "Uncommon" -> 0x22C55E;
         default -> 0x9CA3AF;
      };
   }

   private static String path(String itemId) {
      return itemId != null && itemId.startsWith(NAMESPACE)
         ? itemId.substring(NAMESPACE.length())
         : null;
   }
}
