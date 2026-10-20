package pt.realmeconomy.domain;
public enum ServiceKind {
    VILLAGER(500), ANVIL_REPAIR(100), ANVIL_COMBINE(150), ANVIL_RENAME(100), ENCHANT(100), SMITHING(1000), BREWING(500), BEACON(5000);
    public final long defaultCents;
    ServiceKind(long cents){defaultCents=cents;}
}
