package com.yasui_i.emucab.control;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class CabDisplayFormat {
    public static final double DEFAULT_FORCE_SCALE_KN=5;
    /** Brake column display range; the physical Simurail force is never clipped. */
    public static final double DEFAULT_BRAKE_SCALE_KN=2;
    public static boolean validForceScale(double value){return Double.isFinite(value)&&value>=.1&&value<=1000;}
    public static double forceFraction(double newtons,double scaleKn){
        return Double.isFinite(newtons)&&validForceScale(scaleKn)?Math.clamp(newtons/(scaleKn*1000),0,1):0;
    }
    public static String scaleLabel(double scaleKn){return String.format(Locale.ROOT,scaleKn==Math.rint(scaleKn)?"%.0f":"%.1f",scaleKn);}
    /** Minecraft tick zero is 06:00; one in-game day takes 24,000 ticks. */
    public static String gameTime(long dayTime){
        long seconds=(Math.floorMod(dayTime,24000)*86400/24000+21600)%86400;
        return String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60);
    }
    public static boolean validDate(String date){
        if(date==null||!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))return false;
        try{LocalDate.parse(date);return true;}catch(DateTimeParseException e){return false;}
    }
    public static String locomotive(String value){
        if(value==null)return "";
        var clean=value.replaceAll("[\\p{Cntrl}§]","").strip();
        return clean.substring(0,Math.min(24,clean.length()));
    }
    private CabDisplayFormat(){}
}
