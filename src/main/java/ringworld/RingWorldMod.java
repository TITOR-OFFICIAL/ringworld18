package ringworld;

import net.minecraft.world.WorldType;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * RingWorld для Minecraft 1.8.9 (Forge).
 * Добавляет тип мира "Ring World": мир замкнут по оси X. Идёшь на восток - возвращаешься с запада.
 */
@Mod(modid = RingWorldMod.MODID, name = "RingWorld", version = "0.1", acceptedMinecraftVersions = "[1.8.9]")
public class RingWorldMod {

    public static final String MODID = "ringworld18";

    /** Длина кольца в чанках (кратна 4). Берётся из config/ringworld18.cfg. */
    public static volatile int ringChunks = 256;

    /** Полуширина ленты в блоках: невидимая стена на z = +-значение. 0 = без стены. */
    public static volatile int halfWidth = 1000;

    public static WorldType ringType;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Configuration cfg = new Configuration(event.getSuggestedConfigurationFile());
        int n = cfg.getInt("ringLengthChunks", "ring", 256, 16, 8192,
                "Длина кольца в чанках (1 чанк = 16 блоков). 256 = 4096 блоков. "
                + "Округляется вниз до числа, кратного 4. НЕ МЕНЯТЬ после создания мира!");
        halfWidth = cfg.getInt("halfWidthBlocks", "ring", 1000, 0, 29999000,
                "Полуширина ленты в блоках: невидимая стена на z = +-значение (1000 = лента шириной 2000). "
                + "0 = без стены (мир бесконечен на север и юг). Можно менять в любой момент.");
        if (cfg.hasChanged()) {
            cfg.save();
        }
        n -= n % 4;
        if (n < 16) {
            n = 16;
        }
        ringChunks = n;

        ringType = new RingWorldType();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance().bus().register(new RingWrapHandler());
    }
}
