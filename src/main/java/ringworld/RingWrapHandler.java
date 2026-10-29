package ringworld;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Телепортирует игрока с одного конца кольца на другой.
 * Канонический диапазон X: [-len/2, +len/2). Срабатывает только на сервере,
 * только в верхнем мире (dim 0) и только в мире типа "ringworld".
 * Телепорт относительный (скорость и поворот камеры сохраняются).
 */
public class RingWrapHandler {

    private static final Set<S08PacketPlayerPosLook.EnumFlags> ALL_RELATIVE = EnumSet.of(
            S08PacketPlayerPosLook.EnumFlags.X,
            S08PacketPlayerPosLook.EnumFlags.Y,
            S08PacketPlayerPosLook.EnumFlags.Z,
            S08PacketPlayerPosLook.EnumFlags.Y_ROT,
            S08PacketPlayerPosLook.EnumFlags.X_ROT);

    private static final Set<S08PacketPlayerPosLook.EnumFlags> WALL_FLAGS = EnumSet.of(
            S08PacketPlayerPosLook.EnumFlags.X,
            S08PacketPlayerPosLook.EnumFlags.Y,
            S08PacketPlayerPosLook.EnumFlags.Y_ROT,
            S08PacketPlayerPosLook.EnumFlags.X_ROT);

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != Side.SERVER) {
            return;
        }
        EntityPlayer player = event.player;
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        World world = player.worldObj;
        if (world.provider.getDimensionId() != 0
                || world.getWorldInfo().getTerrainType() != RingWorldMod.ringType) {
            return;
        }

        final double len = RingWorldMod.ringChunks * 16.0D;
        final double half = len / 2.0D;

        EntityPlayerMP mp = (EntityPlayerMP) player;

        // 1) шов кольца по X
        double dx = 0.0D;
        if (player.posX >= half) {
            dx = -len;
        } else if (player.posX < -half) {
            dx = len;
        }
        if (dx != 0.0D) {
            if (mp.ridingEntity != null) {
                mp.mountEntity(null); // верховую езду через шов не переносим
            }
            mp.playerNetServerHandler.setPlayerLocation(dx, 0.0D, 0.0D, 0.0F, 0.0F, ALL_RELATIVE);
        }

        // 2) стена ленты по Z: возвращаем игрока к границе, движение вдоль Z обнуляется
        final int hw = RingWorldMod.halfWidth;
        if (hw > 0) {
            double target;
            if (player.posZ > hw) {
                target = hw - 0.05D;
            } else if (player.posZ < -hw) {
                target = -hw + 0.05D;
            } else {
                return;
            }
            if (mp.ridingEntity != null) {
                mp.mountEntity(null);
            }
            // X, Y и повороты относительные (не меняются), Z абсолютный
            mp.playerNetServerHandler.setPlayerLocation(0.0D, 0.0D, target, 0.0F, 0.0F, WALL_FLAGS);
        }
    }
}
