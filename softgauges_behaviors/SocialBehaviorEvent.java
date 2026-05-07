package org.softgauges_behaviors;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class SocialBehaviorEvent extends BehaviorEvent {
    private final UUID targetPlayerId;
    private final String targetPlayerName;
    private final ItemStack itemInvolved;

    public SocialBehaviorEvent(BehaviorType type, Location loc,
                               UUID actorId, String actorName,
                               UUID targetPlayerId, String targetPlayerName,
                               ItemStack itemInvolved) {
        super(BehaviorCategory.SOCIAL, type, loc, actorId, actorName);
        this.targetPlayerId = targetPlayerId;
        this.targetPlayerName = targetPlayerName;
        this.itemInvolved = itemInvolved;
    }

    @Override
    public String getDescription() {
        String item = itemInvolved != null
                ? itemInvolved.getAmount() + "x " + itemInvolved.getType().name().replace('_', ' ').toLowerCase()
                : "nothing";
        return switch (getType()) {
            case GAVE_ITEM    -> getActorName() + " gave " + item + " to " + targetPlayerName;
            case HELPED_PLAYER -> getActorName() + " helped " + targetPlayerName + " (gave " + item + ")";
            case SAVED_PLAYER -> getActorName() + " saved " + targetPlayerName + " from danger";
            case IGNORED_HELP_REQUEST -> getActorName() + " ignored a help request from " + targetPlayerName;
            default           -> getActorName() + " [" + getType() + "] -> " + targetPlayerName;
        };
    }

    public UUID getTargetPlayerId()    { return targetPlayerId; }
    public String getTargetPlayerName() { return targetPlayerName; }
    public ItemStack getItemInvolved()  { return itemInvolved; }
}
