package com.yasui_i.emucab.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.CabAction;
import com.yasui_i.emucab.control.CabVehicleDisplay;
import com.yasui_i.emucab.control.CabElectricalDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;

/** Shared live clock/speed headers, radio identity and central ATP planning display. */
final class CabDisplayRenderer {
    static final int WIDTH=256,HEIGHT=176;
    // World-space distances along the LCD normal. Keep masks, fills, lines and
    // text on separate planes; same-depth solid quads caused blue/grey striping.
    private static final float MASK_Z=.0005F,FILL_Z=.001F,LINE_Z=.0015F,TEXT_Z=.002F;
    private static final RenderType[] FACES=new RenderType[5];
    private static final RenderType WHITE=RenderType.entitySolid(EMUCab.id("textures/block/cab_white.png"));
    static {for(int i=0;i<5;i++)FACES[i]=RenderType.entitySolid(EMUCab.id("textures/display/screen_"+i+".png"));}

    static void render(int index,CabDeskBlockEntity desk,PoseStack pose,MultiBufferSource buffers,float partial) {
        var out=buffers.getBuffer(FACES[index]);
        vertex(out,pose,0,0,0,0,0,0xFFFFFFFF);
        vertex(out,pose,0,HEIGHT,0,0,1,0xFFFFFFFF);
        vertex(out,pose,WIDTH,HEIGHT,0,1,1,0xFFFFFFFF);
        vertex(out,pose,WIDTH,0,0,1,0,0xFFFFFFFF);
        if(index==0){
            fittedText(pose,buffers,desk.locomotiveNumber().isBlank()?"—":desk.locomotiveNumber(),224,5,49,.6F,0xFFDCE2DC);
        }
        if(index==1||index==3){
            fittedText(pose,buffers,desk.displayDate(),32,4,58,.6F,0xFFDCE2DC);
            fittedText(pose,buffers,desk.displayTime(),88,4,42,.6F,0xFF82DF77);
            fittedText(pose,buffers,Long.toString(Math.round(desk.getShownSpeedKmh(partial))),124,3.5F,24,.75F,0xFFDCE2DC);
            if(index==3&&desk.isEmergency()){
                out=buffers.getBuffer(WHITE);quad(out,pose,213,2,231,13,0xFFBD272B);
                text(pose,buffers,"EB",222,4,.7F,0xFFFFFFFF);
            }
            renderPantographs(desk,pose,buffers);

        }
        if(index==3) renderVehicleState(desk,pose,buffers);
        if(index==1) renderElectrical(desk,pose,buffers,partial);
        if(index==1) renderForceBars(desk,pose,buffers);
        if(index!=2)return;
        double speed=desk.getShownSpeedKmh(partial),limit=desk.getLimitKmh();
        double a=Math.toRadians(135+Math.clamp(speed/450,0,1)*270);
        float dx=(float)Math.cos(a),dy=(float)Math.sin(a);
        boolean overspeed=speed>limit+2;
        out=buffers.getBuffer(WHITE);
        // Start at the hub's edge so its speed readout remains unobstructed.
        line(out,pose,68+dx*10,71+dy*10,68+dx*47,71+dy*47,1.5F,overspeed?0xFFFF6358:0xFFDCE2DC);
        double limAngle=Math.toRadians(135+Math.clamp(limit/450,0,1)*270);
        line(out,pose,68+(float)Math.cos(limAngle)*51,71+(float)Math.sin(limAngle)*51,
                68+(float)Math.cos(limAngle)*57,71+(float)Math.sin(limAngle)*57,2,0xFFEEE3AC);
        var plan=desk.getSpeedPlan();
        if(plan.available()) {
            // 76 narrow trapezoids: bounded work, no texture uploads or per-frame collections.
            for(int i=0;i<76;i++) {
                float x0=141+i,x1=x0+1;
                float y0=91-(float)Math.clamp(plan.speedAt(limit,plan.range()*i/76)/450,0,1)*61;
                float y1=91-(float)Math.clamp(plan.speedAt(limit,plan.range()*(i+1)/76)/450,0,1)*61;
                vertex(out,pose,x0,y0,FILL_Z,.5F,.5F,0xFF164EB5);
                vertex(out,pose,x0,91,FILL_Z,.5F,.5F,0xFF164EB5);
                vertex(out,pose,x1,91,FILL_Z,.5F,.5F,0xFF164EB5);
                vertex(out,pose,x1,y1,FILL_Z,.5F,.5F,0xFF164EB5);
                line(out,pose,x0,y0,x1,y1,.65F,0xFF98C5FF);
            }
            for(var target:plan.targets()) {
                float x=141+(float)(target.distance()/plan.range())*76;
                float y=91-(float)Math.clamp(target.limit()/450,0,1)*61;
                line(out,pose,x,y-2,x,y+2,.8F,0xFFEEE3AC);
            }
        } else text(pose,buffers,"无线路数据",179,58,.5F,0xFF859391);
        text(pose,buffers,"0",142,93,.35F,0xFF859391);
        text(pose,buffers,Math.round(plan.range())+" m",203,93,.35F,0xFF859391);
        text(pose,buffers,Long.toString(Math.round(speed)),68,67,.85F,overspeed?0xFFAD2018:0xFF182321);
        text(pose,buffers,Long.toString(Math.round(limit)),185,109,1.25F,0xFFEEE3AC);
        text(pose,buffers,desk.getNextStationName(),69,138,.65F,0xFFDCE2DC);
        var controls=desk.controls();
        String state=desk.isEmergency()?"eb":controls.ready()?"ready":"interlock";
        text(pose,buffers,Component.translatable("display.emucab."+state),73,151,.6F,
                desk.isEmergency()?0xFFFF6358:controls.ready()?0xFF8BDA85:0xFFEEE3AC);
        String notice=overspeed?"overspeed":speed>=Math.max(0,limit-5)?"near_limit":"no_notice";
        text(pose,buffers,Component.translatable("display.emucab."+notice),73,163,.55F,
                overspeed?0xFFFF6358:0xFF859391);
        text(pose,buffers,desk.handleLabel(),194,140,.65F,0xFFEEE3AC);
        text(pose,buffers,Component.translatable("state.emucab."+
                (controls.direction>0?"forward":controls.direction<0?"reverse":"neutral")),194,152,.6F,0xFFDCE2DC);
        double distance=desk.getNextTrackDistance();
        text(pose,buffers,desk.getNextTrackLimitKmh()>0 && Double.isFinite(distance) && distance>=0
                ?Math.round(distance)+" m":"—",194,164,.55F,0xFF859391);
    }
    private static void vertex(VertexConsumer out,PoseStack pose,float x,float y,float z,float u,float v,int color) {
        out.addVertex(pose.last(),x,y,z).setColor(color).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);
    }
    private static void quad(VertexConsumer out,PoseStack pose,float x0,float y0,float x1,float y1,int color){
        quadAt(out,pose,x0,y0,x1,y1,FILL_Z,color);
    }
    private static void mask(VertexConsumer out,PoseStack pose,float x0,float y0,float x1,float y1,int color){
        quadAt(out,pose,x0,y0,x1,y1,MASK_Z,color);
    }
    private static void quadAt(VertexConsumer out,PoseStack pose,float x0,float y0,float x1,float y1,float z,int color){
        if(x1<=x0||y1<=y0)return;
        vertex(out,pose,x0,y0,z,.5F,.5F,color);vertex(out,pose,x0,y1,z,.5F,.5F,color);
        vertex(out,pose,x1,y1,z,.5F,.5F,color);vertex(out,pose,x1,y0,z,.5F,.5F,color);
    }
    private static void fittedText(PoseStack pose,MultiBufferSource buffers,String value,float x,float y,float width,float scale,int color){
        int pixels=Minecraft.getInstance().font.width(value);
        text(pose,buffers,value,x,y,pixels>0?Math.min(scale,width/pixels):scale,color);
    }
    private static void renderForceBars(CabDeskBlockEntity desk,PoseStack pose,MultiBufferSource buffers){
        var out=buffers.getBuffer(WHITE);int[] cars={2,4,5,7};int[] xs={80,112,144,176};
        for(int i=0;i<4;i++){
            var c=desk.telemetry().car(cars[i]);float x=xs[i];
            mask(out,pose,x+.5F,88.5F,x+9.5F,153.5F,0xFF252B33);
            if(c.complete()){
                float traction=(float)com.yasui_i.emucab.control.CabDisplayFormat.forceFraction(desk.telemetry().shownTraction(cars[i]),desk.forceScaleKn())*31;
                float brake=(float)com.yasui_i.emucab.control.CabDisplayFormat.forceFraction(desk.telemetry().shownBrake(cars[i]),com.yasui_i.emucab.control.CabDisplayFormat.DEFAULT_BRAKE_SCALE_KN)*31;
                if(traction>0)quad(out,pose,x+1,121-traction,x+9,121,0xFF244FE0);
                if(brake>0)quad(out,pose,x+1,121,x+9,121+brake,0xFFE5C84C);
            }
            line(out,pose,x,121,x+10,121,.5F,0xFF9CA7AF);
        }
        String scale=com.yasui_i.emucab.control.CabDisplayFormat.scaleLabel(desk.forceScaleKn());
        String brakeScale=com.yasui_i.emucab.control.CabDisplayFormat.scaleLabel(com.yasui_i.emucab.control.CabDisplayFormat.DEFAULT_BRAKE_SCALE_KN);
        for(int i=0;i<4;i++){
            text(pose,buffers,scale,xs[i]+16,86,.32F,0xFF9CA7AF);
            text(pose,buffers,"0",xs[i]+16,119,.32F,0xFF9CA7AF);
            text(pose,buffers,brakeScale,xs[i]+16,152,.32F,0xFF9CA7AF);
            var c=desk.telemetry().car(cars[i]);
            fittedText(pose,buffers,c.complete()?String.format(java.util.Locale.ROOT,"%.1f/%.1f",c.traction()/1000,c.brake()/1000):"—",xs[i]+5,82,25,.4F,0xFFDCE2DC);
        }
    }
    private static void renderPantographs(CabDeskBlockEntity desk,PoseStack pose,MultiBufferSource buffers){
        var out=buffers.getBuffer(WHITE);
        for(int bow=0;bow<2;bow++){
            float x=bow==0?99:158;
            var feedback=com.yasui_i.emucab.control.CeeElectricalTelemetry.reading(desk,bow==0?3:6);
            boolean up=desk.consistId().isBlank()?(bow==0?desk.frontPantographUp():desk.rearPantographUp()):feedback.online()&&feedback.raised();
            boolean cut=bow==0?desk.pantographs().cutFront:desk.pantographs().cutRear;
            int color=cut?0xFFE5C84C:up?(feedback.contact()?0xFF82DF77:0xFFE5C84C):0xFF9CA7AF;
            mask(out,pose,x-6,36,x+6,50,0xFF30343D);
            if(up){line(out,pose,x,49,x-4,42,1,color);line(out,pose,x-4,42,x+3,34,1,color);line(out,pose,x-5,34,x+6,34,1,color);}
            else line(out,pose,x-5,49,x+6,49,1,color);
            if(cut){line(out,pose,x-5,35,x+5,48,1,color);line(out,pose,x+5,35,x-5,48,1,color);}
            // A registered receiver reports physical contact and the actual electrical breaker.
            mask(out,pose,x-6,55,x+6,66,0xFF30343D);
            boolean closed=feedback.online()&&feedback.contact()&&feedback.breaker();
            line(out,pose,x-4,61,x+4,closed?61:57,1,closed?0xFF82DF77:0xFF9CA7AF);
        }
    }
    private static void renderVehicleState(CabDeskBlockEntity desk,PoseStack pose,MultiBufferSource buffers) {
        var out=buffers.getBuffer(WHITE);var controls=desk.controls();
        double speed=desk.getShownSpeedKmh();boolean air=CabVehicleDisplay.airPrecontrol(controls,speed);
        out=buffers.getBuffer(WHITE);
        for(int car=1;car<=8;car++){
            float x=55+(car-1)*19;
            if(CabVehicleDisplay.motorCar(car))quad(out,pose,x+2.5F,87.5F,x+13.5F,100.5F,CabVehicleDisplay.traction(controls,car)?0xFFE5C84C:0xFF252B33);
            quad(out,pose,x+.5F,126.5F,x+15.5F,131.5F,air?0xFFE5C84C:0xFF30343D);
            int color=CabVehicleDisplay.electricBrake(controls,speed,car)?0xFFE5C84C:0xFF252B33;
            for(int j=0;j<16;j++){
                float dx=j-7.5F;float h=(float)Math.sqrt(Math.max(0,64-dx*dx));
                quad(out,pose,x+j+.2F,117-h,x+j+.9F,117,color);
            }
        }
        for(int unit=0;unit<4;unit++){
            var a=desk.telemetry().car(unit*2+1);var b=desk.telemetry().car(unit*2+2);
            boolean complete=a.complete()&&b.complete(),fault=a.fault()||b.fault();
            boolean applied=a.brake()+b.brake()>100;
            float x=new float[]{55,93,150,188}[unit];
            quad(out,pose,x+.5F,139.5F,x+14.5F,150.5F,fault?0xFFCF3E3E:applied?0xFFE5C84C:0xFF30343D);
            quad(out,pose,x+.5F,152.5F,x+14.5F,157.5F,fault?0xFFCF3E3E:complete?0xFF85BF61:0xFF59616A);
        }
        float demand=(float)CabVehicleDisplay.brakeDemand(controls);
        mask(out,pose,224.5F,91.5F,232.5F,157.5F,0xFF252B33);
        if(demand>0)quad(out,pose,224.5F,158-66*demand,232.5F,157.5F,0xFF244FE0);
        String notice="";
        for(int car=1;car<=8;car++){
            var c=desk.telemetry().car(car);
            if(c.fault()){notice="转向架停用 / 脱轨 / 登记冲突";break;}
            if(c.slip())notice="轮轨滑移提示";
            if(notice.isEmpty()&&c.bound()>0&&!c.complete())notice="制动反馈数据中断";
        }
        if(!notice.isEmpty())text(pose,buffers,notice,125,158,.4F,0xFFFF7770);

    }
    private static void renderElectrical(CabDeskBlockEntity desk,PoseStack pose,MultiBufferSource buffers,float partial) {
        // The values are sampled from CEE's completed circuit, so an unconnected
        // pantograph reports zero instead of a command-side estimate.
        double voltage=desk.electricalDisplay().voltage(partial);
        double current=desk.electricalDisplay().current(partial);
        var out=buffers.getBuffer(WHITE);
        // Interiors are empty in the artwork; only one live fill covers each pixel.
        // Gauge endpoints exactly match the artwork's y=48 and y=154 tick positions.
        float v=154-(float)CabElectricalDisplay.voltageFraction(voltage)*106;
        float a=154-(float)CabElectricalDisplay.currentFraction(current)*106;
        if(v<153.5F)quad(out,pose,28,Math.max(48.5F,v),37,153.5F,0xFF244FE0);
        if(a<153.5F)quad(out,pose,221,Math.max(48.5F,a),230,153.5F,0xFF244FE0);
        // The blank strip below each gauge ends at the footer (y=163).
        // Keep values clear of both the gauge/zero mark and the central car diagram.
        fittedText(pose,buffers,String.format(java.util.Locale.ROOT,"%.2f kV",voltage/1000),32,156,38,.42F,0xFFDCE2DC);
        fittedText(pose,buffers,String.format(java.util.Locale.ROOT,"%.1f A",current),226,156,38,.42F,0xFFDCE2DC);
    }
    private static void line(VertexConsumer out,PoseStack pose,float x0,float y0,float x1,float y1,float width,int color) {
        float dx=x1-x0,dy=y1-y0,len=(float)Math.sqrt(dx*dx+dy*dy),nx=-dy/len*width/2,ny=dx/len*width/2;
        vertex(out,pose,x0+nx,y0+ny,LINE_Z,.5F,.5F,color);
        vertex(out,pose,x1+nx,y1+ny,LINE_Z,.5F,.5F,color);
        vertex(out,pose,x1-nx,y1-ny,LINE_Z,.5F,.5F,color);
        vertex(out,pose,x0-nx,y0-ny,LINE_Z,.5F,.5F,color);
    }
    private static void text(PoseStack pose,MultiBufferSource buffers,String value,float x,float y,float scale,int color) {
        text(pose,buffers,Component.literal(value),x,y,scale,color);
    }
    private static void text(PoseStack pose,MultiBufferSource buffers,Component value,float x,float y,float scale,int color) {
        var font=Minecraft.getInstance().font;
        pose.pushPose();pose.translate(x,y,TEXT_Z);pose.scale(scale,scale,1);
        font.drawInBatch(value,-font.width(value)/2F,0,color,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
