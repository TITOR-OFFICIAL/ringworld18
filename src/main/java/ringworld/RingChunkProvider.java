package ringworld;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkProviderGenerate;

/**
 * Обычный генератор Minecraft, но с периодическим по X рельефом.
 *
 * Кольцо длиной N чанков. Настоящий (генерируемый ванильным кодом) рельеф лежит
 * только в "базовой" половине: чанки [-N/4, N/4). Вторая половина кольца - зеркальная
 * копия базовой (отражение по оси X). Благодаря отражению на стыках нет обрывов:
 * соседние колонки блоков на плоскости отражения совпадают.
 *
 * Любой чанк cx (в том числе далеко за пределами кольца) приводится к базовому чанку
 * по модулю N. Поэтому за "швом" кольца всегда есть чанки, которые выглядят так же,
 * как чанки на другом конце кольца. Игрока при пересечении шва телепортирует
 * RingWrapHandler.
 *
 * Оптимизация: копия делается один раз при генерации чанка, дальше это обычный чанк.
 */
public class RingChunkProvider extends ChunkProviderGenerate {

    private final World ringWorld;

    public RingChunkProvider(World world, long seed, boolean mapFeatures, String options) {
        super(world, seed, mapFeatures, options);
        this.ringWorld = world;
    }

    @Override
    public Chunk provideChunk(int cx, int cz) {
        final int n = RingWorldMod.ringChunks;
        final int u = Math.floorMod(cx + n / 4, n); // 0..n-1

        final int base;
        final boolean flip;
        if (u < n / 2) {
            base = u - n / 4;
            flip = false;
        } else {
            base = (n - 1 - u) - n / 4;
            flip = true;
        }

        if (!flip && base == cx) {
            // обычный чанк базовой половины - ванильная генерация
            return super.provideChunk(cx, cz);
        }
        Chunk src = super.provideChunk(base, cz);
        return makeCopy(src, cx, cz, flip);
    }

    /** Копия чанка src в позицию (cx, cz), при flip - с отражением по X. */
    private Chunk makeCopy(Chunk src, int cx, int cz, boolean flip) {
        ChunkPrimer primer = new ChunkPrimer();
        ExtendedBlockStorage[] sections = src.getBlockStorageArray();

        for (int s = 0; s < sections.length; s++) {
            ExtendedBlockStorage sec = sections[s];
            if (sec == null || sec.isEmpty()) {
                continue;
            }
            final int baseY = s << 4;
            for (int lx = 0; lx < 16; lx++) {
                final int tx = flip ? 15 - lx : lx;
                for (int lz = 0; lz < 16; lz++) {
                    for (int ly = 0; ly < 16; ly++) {
                        IBlockState state = sec.get(lx, ly, lz);
                        if (state.getBlock() != Blocks.air) {
                            primer.setBlockState(tx, baseY + ly, lz, state);
                        }
                    }
                }
            }
        }

        Chunk out = new Chunk(this.ringWorld, primer, cx, cz);

        byte[] from = src.getBiomeArray();
        byte[] to = out.getBiomeArray();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int tx = flip ? 15 - lx : lx;
                to[lz << 4 | tx] = from[lz << 4 | lx];
            }
        }

        out.generateSkylightMap();
        return out;
    }
}
