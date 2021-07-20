package nl.pim16aap2.bigDoors.NMS;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Base64;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_13_R2.CraftServer;
import org.bukkit.craftbukkit.v1_13_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import net.minecraft.server.v1_13_R2.DimensionManager;
import net.minecraft.server.v1_13_R2.EntityPlayer;
import net.minecraft.server.v1_13_R2.MinecraftServer;
import net.minecraft.server.v1_13_R2.PlayerInteractManager;
import nl.pim16aap2.bigDoors.skulls.HeadManager;
import nl.pim16aap2.bigDoors.skulls.Skull;
import org.bukkit.plugin.java.JavaPlugin;

public class SkullCreator_V1_13_R2 extends HeadManager
{
    public SkullCreator_V1_13_R2(JavaPlugin plugin)
    {
        super(plugin);
    }

    @Override
    public void createSkull(int x, int y, int z, String name, UUID playerUUID, Player p)
    {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            String[] a = getFromName(name, p);
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
            GameProfile profile = new GameProfile(UUID.randomUUID(), name);

            profile.getProperties().put("textures", new Property("textures", a[0], a[1]));

            EntityPlayer npc = new EntityPlayer(server, server.getWorldServer(DimensionManager.OVERWORLD), profile,
                                                new PlayerInteractManager(server.getWorldServer(DimensionManager.OVERWORLD)));
            npc.setPosition(x, y, z);

            byte[] dec = Base64.getDecoder().decode(a[0]);
            String s = new String(dec);

            s = s.substring(s.indexOf("l\":\"") + 1);
            s = s.substring(0, s.indexOf("\"}}}"));
            s = s.substring(s.indexOf("\""));
            s = s.substring(1);
            s = s.substring(1);
            s = s.substring(1);

            ItemStack skull = (Skull.getCustomSkull(s));
            SkullMeta sm = (SkullMeta) skull.getItemMeta();
            sm.setDisplayName(name);
            skull.setItemMeta(sm);

            headMap.put(playerUUID, skull);
        });
    }

    private String[] getFromName(String name, Player p)
    {
        try
        {
            if (!map.contains(name))
            {
                URL url_0 = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
                InputStreamReader reader_0 = new InputStreamReader(url_0.openStream());
                try
                {
                    String uuid = new JsonParser().parse(reader_0).getAsJsonObject().get("id").getAsString();
                    URL url_1 = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid
                        + "?unsigned=false");
                    InputStreamReader reader_1 = new InputStreamReader(url_1.openStream());
                    JsonObject textureProperty = new JsonParser().parse(reader_1).getAsJsonObject().get("properties")
                        .getAsJsonArray().get(0).getAsJsonObject();
                    String texture = textureProperty.get("value").getAsString();
                    String signature = textureProperty.get("signature").getAsString();
                    map.put(name, textureProperty);

                    return new String[] { texture, signature };
                }
                catch (IllegalStateException e)
                {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cError: &3Player not found in mojang database."));
                }

            }
            else
            {
                JsonObject textureProperty = map.get(name);
                String texture = textureProperty.get("value").getAsString();
                String signature = textureProperty.get("signature").getAsString();
                return new String[] { texture, signature };
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not get skin data from session servers!");
            e.printStackTrace();
            return null;
        }
        return null;
    }
}
