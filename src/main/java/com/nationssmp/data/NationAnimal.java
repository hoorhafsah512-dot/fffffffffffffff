package com.nationssmp.data;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * Nation animals — strictly real Minecraft mobs only.
 * No renamed wolves pretending to be lions, no phantom eagles.
 * Each has an emoji, GUI icon, and the actual EntityType that spawns.
 *
 * Army army safe types: entities that won't fight their own master
 * and survive in the overworld without special biomes or conditions.
 */
public enum NationAnimal {

    WOLF       ("wolf",      "Wolf",       "🐺", Material.BONE,              EntityType.WOLF),
    FOX        ("fox",       "Fox",        "🦊", Material.SWEET_BERRIES,      EntityType.FOX),
    BEAR       ("bear",      "Polar Bear", "🐻", Material.SNOW_BLOCK,         EntityType.POLAR_BEAR),
    HORSE      ("horse",     "Horse",      "🐴", Material.SADDLE,             EntityType.HORSE),
    CAT        ("cat",       "Cat",        "🐱", Material.COD,                EntityType.CAT),
    PARROT     ("parrot",    "Parrot",     "🦜", Material.FEATHER,            EntityType.PARROT),
    IRON_GOLEM ("golem",     "Iron Golem", "🗿", Material.IRON_INGOT,         EntityType.IRON_GOLEM),
    GOAT       ("goat",      "Goat",       "🐐", Material.GOAT_HORN,          EntityType.GOAT),
    COW        ("cow",       "Cow",        "🐄", Material.WHEAT,              EntityType.COW),
    PANDA      ("panda",     "Panda",      "🐼", Material.BAMBOO,             EntityType.PANDA),
    LLAMA      ("llama",     "Llama",      "🦙", Material.LEATHER,            EntityType.LLAMA),
    BEE        ("bee",       "Bee",        "🐝", Material.HONEYCOMB,          EntityType.BEE),
    AXOLOTL    ("axolotl",   "Axolotl",   "🦎", Material.TROPICAL_FISH,      EntityType.AXOLOTL),
    FROG       ("frog",      "Frog",       "🐸", Material.SLIME_BALL,         EntityType.FROG),
    RABBIT     ("rabbit",    "Rabbit",     "🐇", Material.CARROT,             EntityType.RABBIT),
    TURTLE     ("turtle",    "Turtle",     "🐢", Material.TURTLE_SCUTE,              EntityType.TURTLE),
    BAT        ("bat",       "Bat",        "🦇", Material.BLACK_DYE,          EntityType.BAT),
    MOOSHROOM  ("mooshroom", "Mooshroom",  "🍄", Material.RED_MUSHROOM,       EntityType.MOOSHROOM),
    OCELOT     ("ocelot",    "Ocelot",     "🐈", Material.COD,                EntityType.OCELOT),
    DOLPHIN    ("dolphin",   "Dolphin",    "🐬", Material.COD,                EntityType.DOLPHIN);

    private final String     key;
    private final String     displayName;
    private final String     emoji;
    private final Material   guiIcon;
    private final EntityType entityType;

    NationAnimal(String key, String displayName, String emoji,
                 Material guiIcon, EntityType entityType) {
        this.key         = key;
        this.displayName = displayName;
        this.emoji       = emoji;
        this.guiIcon     = guiIcon;
        this.entityType  = entityType;
    }

    public String     getKey()         { return key; }
    public String     getDisplayName() { return displayName; }
    public String     getEmoji()       { return emoji; }
    public Material   getGuiIcon()     { return guiIcon; }
    public EntityType getEntityType()  { return entityType; }
    public String     getFullLabel()   { return emoji + " " + displayName; }

    /** Look up by key string (case-insensitive). Returns WOLF as fallback. */
    public static NationAnimal byKey(String key) {
        if (key == null) return WOLF;
        for (NationAnimal a : values())
            if (a.key.equalsIgnoreCase(key)) return a;
        return WOLF;
    }
}
