package emucabthrottleuitest;

import com.yasui_i.emucab.blockentity.GradualThrottleBlockEntity;
import com.yasui_i.emucab.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod("emucabthrottleuitest")
public final class ThrottleUiHarness {
    static final BlockPos POS=new BlockPos(0,85,0);
    public ThrottleUiHarness(){
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e)->{
            com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxRotationSpeed.set(512);
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)e.getServer().overworld().setBlockAndUpdate(new BlockPos(x,84,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            e.getServer().overworld().setBlockAndUpdate(POS,ModBlocks.GRADUAL_THROTTLE.get().defaultBlockState());
            ((GradualThrottleBlockEntity)e.getServer().overworld().getBlockEntity(POS)).configure(1,17);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e)->{
            if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p){
                p.setGameMode(GameType.CREATIVE);p.teleportTo(p.serverLevel(),.5,85,-1.5,0,0);
                p.getServer().getPlayerList().op(p.getGameProfile());
            }
        });
    }
}
