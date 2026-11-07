package clre20.customInvAudit.util;

/** 權限節點。 */
public final class Perms {

    public static final String INV_VIEW = "custominvaudit.inventory.view";
    public static final String INV_MODIFY = "custominvaudit.inventory.modify";
    public static final String EC_SELF = "custominvaudit.enderchest.self";
    public static final String EC_OTHER = "custominvaudit.enderchest.other";
    public static final String EC_MODIFY = "custominvaudit.enderchest.modify";
    public static final String OFFLINE = "custominvaudit.offline";
    public static final String STATS_MODIFY = "custominvaudit.stats.modify";
    public static final String TELEPORT = "custominvaudit.teleport";
    public static final String ENTITY_VIEW = "custominvaudit.entity.view";
    public static final String ENTITY_MODIFY = "custominvaudit.entity.modify";
    public static final String EXEMPT = "custominvaudit.exempt";
    public static final String BYPASS_EXEMPT = "custominvaudit.bypass.exempt";
    public static final String VERSION = "custominvaudit.version";
    public static final String RELOAD = "custominvaudit.reload";

    private Perms() {
    }
}
