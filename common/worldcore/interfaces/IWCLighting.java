package worldcore.interfaces;

import net.minecraft.client.multiplayer.WorldClient;

public interface IWCLighting
{
    public boolean isLightingDisabled();
    
    public Float[] getLightingMultipliers(WorldClient worldclient);
}
