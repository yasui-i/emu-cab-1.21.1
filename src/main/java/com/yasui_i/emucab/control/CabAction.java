package com.yasui_i.emucab.control;

/** Stable action names; independent of model object order and future wire adapters. */
public enum CabAction {
    HANDLE, SCREEN, RADIO, EMERGENCY, ACK, RESET, DIRECTION, MASTER,
    POWER, CAB_ACTIVE, PANTOGRAPH, BREAKER, AUXILIARY, BRAKE_TEST,
    CAB_LIGHT, SAND, DOOR_LEFT, DOOR_RIGHT, DOOR_CLOSE, WIPER, HEADLIGHT, HORN, UNASSIGNED,
    MANUAL_NEUTRAL, SUNSHADE, PARKING_APPLY, PARKING_RELEASE, CLEANING_BRAKE,
    HOLDING_BRAKE, DSD, GENERAL_RESET, EMERGENCY_RESET, PASSENGER_ALARM_BYPASS,
    DOOR_RELEASE_LEFT, DOOR_OPEN_LEFT, DOOR_CLOSE_LEFT,
    DOOR_RELEASE_RIGHT, DOOR_OPEN_RIGHT, DOOR_CLOSE_RIGHT, OPERATING_MODE;

    public boolean momentary() {
        return switch (this) {
            case SCREEN, RADIO, ACK, SAND, HORN, BRAKE_TEST, DOOR_CLOSE,
                    MANUAL_NEUTRAL, CLEANING_BRAKE, DSD, GENERAL_RESET, EMERGENCY_RESET,
                    PARKING_APPLY, PARKING_RELEASE, DOOR_CLOSE_LEFT,
                    DOOR_CLOSE_RIGHT, OPERATING_MODE -> true;
            default -> false;
        };
    }

    public String channel() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
