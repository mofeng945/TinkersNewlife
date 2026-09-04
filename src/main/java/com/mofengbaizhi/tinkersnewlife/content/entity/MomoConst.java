package com.mofengbaizhi.tinkersnewlife.content.entity;

/** 墨默全部数值/状态常量（自 MomoMerchant 拆出，集中配置） */
public interface MomoConst {

    int S_IDLE = 0;
    int S_ENGAGE = 1;
    double REACH = 3.0;
    int SWEEP_WAIT_TICKS = 10;
    int BACKOFF_TICKS = 26;
    int COMBO_COOLDOWN = 30;
    int LEAP_UP_TICKS = 10;
    int ULT_INTERVAL = 12;
    float[] ULT_MULTIPLIERS = {0.8f, 0.8f, 1.2f, 0.9f, 2.0f};
    float[] COUNTER_MULTIPLIERS = {0.6f, 0.8f, 1.0f};
    double WANDER_RADIUS = 20.0;
    int HUNT_INTERVAL = 500;
    double HUNT_CHANCE = 0.1;
    double HUNT_RADIUS = 10.0;
    float IDLE_MOVE_SPEED = 0.9F;
    int ASTAR_MAX_EXPAND = 700;
    long HIRE_DURATION_TICKS = 24000;
    int SONG_BACKOFF_TICKS = 20;
    int SONG_DURATION_TICKS = 200;
    int SONG_COOLDOWN_TICKS = 2400;
    double EMPLOYER_LOW_HP_RATIO = 0.6;
    int SONG_REGEN_AMPLIFIER = 2;
    int EAT_DURATION_TICKS = 40;
    int EAT_CHECK_INTERVAL = 100;
    double EAT_CHANCE = 0.3;
    int RETURN_GRACE_TICKS = 400;
    float HIRED_BASE_ATTACK = 35.0F;
    float EXECUTE_HP = 10.0F;
    double EMPLOYER_TELEPORT_DIST = 50.0;
    int DMG_WINDOW_TICKS = 100;
    double CHANT_DAMAGE_THRESHOLD = 0.5;
    double MASS_EXECUTE_RADIUS = 50.0;
    double MASS_EXECUTE_HP = 0.2;
    int MASS_EXECUTE_COOLDOWN = 600;
    int STUCK_WINDOW_TICKS = 120;
    double STUCK_MAX_MOVE = 1.0;
    int STUCK_ESCAPE_COOLDOWN = 300;
    double AIR_RADIUS = 20.0;
    double AIR_GAP = 2.2;
    int AIR_COMBO_COOLDOWN = 400;
    float[] AIR_HIT_MULTIPLIERS = {0.8f, 0.8f, 1.0f, 1.2f, 1.6f};
    float PIERCE_CHUNK = 19.0F;
}
