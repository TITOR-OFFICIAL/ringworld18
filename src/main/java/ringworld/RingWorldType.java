package ringworld;

import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;

/** Тип мира "ringworld" (в списке типов мира при создании). */
public class RingWorldType extends WorldType {

    public RingWorldType() {
        super("ringworld");
    }

    @Override
    public IChunkProvider getChunkGenerator(World world, String generatorOptions) {
        return new RingChunkProvider(world, world.getSeed(),
                world.getWorldInfo().isMapFeaturesEnabled(), generatorOptions);
    }
}
