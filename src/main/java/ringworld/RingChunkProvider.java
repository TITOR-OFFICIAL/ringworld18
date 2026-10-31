package ringworld;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkProviderGenerate;

/**
 * Обычный генератор Minecraft, но с периодическим по X рельефом и стеной по Z.
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
 * Стена (если halfWidth > 0): бедрок толщиной в 1 блок на z = halfWidth и z = -halfWidth-1,
 * от y=0 до y=255. Свободная полоса: блоки z от -halfWidth до halfWidth-1 (ширина 2*halfWidth).
 * Всё за стеной - пустые чанки (воздух), их генерация почти ничего не стоит.
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
        final int hw = RingWorldMod.halfWidth;
        final int z0 = cz << 4;
        final int z1 = z0 + 15;

        // весь чанк за стеной - пустой чанк
        if (hw > 0 && (z0 > hw || z1 < -hw - 1)) {
            return emptyChunk(cx, cz);
        }

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

        final boolean touchesWall = hw > 0 && (z1 >= hw || z0 <= -hw - 1);

        if (!flip && base == cx && !touchesWall) {
            // обычный чанк базовой половины - ванильная генерация
            return super.provideChunk(cx, cz);
        }
        Chunk src = super.provideChunk(base, cz);
        return makeCopy(src, cx, cz, flip, hw);
    }

    /**
     * После обычного декорирования восстанавливаем стену: деревни, шахты и прочее
     * могли задеть её блоки. Декорирование чанка (cx, cz) затрагивает чанки cx..cx+1 и cz..cz+1.
     */
    @Override
    public void populate(IChunkProvider provider, int cx, int cz) {
        super.populate(provider, cx, cz);

        final int hw = RingWorldMod.halfWidth;
        if (hw <= 0) {
            return;
        }
        final IBlockState bedrock = Blocks.bedrock.getDefaultState();
        for (int dz = 0; dz <= 1; dz++) {
            final int zBase = (cz + dz) << 4;
            for (int lz = 0; lz < 16; lz++) {
                final int wz = zBase + lz;
                if (wz != hw && wz != -hw - 1) {
                    continue;
                }
                for (int dx = 0; dx <= 1; dx++) {
                    final int xBase = (cx + dx) << 4;
                    for (int lx = 0; lx < 16; lx++) {
                        for (int y = 0; y < 256; y++) {
                            BlockPos pos = new BlockPos(xBase + lx, y, wz);
                            if (this.ringWorld.getBlockState(pos).getBlock() != Blocks.bedrock) {
                                this.ringWorld.setBlockState(pos, bedrock, 2);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Пустой чанк (воздух) - для области за стеной. */
    private Chunk emptyChunk(int cx, int cz) {
        Chunk out = new Chunk(this.ringWorld, new ChunkPrimer(), cx, cz);
        out.generateSkylightMap();
        return out;
    }

    /**
     * Копия чанка src в позицию (cx, cz), при flip - с отражением по X.
     * При hw > 0: блоки за стеной не копируются, а на линиях стены ставится бедрок.
     */
    private Chunk makeCopy(Chunk src, int cx, int cz, boolean flip, int hw) {
        ChunkPrimer primer = new ChunkPrimer();
        final int zBase = cz << 4;
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
                    if (hw > 0) {
                        final int wz = zBase + lz;
                        if (wz >= hw || wz <= -hw - 1) {
                            continue; // стена или то, что за ней - копировать не нужно
                        }
                    }
                    for (int ly = 0; ly < 16; ly++) {
                        IBlockState state = sec.get(lx, ly, lz);
                        if (state.getBlock() != Blocks.air) {
                            primer.setBlockState(tx, baseY + ly, lz, state);
                        }
                    }
                }
            }
        }

        if (hw > 0) {
            final IBlockState bedrock = Blocks.bedrock.getDefaultState();
            for (int lz = 0; lz < 16; lz++) {
                final int wz = zBase + lz;
                if (wz == hw || wz == -hw - 1) {
                    for (int tx = 0; tx < 16; tx++) {
                        for (int y = 0; y < 256; y++) {
                            primer.setBlockState(tx, y, lz, bedrock);
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
