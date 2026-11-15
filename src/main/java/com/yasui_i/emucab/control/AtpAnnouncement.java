package com.yasui_i.emucab.control;

/** Durations include a short gap after each user-supplied recording, at 20 TPS. */
public enum AtpAnnouncement {
    OVERSPEED("超速报警",158,100),
    LEVEL_TRANSITION_COMPLETE("等级转换成功",55,30),
    CAB_SIGNAL_ACK("机信确认",52,50),
    LEVEL_TRANSITION("级间切换",97,50),
    REDUCE_SPEED("减速",138,80),
    CONTROL_SPEED("控制速度",84,60),
    ON_SIGHT_ACK("目视确认",59,50),
    NEUTRAL_SECTION_AHEAD("前方过分相",114,40),
    NOTICE("提示",29,10),
    SHUNTING_ACK("引导确认",49,50),
    PLATFORM_RIGHT("右侧站台",111,20),
    RELEASE_PERMITTED("允许缓解",50,60),
    BRAKING_COMPLETE("制动完成",41,60),
    STOP_AHEAD("注意停车",94,90),
    PLATFORM_LEFT("左侧站台",125,20);

    public final String sourceName;
    public final int ticks,priority;
    AtpAnnouncement(String sourceName,int ticks,int priority) {
        this.sourceName=sourceName;this.ticks=ticks;this.priority=priority;
    }
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
}
