package com.yasui_i.emucab.blockentity;

import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.block.CabDeskPart;
import com.yasui_i.emucab.config.CabConfig;
import com.yasui_i.emucab.control.*;
import com.yasui_i.emucab.registry.ModBlockEntities;
import com.yasui_i.emucab.track.CabTrackLimits;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3d;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.LocalTime;

/** Only the middle block owns state. All physical actions and signals run on the server. */
public class CabDeskBlockEntity extends BlockEntity implements BlockEntitySubLevelActor, CabSignalSource {
    public static final int MAX_NOTCH = CabControlState.MAX_NOTCH;
    private final CabControlState controls = new CabControlState();
    private final CabControlAnimation controlAnimation = new CabControlAnimation();
    private final CabSpeedometer speedometer = new CabSpeedometer();
    private final CabElectricalDisplay electricalDisplay = new CabElectricalDisplay();
    public CabElectricalDisplay electricalDisplay(){return electricalDisplay;}
    private long animationSequence;
    private double speedKmh, shownSpeedKmh;
    private double limitKmh = -1;
    private double trackLimitKmh=-1,nextTrackLimitKmh=-1,nextTrackDistance=Double.POSITIVE_INFINITY;
    private String trackWarning="";
    private final CabTrackLimits trackLimits=new CabTrackLimits();
    private com.yasui_i.emucab.track.CabSpeedPlan speedPlan=com.yasui_i.emucab.track.CabSpeedPlan.EMPTY;
    private final AtpAudioController atpAudio=new AtpAudioController();
    private Map<String,Integer> previousSignals = Map.of();
    /** 0 = game clock / system date, 1 = real clock / custom date. */
    private int clockMode,dateMode;
    private final CabPantographs pantographs=new CabPantographs();
    private String customDate="",locomotiveNumber="";
    private boolean movingForAssembly;
    public void beginAssemblyMove(){movingForAssembly=true;}
    public boolean movingForAssembly(){return movingForAssembly;}
    private double forceScaleKn=CabDisplayFormat.DEFAULT_FORCE_SCALE_KN;
    private java.util.UUID telemetryId=java.util.UUID.randomUUID();
    /** Explicit consist identity: endpoint 1 is Tc1/01, endpoint 8 is Tc2/08. */
    private String consistId="";
    private int endpoint;
    private java.util.UUID linkId=java.util.UUID.randomUUID();
    private double lineVoltage, lineCurrent;
    private final com.yasui_i.emucab.telemetry.CabTelemetry telemetry=new com.yasui_i.emucab.telemetry.CabTelemetry();
    public java.util.UUID telemetryId(){return telemetryId;}
    public com.yasui_i.emucab.telemetry.CabTelemetry telemetry(){return telemetry;}
    public void telemetryChanged(){com.yasui_i.emucab.telemetry.SimurailBrakeBindings.update(this);sync();}
    public String consistId(){return consistId;}
    public int endpoint(){return endpoint;}
    public java.util.UUID linkId(){return linkId;}
    public void renewLinkId(){linkId=java.util.UUID.randomUUID();setChanged();}
    public double lineVoltage(){return lineVoltage;}
    public double lineCurrent(){return lineCurrent;}
    public void setElectricalReading(double voltage,double current){
        lineVoltage=Double.isFinite(voltage)?Math.max(0,voltage):0; lineCurrent=Double.isFinite(current)?Math.abs(current):0;
        setChanged(); sync();
    }
    public void setConsistEndpoint(String id,int end){
        if(level==null||level.isClientSide||!isCenter()||(end!=0&&end!=1&&end!=8))return;
        String clean=com.yasui_i.emucab.control.CabDisplayFormat.locomotive(id);
        if(consistId.equals(clean)&&endpoint==end)return;
        com.yasui_i.emucab.control.CabEndpointRegistry.release(this);
        com.yasui_i.emucab.control.CabEndpointRegistry.unregister(this);
        controls.releaseForTakeover();
        consistId=clean;
        endpoint=end;
        com.yasui_i.emucab.control.CabEndpointRegistry.register(this);
        publishSignals();sync();
    }
    /** Called by the opposite cab when its master key is inserted. */
    public void forceTakeoverRelease(){
        if(level==null||level.isClientSide||!isCenter())return;
        controls.releaseForTakeover();
        if(getBlockState().getValue(CabDeskBlock.LIT))
            level.setBlock(worldPosition,getBlockState().setValue(CabDeskBlock.LIT,false),Block.UPDATE_ALL);
        publishSignals(); sync();
    }

    public CabDeskBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.CAB_DESK.get(), pos, state); }
    public CabControlState controls() { return controls; }
    public CabControlAnimation controlAnimation() { return controlAnimation; }

    public void clientTick() {
        if (level == null) return;
        speedometer.tick();
        speedKmh = speedometer.target();
        shownSpeedKmh = speedometer.latest();
        controls.tick();
        telemetry.expire(level.getGameTime());
        electricalDisplay.tick(CeeElectricalTelemetry.voltage(this),CeeElectricalTelemetry.current(this));
    }

    public void serverTick() {
        if (level == null) return;
        com.yasui_i.emucab.control.CabEndpointRegistry.reconcile(this);
        if(level.getGameTime()%20==0)com.yasui_i.emucab.telemetry.SimurailBrakeBindings.update(this);
        speedKmh = readSpeedKmh(level);
        if(level.getGameTime()%2==0) sendSpeedSample();
        if(level.getGameTime()%4==Math.floorMod(worldPosition.hashCode(),4))sendTelemetrySample();
        if (limitKmh <= 0) limitKmh = CabConfig.DEFAULT_LIMIT.get();
        boolean changed = controls.tick();
        if(level.getGameTime()%5==Math.floorMod(worldPosition.hashCode(),5)) {
            var reading=trackLimits.update(this);
            if(trackLimitKmh!=reading.current() || nextTrackLimitKmh!=reading.next()
                    || Math.abs(nextTrackDistance-reading.distance())>2 || !speedPlan.equals(reading.plan()))changed=true;
            trackLimitKmh=reading.current();nextTrackLimitKmh=reading.next();nextTrackDistance=reading.distance();
            trackWarning=reading.warningKey();
            speedPlan=reading.plan();
        }
        tickAtpTones();
        publishSignals();
        // Speed uses its own small payload; only changed controls need a block update.
        // Periodic UPDATE_ALL would rebuild the static chunk mesh twice per second.
        if (changed) sync();
    }

    private void tickAtpTones() {
        var cue=atpAudio.tick(CabConfig.ATP_TONES.get() && controls.displaysActive(),speedKmh,getLimitKmh(),trackWarning);
        if(cue!=null) {
            Vec3 position=com.yasui_i.emucab.network.CabAtpSoundPayload.worldPosition(level,worldPosition);
            float volume=CabConfig.ATP_VOLUME.get().floatValue();
            var packet=new com.yasui_i.emucab.network.CabAtpSoundPayload(worldPosition,cue.id(),volume);
            var simultaneous=atpAudio.simultaneous();
            // Delivery includes a margin for network/pose delay. The client applies
            // the exact 8-block audible radius and rendered-pose gain.
            if(level instanceof net.minecraft.server.level.ServerLevel server)
                for(var player:server.players())if(player.getEyePosition().distanceToSqr(position)<=CabAtpAcoustics.DELIVERY_RADIUS*CabAtpAcoustics.DELIVERY_RADIUS) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,packet);
                    if(simultaneous!=null)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                            new com.yasui_i.emucab.network.CabAtpSoundPayload(worldPosition,simultaneous.id(),volume));
                }
        }
    }

    /** Server-thread API for future route/CTCS adapters; does not invent route events. */
    public boolean requestAtpAnnouncement(AtpAnnouncement cue) {
        return level!=null && !level.isClientSide && isCenter() && CabConfig.ATP_TONES.get()
                && controls.displaysActive() && atpAudio.request(cue);
    }

    private double readSpeedKmh(Level level) {
        Vec3 v=SableCompanion.INSTANCE.getVelocity(level,Vec3.atCenterOf(worldPosition));
        return Math.sqrt(v.x*v.x+v.z*v.z)*CabConfig.VELOCITY_SCALE.get();
    }

    private void sendSpeedSample() {
        if(!(level instanceof net.minecraft.server.level.ServerLevel server))return;
        Vec3 position=com.yasui_i.emucab.network.CabAtpSoundPayload.worldPosition(level,worldPosition);
        var packet=new com.yasui_i.emucab.network.CabSpeedPayload(worldPosition,level.getGameTime(),speedKmh);
        for(var player:server.players())if(player.distanceToSqr(position.x,position.y,position.z)<=64*64)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,packet);
    }
    private void sendTelemetrySample(){
        if(!(level instanceof net.minecraft.server.level.ServerLevel server))return;
        var cars=com.yasui_i.emucab.telemetry.SimurailTelemetry.read(this);telemetry.serverCars(cars);
        var packet=new com.yasui_i.emucab.network.CabTelemetryPayload(worldPosition,level.getGameTime(),cars);
        Vec3 position=com.yasui_i.emucab.network.CabAtpSoundPayload.worldPosition(level,worldPosition);
        for(var player:server.players())if(player.distanceToSqr(position.x,position.y,position.z)<=64*64)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,packet);
    }

    /** Called only by the client payload handler; sequence rejects delayed packets. */
    public void acceptSpeedSample(long sequence,double speed) {
        speedometer.accept(sequence,speed);
    }

    public void activate(CabGeometry.Control control, Player player) {
        if (level == null || level.isClientSide) return;
        if (control.number().equals("B140")){
            if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                com.yasui_i.emucab.network.CabMonPayload.open(serverPlayer,this);
            return;
        }
        if (control.action()==CabAction.UNASSIGNED) return;
        speedKmh=readSpeedKmh(level);
        if(control.action()==CabAction.PANTOGRAPH&&!controls.on(CabAction.PANTOGRAPH)&&selectedPantographCar()==0){
            player.displayClientMessage(Component.translatable("message.emucab.pantographs_isolated"),true);return;
        }
        String error=controls.press(control.id(),control.action(),control.argument(),player.isShiftKeyDown(),speedKmh>.5);
        if (error != null) {
            player.displayClientMessage(Component.translatable("message.emucab."+error),true);
        } else {
            if(control.action()==CabAction.DIRECTION){
                controls.switches.put(CabAction.PANTOGRAPH,0);controls.switches.put(CabAction.BREAKER,0);
            }
            if(control.action()==CabAction.MASTER){
                if(controls.on(CabAction.MASTER)) {
                    controls.direction=0;controls.notch=0;
                    controls.switches.put(CabAction.PANTOGRAPH,0);controls.switches.put(CabAction.BREAKER,0);
                    com.yasui_i.emucab.control.CabEndpointRegistry.activate(this);
                } else { com.yasui_i.emucab.control.CabEndpointRegistry.release(this);controls.releaseForTakeover(); }
            }
            animateControl(control);
            if (control.action()==CabAction.HORN) level.playSound(null,worldPosition,
                    SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(),SoundSource.BLOCKS,1F,.7F);
            player.displayClientMessage(Component.translatable("message.emucab.control",
                    Component.translatable("control.emucab."+control.action().channel()),
                    controlStatus(control)),true);
        }
        level.playSound(null,worldPosition,SoundEvents.LEVER_CLICK,SoundSource.BLOCKS,.35F,error==null?1.25F:.6F);
        boolean lit=controls.on(CabAction.CAB_LIGHT);
        if (getBlockState().getValue(CabDeskBlock.LIT)!=lit) {
            level.setBlock(worldPosition,getBlockState().setValue(CabDeskBlock.LIT,lit),Block.UPDATE_ALL);
        }
        publishSignals(); sync();
    }

    private void animateControl(CabGeometry.Control control) {
        if(!(level instanceof net.minecraft.server.level.ServerLevel server) || control.action()==CabAction.HANDLE)return;
        animationSequence=Math.max(animationSequence+1,level.getGameTime()*1024);
        var packet=new com.yasui_i.emucab.network.CabControlAnimationPayload(worldPosition,control.id(),animationSequence);
        Vec3 world=com.yasui_i.emucab.network.CabAtpSoundPayload.worldPosition(level,worldPosition);
        // Storage chunks can be remote from a Sable structure's rendered location.
        for(var player:server.players())if(player.distanceToSqr(world.x,world.y,world.z)<=64*64)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,packet);
    }

    public Component controlStatus(CabGeometry.Control c) {
        if (c.action()==CabAction.PARKING_APPLY || c.action()==CabAction.PARKING_RELEASE)
            return Component.translatable("state.emucab."+(controls.parkingBrake?"applied":"released"));
        if (c.action()==CabAction.DOOR_OPEN_LEFT || c.action()==CabAction.DOOR_CLOSE_LEFT)
            return Component.translatable("state.emucab."+(controls.on(CabAction.DOOR_LEFT)?"open_command":"closed_command"));
        if (c.action()==CabAction.DOOR_OPEN_RIGHT || c.action()==CabAction.DOOR_CLOSE_RIGHT)
            return Component.translatable("state.emucab."+(controls.on(CabAction.DOOR_RIGHT)?"open_command":"closed_command"));
        if (c.action()==CabAction.OPERATING_MODE) return Component.translatable("state.emucab.request_only");
        if (c.action().momentary()) return Component.translatable("state.emucab.momentary");
        return switch (c.action()) {
            case UNASSIGNED -> Component.translatable("state.emucab.unassigned");
            case HANDLE -> Component.literal(handleLabel());
            case SCREEN -> Component.translatable("state.emucab.pulse");
            case RADIO -> Component.literal("CH "+controls.radioChannel+" "+controls.radioInput);
            case DIRECTION -> Component.translatable("state.emucab."+(controls.direction==1?"forward":controls.direction==-1?"reverse":"neutral"));
            case EMERGENCY, RESET -> Component.literal(controls.emergency?"EB":"N");
            case WIPER, HEADLIGHT -> Component.literal(Integer.toString(controls.value(c.action())));
            case ACK, HORN, SAND, BRAKE_TEST, DOOR_CLOSE -> Component.translatable("state.emucab.pulse");
            default -> Component.translatable("state.emucab."+(controls.on(c.action())?"on":"off"));
        };
    }

    public void dragHandle(int target,Player player) {
        if(level==null || level.isClientSide || target==leverPosition())return;
        speedKmh=readSpeedKmh(level);
        String error=controls.setHandle(target,speedKmh>.5);
        if(error!=null)player.displayClientMessage(Component.translatable("message.emucab."+error),true);
        else level.playSound(null,worldPosition,SoundEvents.LEVER_CLICK,SoundSource.BLOCKS,.18F,1.1F);
        publishSignals();sync();
    }

    @Override public Map<String,Integer> getCabSignals() {
        var signals=new java.util.HashMap<>(controls.signals());int car=commandedPantographCar();
        signals.put("pantograph_03",car==3?15:0);signals.put("pantograph_06",car==6?15:0);
        return Map.copyOf(signals);
    }
    private void publishSignals() {
        Map<String,Integer> signals=getCabSignals();
        com.yasui_i.emucab.compat.CabWireIntegration.tick(this,signals);
        if (!signals.equals(previousSignals)) {
            previousSignals=signals;
            NeoForge.EVENT_BUS.post(new CabSignalChangedEvent(this,signals));
        }
    }

    @Override public void setRemoved() {
        com.yasui_i.emucab.control.CabEndpointRegistry.unregister(this);
        com.yasui_i.emucab.control.CeeElectricalTelemetry.unregister(this);
        com.yasui_i.emucab.compat.CabWireIntegration.unload(this);
        super.setRemoved();
    }
    @Override public void onChunkUnloaded() {
        com.yasui_i.emucab.control.CabEndpointRegistry.unregister(this);
        com.yasui_i.emucab.control.CeeElectricalTelemetry.unregister(this);
        com.yasui_i.emucab.compat.CabWireIntegration.unload(this);
        super.onChunkUnloaded();
    }

    public void reportStatus(Player player) {
        player.displayClientMessage(Component.translatable("message.emucab.status",
                String.format("%.0f",speedKmh),String.format("%.0f",getLimitKmh()),handleLabel()),true);
    }
    public String handleLabel() {
        return controls.emergency?"EB":controls.notch>0?"P"+controls.notch:controls.notch<0?"B"+(-controls.notch):"N";
    }
    public int leverPosition() { return controls.emergency?-8:controls.notch; }
    public double getSpeedKmh() { return speedKmh; }
    public double getShownSpeedKmh() { return shownSpeedKmh; }
    public double getShownSpeedKmh(float partialTick) { return speedometer.value(partialTick); }
    /** No station provider is connected yet. Future adapters may supply real route data. */
    public Component getNextStationName() { return Component.literal("—"); }
    public double forceScaleKn(){return forceScaleKn;}
    @Override public void onLoad(){super.onLoad();com.yasui_i.emucab.telemetry.SimurailBrakeBindings.update(this);com.yasui_i.emucab.control.CabEndpointRegistry.register(this);com.yasui_i.emucab.control.CeeElectricalTelemetry.register(this);}
    public int clockMode(){return clockMode;}
    public int dateMode(){return dateMode;}
    public String customDate(){return customDate;}
    public String locomotiveNumber(){return locomotiveNumber;}
    public CabPantographs pantographs(){return pantographs;}
    private int selectedPantographCar(){return pantographs.selectedCar(endpoint,controls.direction);}
    public int commandedPantographCar(){return controls.on(CabAction.MASTER)&&controls.on(CabAction.PANTOGRAPH)?selectedPantographCar():0;}
    public boolean frontPantographUp(){return com.yasui_i.emucab.control.CabEndpointRegistry.configured(this)?commandedPantographCar()==3:pantographs.up(1,controls.direction,controls.on(CabAction.PANTOGRAPH));}
    public boolean rearPantographUp(){return com.yasui_i.emucab.control.CabEndpointRegistry.configured(this)?commandedPantographCar()==6:pantographs.up(-1,controls.direction,controls.on(CabAction.PANTOGRAPH));}
    public void configurePantographs(boolean cutFront,boolean cutRear){
        if(level==null||level.isClientSide)return;
        com.yasui_i.emucab.control.CabEndpointRegistry.configureIsolation(this,cutFront,cutRear);
    }
    public void applySharedPantographIsolation(boolean cutFront,boolean cutRear){
        if(pantographs.cutFront==cutFront&&pantographs.cutRear==cutRear)return;
        if(controls.on(CabAction.PANTOGRAPH)){
            controls.switches.put(CabAction.PANTOGRAPH,0);controls.switches.put(CabAction.BREAKER,0);
            if(controls.notch>0)controls.notch=0;
        }
        pantographs.cutFront=cutFront;pantographs.cutRear=cutRear;
        publishSignals();sync();
    }
    public void setDisplaySettings(int clock,int date,String custom,String locomotive){
        setDisplaySettings(clock,date,custom,locomotive,forceScaleKn);
    }
    public void setDisplaySettings(int clock,int date,String custom,String locomotive,double scaleKn){
        if(!CabDisplayFormat.validForceScale(scaleKn))return;
        if(clock<0||clock>1||date<0||date>1||date==1&&!CabDisplayFormat.validDate(custom))return;
        clockMode=clock;dateMode=date;
        customDate=CabDisplayFormat.validDate(custom)?custom:"";
        locomotiveNumber=CabDisplayFormat.locomotive(locomotive);forceScaleKn=scaleKn;
        sync();
    }
    public String displayTime(){
        if(clockMode==0){
            return CabDisplayFormat.gameTime(level==null?0:level.getDayTime());
        }
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
    public String displayDate(){
        if(dateMode==1&&customDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))return customDate;
        return LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
    public double getLimitKmh() { return trackLimitKmh>0?trackLimitKmh:limitKmh<=0?CabConfig.DEFAULT_LIMIT.get():limitKmh; }
    public double getNextTrackLimitKmh() { return nextTrackLimitKmh; }
    public double getNextTrackDistance() { return nextTrackDistance; }
    public com.yasui_i.emucab.track.CabSpeedPlan getSpeedPlan() { return speedPlan; }
    public boolean isEmergency() { return controls.emergency; }
    private void sync() {
        setChanged();
        // Only the BER/HUD state changes. Dragging must not rebake the static OBJ.
        level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag=new CompoundTag(); saveAdditional(tag,registries); return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        tag.putInt("notch",controls.notch); tag.putBoolean("emergency",controls.emergency); tag.putDouble("limit",limitKmh);
        tag.putDouble("trackLimit",trackLimitKmh);tag.putDouble("nextTrackLimit",nextTrackLimitKmh);
        tag.putDouble("nextTrackDistance",nextTrackDistance);
        tag.put("speedPlan",speedPlan.save());
        trackLimits.save(tag);
        tag.putBoolean("parkingBrake",controls.parkingBrake);
        tag.putInt("controlMappingVersion",1);
        tag.putInt("direction",controls.direction); tag.putInt("radioChannel",controls.radioChannel);
        tag.putString("radioInput",controls.radioInput); tag.putIntArray("pages",controls.pages);
        CompoundTag switches=new CompoundTag();
        controls.switches.forEach((a,v)->switches.putInt(a.channel(),v)); tag.put("switches",switches);
        tag.putString("feedback",controls.lastControl());
        tag.putDouble("displayForceScaleKn",forceScaleKn);
        tag.putInt("displayClockMode",clockMode);tag.putInt("displayDateMode",dateMode);
        tag.putBoolean("pantographCutFront",pantographs.cutFront);tag.putBoolean("pantographCutRear",pantographs.cutRear);
        tag.putString("displayCustomDate",customDate);tag.putString("locomotiveNumber",locomotiveNumber);
        tag.putString("ConsistId",consistId);tag.putInt("CabEndpoint",endpoint);
        tag.putUUID("CabLinkId",linkId);
        tag.putDouble("LineVoltage",lineVoltage);tag.putDouble("LineCurrent",lineCurrent);
        tag.putUUID("TelemetryCabId",telemetryId);telemetry.save(tag);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);
        controls.notch=Math.clamp(tag.getInt("notch"),-7,7); controls.emergency=tag.getBoolean("emergency");
        trackLimitKmh=tag.contains("trackLimit")?tag.getDouble("trackLimit"):-1;
        if(!Double.isFinite(trackLimitKmh) || trackLimitKmh<=0 || trackLimitKmh>1000)trackLimitKmh=-1;
        nextTrackLimitKmh=tag.contains("nextTrackLimit")?tag.getDouble("nextTrackLimit"):-1;
        if(!Double.isFinite(nextTrackLimitKmh) || nextTrackLimitKmh<=0 || nextTrackLimitKmh>1000)nextTrackLimitKmh=-1;
        nextTrackDistance=tag.contains("nextTrackDistance")?tag.getDouble("nextTrackDistance"):Double.POSITIVE_INFINITY;
        speedPlan=tag.contains("speedPlan")?com.yasui_i.emucab.track.CabSpeedPlan.load(tag.getCompound("speedPlan"))
                :com.yasui_i.emucab.track.CabSpeedPlan.EMPTY;
        trackLimits.load(tag);
        controls.parkingBrake=tag.getBoolean("parkingBrake");
        limitKmh=tag.contains("limit")?tag.getDouble("limit"):-1;
        controls.direction=tag.contains("direction")?Math.clamp(tag.getInt("direction"),-1,1):0;
        controls.radioChannel=tag.contains("radioChannel")?Math.clamp(tag.getInt("radioChannel"),1,999):1;
        String input=tag.getString("radioInput"); controls.radioInput=input.matches("[0-9]{0,3}")?input:"";
        int[] pages=tag.getIntArray("pages");
        for(int i=0;i<5;i++) controls.pages[i]=i<pages.length?Math.floorMod(pages[i],3):0;
        CompoundTag switches=tag.getCompound("switches");
        controls.switches.clear();
        controls.switches.put(CabAction.POWER,1);controls.switches.put(CabAction.CAB_ACTIVE,1);
        for(CabAction a:CabAction.values()) if(switches.contains(a.channel())) controls.switches.put(a,Math.clamp(switches.getInt(a.channel()),0,2));
        // Old object-name guesses could leave inaccessible power/door/light states.
        // Migrate once when adopting the user's numbered assignments.
        if (tag.getInt("controlMappingVersion")<1) {
            for (CabAction a:new CabAction[]{CabAction.MASTER,CabAction.POWER,CabAction.CAB_ACTIVE}) controls.switches.put(a,1);
            for (CabAction a:new CabAction[]{CabAction.DOOR_LEFT,CabAction.DOOR_RIGHT,CabAction.CAB_LIGHT}) controls.switches.put(a,0);
        }
        controls.feedback(tag.getString("feedback"));
        forceScaleKn=tag.contains("displayForceScaleKn")&&CabDisplayFormat.validForceScale(tag.getDouble("displayForceScaleKn"))?tag.getDouble("displayForceScaleKn"):CabDisplayFormat.DEFAULT_FORCE_SCALE_KN;
        clockMode=Math.clamp(tag.getInt("displayClockMode"),0,1);dateMode=Math.clamp(tag.getInt("displayDateMode"),0,1);
        pantographs.cutFront=tag.getBoolean("pantographCutFront");pantographs.cutRear=tag.getBoolean("pantographCutRear");
        customDate=tag.getString("displayCustomDate");if(!CabDisplayFormat.validDate(customDate))customDate="";
        locomotiveNumber=CabDisplayFormat.locomotive(tag.getString("locomotiveNumber"));
        consistId=CabDisplayFormat.locomotive(tag.getString("ConsistId")); endpoint=Math.clamp(tag.getInt("CabEndpoint"),0,8);
        if(endpoint!=1&&endpoint!=8)endpoint=0;
        if(tag.hasUUID("CabLinkId"))linkId=tag.getUUID("CabLinkId");
        lineVoltage=0;lineCurrent=0;
        if(tag.hasUUID("TelemetryCabId"))telemetryId=tag.getUUID("TelemetryCabId");telemetry.load(tag);
    }

    @Override public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (!CabConfig.TRACTION_ENABLED.get()) return;
        int lever=leverPosition();
        if (lever==0 || (lever>0 && !controls.ready())) return;
        Direction forward=getBlockState().getValue(CabDeskBlock.FACING).getOpposite();
        Vector3d impulse=new Vector3d();
        if(lever>0) impulse.set(forward.getStepX(),0,forward.getStepZ()).mul(controls.direction*lever*CabConfig.TRACTION_FORCE_PER_NOTCH.get()*timeStep);
        else {
            Vector3d velocity=handle.getLinearVelocity(new Vector3d()); velocity.y=0;
            if(velocity.lengthSquared()<1E-4) return;
            double force=controls.emergency?CabConfig.EMERGENCY_FORCE.get():Math.abs(lever)*CabConfig.BRAKE_FORCE_PER_NOTCH.get();
            velocity.normalize().mul(-force*timeStep,impulse);
        }
        handle.applyLinearImpulse(impulse);
    }
    public boolean isCenter() { return getBlockState().getValue(CabDeskBlock.PART)==CabDeskPart.CENTER; }
}
