package com.region.buyregion.listeners;

import com.region.buyregion.BuyRegion;
import com.region.buyregion.helpers.ChatHelper;
import com.region.buyregion.regions.RentableRegion;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

public class RenterTask {
    private BuyRegion plugin = BuyRegion.instance;

    public RenterTask() {
        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, this::run, plugin.config.tickRate, plugin.config.tickRate);
    }

    private void run() {
        try {
            long now = new Date().getTime();
            ConcurrentHashMap<String, Long> expirations = plugin.rentedRegionExpirations.get();
            for (String regionName : expirations.keySet()) {
                if (expirations.containsKey(regionName)) {
                    long regionExp = expirations.get(regionName);
                    if (regionExp <= now) {
                        boolean renewed = false;

                        RentableRegion rentedRegion = plugin.loadRegion(regionName);
                        if (plugin.autoRenews.get().containsKey(rentedRegion.renter)) {
                            if (plugin.autoRenews.get().get(rentedRegion.renter)) {
                                Player player = plugin.getServer().getPlayer(rentedRegion.renter);

                                double regionPrice = Double.parseDouble(rentedRegion.signLine3);
                                if (BuyRegion.econ.getBalance(rentedRegion.renter) >= regionPrice) {
                                    EconomyResponse response = BuyRegion.econ.withdrawPlayer(rentedRegion.renter, regionPrice);
                                    if (response.transactionSuccess()) {
                                        renewed = true;

                                        String[] timeSpan = rentedRegion.signLine4.split(" ");
                                        long currentExpiration = expirations.get(regionName);

                                        BuyRegion.DateResult timeData = plugin.parseDateString(Integer.parseInt(timeSpan[0]), timeSpan[1], currentExpiration);
                                        expirations.put(regionName, timeData.Time);
                                        plugin.rentedRegionExpirations.save();

                                        plugin.logActivity(rentedRegion.renter, " AUTORENEW " + regionName);

                                        SimpleDateFormat sdf = new SimpleDateFormat(plugin.config.dateFormatString);
                                        if (player != null) {
                                            player.sendMessage(ChatHelper.notice("Renewed", regionName, sdf.format(new Date(timeData.Time))));
                                            player.sendMessage(ChatHelper.notice("NewBalance", BuyRegion.econ.getBalance(rentedRegion.renter)));
                                        }
                                        World world = plugin.getServer().getWorld(rentedRegion.worldName);

                                        double x = Double.parseDouble(rentedRegion.signLocationX);
                                        double y = Double.parseDouble(rentedRegion.signLocationY);
                                        double z = Double.parseDouble(rentedRegion.signLocationZ);
                                        float pitch = Float.parseFloat(rentedRegion.signLocationPitch);
                                        float yaw = Float.parseFloat(rentedRegion.signLocationYaw);

                                        Location signLoc = new Location(world, x, y, z, pitch, yaw);

                                        Block currentBlock = world.getBlockAt(signLoc);
                                        if (currentBlock.getType() == Material.SIGN || (currentBlock.getType() == Material.WALL_SIGN)) {
                                            Sign theSign = (Sign) currentBlock.getState();

                                            theSign.setLine(0, regionName);
                                            theSign.setLine(1, rentedRegion.renter);
                                            theSign.setLine(2, ChatColor.WHITE + "Until:");
                                            theSign.setLine(3, sdf.format(new Date(timeData.Time)));
                                            theSign.update();

                                            theSign.update();
                                        }
                                    }
                                } else if (player != null) {
                                    player.sendMessage(ChatHelper.notice("NotEnoughRenew", regionName));
                                    player.sendMessage(ChatHelper.notice("Balance", BuyRegion.econ.getBalance(rentedRegion.renter)));
                                }
                            }
                        }
                        if (!renewed) {
                            expirations.remove(regionName);
                            plugin.rentedRegionExpirations.save();

                            World world = plugin.getServer().getWorld(rentedRegion.worldName);
                            ProtectedRegion region = plugin.getWorldGuardRegion(rentedRegion.worldName, regionName);

                            if (region == null)
                            return;
                            DefaultDomain dd = region.getMembers();

                            dd.removePlayer(rentedRegion.renter);

                            region.setMembers(dd);

                            plugin.removeRentedRegionFromCount(rentedRegion.renter);

                            double x = Double.parseDouble(rentedRegion.signLocationX);
                            double y = Double.parseDouble(rentedRegion.signLocationY);
                            double z = Double.parseDouble(rentedRegion.signLocationZ);
                            float pitch = Float.parseFloat(rentedRegion.signLocationPitch);
                            float yaw = Float.parseFloat(rentedRegion.signLocationYaw);

                            Location signLoc = new Location(world, x, y, z, pitch, yaw);

                            Block currentBlock = world.getBlockAt(signLoc);
                            if (currentBlock.getType() == Material.SIGN || (currentBlock.getType() == Material.WALL_SIGN)) {
                                Sign theSign = (Sign) currentBlock.getState();

                                theSign.setLine(0, rentedRegion.signLine1);
                                theSign.setLine(1, rentedRegion.signLine2);
                                theSign.setLine(2, rentedRegion.signLine3);
                                theSign.setLine(3, rentedRegion.signLine4);

                                theSign.update();
                            } else {
                                try {
                                    if (rentedRegion.signType.equals("WALL_SIGN")) {
                                        currentBlock.setType(Material.WALL_SIGN);
                                    } else {
                                        currentBlock.setType(Material.SIGN);
                                    }
                                    Sign newSign = (Sign) currentBlock.getState();

                                    newSign.setLine(0, rentedRegion.signLine1);
                                    newSign.setLine(1, rentedRegion.signLine2);
                                    newSign.setLine(2, rentedRegion.signLine3);
                                    newSign.setLine(3, rentedRegion.signLine4);

                                    newSign.update();
                                } catch(Exception e) {
                                    plugin.getLogger().severe("RentRegion automatic sign creation failed for region " + rentedRegion.regionName);
                                }
                            }
                            File regionFile = new File(plugin.config.signDataLoc + regionName + ".digi");
                            if (regionFile.exists()) {
                                regionFile.delete();
                            }
                            Player player = plugin.getServer().getPlayer(rentedRegion.renter);
                            if ((player != null)) {
                                player.sendMessage(ChatHelper.notice("Expired", regionName));
                            }
                            plugin.logActivity(rentedRegion.renter, " EXPIRED " + rentedRegion.regionName);
                        }
                    }
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}
