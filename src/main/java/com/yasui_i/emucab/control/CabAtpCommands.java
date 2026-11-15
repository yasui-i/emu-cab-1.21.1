package com.yasui_i.emucab.control;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator preview and an explicit entry point for command-driven route systems. */
public final class CabAtpCommands {
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("emucab").requires(s->s.hasPermission(2))
            .then(Commands.literal("atp")
                .then(Commands.argument("pos",BlockPosArgument.blockPos())
                    .then(Commands.argument("cue",StringArgumentType.word())
                        .suggests((context,builder)-> {
                            for(var cue:AtpAnnouncement.values())builder.suggest(cue.id());
                            return builder.buildFuture();
                        })
                        .executes(context-> {
                            var source=context.getSource();
                            var pos=BlockPosArgument.getLoadedBlockPos(context,"pos");
                            var center=CabDeskBlock.findCenter(source.getLevel(),pos,source.getLevel().getBlockState(pos));
                            if(center==null || !(source.getLevel().getBlockEntity(center) instanceof CabDeskBlockEntity desk))
                                throw new SimpleCommandExceptionType(Component.translatable("message.emucab.atp_no_desk")).create();
                            AtpAnnouncement cue;
                            try { cue=AtpAnnouncement.valueOf(StringArgumentType.getString(context,"cue").toUpperCase(java.util.Locale.ROOT)); }
                            catch(IllegalArgumentException error) {
                                throw new SimpleCommandExceptionType(Component.translatable("message.emucab.atp_unknown")).create();
                            }
                            if(!desk.requestAtpAnnouncement(cue))
                                throw new SimpleCommandExceptionType(Component.translatable("message.emucab.atp_unavailable")).create();
                            var selected=cue;
                            source.sendSuccess(()->Component.translatable("message.emucab.atp_queued",
                                    Component.translatable("subtitle.emucab.atp."+selected.id())),false);
                            return 1;
                        })))));
    }
    private CabAtpCommands() {}
}
