package com.nationssmp;

import com.nationssmp.commands.CommandRegistrar;
import com.nationssmp.listeners.*;
import com.nationssmp.managers.*;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NationsSMP — Main plugin entry point.
 *
 * Initialisation order:
 *  DataManager → NationManager → AnimalArmyManager → AnimalCompanionManager
 *  → OathManager → LandManager → MartyrManager → TrophyManager
 *  → LegendaryItemManager → GlitchManager → DragonManager
 *  → Listeners → Commands
 */
public class NationsSMP extends JavaPlugin {

    // ── Managers ──────────────────────────────────────────────────────────────
    private DataManager            dataManager;
    private NationManager          nationManager;
    private AnimalArmyManager      animalArmyManager;
    private AnimalCompanionManager animalCompanionManager;
    private OathManager            oathManager;
    private LandManager            landManager;
    private MartyrManager          martyrManager;
    private TrophyManager          trophyManager;
    private LegendaryItemManager   legendaryItemManager;
    private GlitchManager          glitchManager;
    private DragonManager          dragonManager;
    private CastleManager          castleManager;
    private BannerManager          bannerManager;
    private WarManager             warManager;
    private TreasuryManager        treasuryManager;
    private LeaderboardManager     leaderboardManager;
    private NationHistoryManager   nationHistoryManager;

    // ── Listeners (some need to be referenced by managers) ────────────────────
    private PlayerDeathListener deathListener;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        // Config
        saveDefaultConfig();

        // Managers
        dataManager            = new DataManager(this);
        nationManager          = new NationManager(this, dataManager);
        animalArmyManager      = new AnimalArmyManager(this, nationManager);
        animalCompanionManager = new AnimalCompanionManager(this, nationManager);
        oathManager            = new OathManager(this, nationManager, dataManager);
        castleManager          = new CastleManager(this);
        bannerManager          = new BannerManager(this);
        nationHistoryManager   = new NationHistoryManager(this);
        warManager             = new WarManager(this, nationManager);
        treasuryManager        = new TreasuryManager(this);
        leaderboardManager     = new LeaderboardManager(this);
        landManager            = new LandManager(this, nationManager, dataManager);
        martyrManager          = new MartyrManager(this, nationManager);
        trophyManager          = new TrophyManager(this, nationManager);
        legendaryItemManager   = new LegendaryItemManager(this, nationManager);
        glitchManager          = new GlitchManager(this, nationManager, legendaryItemManager);
        dragonManager          = new DragonManager(this, nationManager);

        // Listeners
        deathListener = new PlayerDeathListener(this);
        var pm = getServer().getPluginManager();
        pm.registerEvents(deathListener,                 this);
        pm.registerEvents(new PlayerJoinListener(this),  this);
        pm.registerEvents(new PlayerChatListener(this),  this);
        pm.registerEvents(new InventoryClickListener(this), this);
        pm.registerEvents(new BlockListener(this),       this);
        pm.registerEvents(new WorldChangeListener(this), this);
        pm.registerEvents(new LandBorderListener(this),        this);
        pm.registerEvents(new SiegeListener(this),              this);
        pm.registerEvents(new OathAnimalPacifismListener(this), this);

        // Commands
        CommandRegistrar.registerAll(this);

        getLogger().info("NationsSMP enabled — the nations rise!");
    }

    @Override
    public void onDisable() {
        // Save all data
        if (nationManager != null) nationManager.saveAll();

        // Graceful shutdown of scheduled tasks
        if (animalArmyManager != null) animalArmyManager.shutdown();
        if (glitchManager != null) glitchManager.shutdown();

        getLogger().info("NationsSMP disabled — the nations rest.");
    }

    // ── Getters (used by listeners and commands) ──────────────────────────────

    public DataManager            getDataManager()            { return dataManager; }
    public NationManager          getNationManager()          { return nationManager; }
    public AnimalArmyManager      getAnimalArmyManager()      { return animalArmyManager; }
    public AnimalCompanionManager getAnimalCompanionManager() { return animalCompanionManager; }
    public OathManager            getOathManager()            { return oathManager; }
    public LandManager            getLandManager()            { return landManager; }
    public MartyrManager          getMartyrManager()          { return martyrManager; }
    public TrophyManager          getTrophyManager()          { return trophyManager; }
    public LegendaryItemManager   getLegendaryItemManager()   { return legendaryItemManager; }
    public GlitchManager          getGlitchManager()          { return glitchManager; }
    public DragonManager          getDragonManager()          { return dragonManager; }
    public CastleManager          getCastleManager()          { return castleManager; }
    public BannerManager          getBannerManager()          { return bannerManager; }
    public WarManager             getWarManager()             { return warManager; }
    public TreasuryManager        getTreasuryManager()        { return treasuryManager; }
    public LeaderboardManager     getLeaderboardManager()     { return leaderboardManager; }
    public NationHistoryManager   getNationHistoryManager()   { return nationHistoryManager; }
    public PlayerDeathListener    getDeathListener()          { return deathListener; }
}
