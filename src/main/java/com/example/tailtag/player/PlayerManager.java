package com.example.tailtag.player;

import com.example.tailtag.player.enums.PlayerColor;
import com.example.tailtag.player.enums.PlayerCondition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

// 좀 짬뽕이긴 한데 일단 개발 하고
// 후에 db겸 구현체로써 더 다듬을 예정
// 기본적으로 main <-> PlayerData(Service와 같은 구현체) <-> TailPlayer(유사 DAO느낌)
// main은 uniqueId만 사용하여 플로우만 관리하는 방향으로 하고싶음(가능하면)
// PlayerData에서 메인 로직 관리
public class PlayerManager {

    public void addPlayers(List<Player> players) {
        Collections.shuffle(players);
        PlayerColor[] colors = PlayerColor.values();
        for (int i = 0; i < players.size(); i++) {
            PlayerColor color = colors[i % players.size()];
            PlayerRepository.addPlayer(players.get(i), color);
        }
    }

    public void makeSlave(UUID masterUUID, UUID slaveUUID) {
        TailPlayer masterPlayer = PlayerRepository.getPlayerByUniqueId(masterUUID);
        TailPlayer slavePlayer = PlayerRepository.getPlayerByUniqueId(slaveUUID);

        List<TailPlayer> newSlaves = slavePlayer.enslave(slavePlayer);
        Player slave = slavePlayer.getPlayer();
        AttributeInstance victimMaxHealthAttr = slave.getAttribute(Attribute.MAX_HEALTH);
        if (victimMaxHealthAttr != null) {
            victimMaxHealthAttr.setBaseValue(8.0);
            slave.setHealth(8.0);
        }

        masterPlayer.addSlave(newSlaves);
        Player master = masterPlayer.getPlayer();
        if (master.getAttribute(Attribute.MAX_HEALTH) != null) {
            master.getAttribute(Attribute.MAX_HEALTH).setBaseValue(Math.max(8.0, 20.0 - 2.0 * masterPlayer.getSlaveCount()));
            master.setHealth(Math.min(master.getHealth(), master.getAttribute(Attribute.MAX_HEALTH).getBaseValue()));
        }
    }

    public PlayerColor getPlayerColor(@NotNull UUID uniqueId) {
        return PlayerRepository.getPlayerByUniqueId(uniqueId).getColor();
    }

    public void clear() {
        PlayerRepository.resetData();
    }

    public boolean isSlave(UUID uniqueId) {
        return PlayerRepository.getPlayerByUniqueId(uniqueId).getIsSlave();
    }



    /**
     * isMaster
     * @param masterUUID target master player
     * @param slaveUUID target slave player
     * @return whether the first player is the master of the second player.
     */
    public boolean isMaster(UUID masterUUID, UUID slaveUUID) {
        return PlayerRepository.getPlayerByUniqueId(slaveUUID).getUniqueId().equals(masterUUID);
    }

    public void deadNaturally(UUID uniqueId) {
        TailPlayer player = PlayerRepository.getPlayerByUniqueId(uniqueId);
        player.stun();
    }

    public boolean isStunned(UUID uniqueId) {
        return PlayerCondition.STUN == PlayerRepository.getPlayerByUniqueId(uniqueId).getState();
    }

    public void updateSlave() {
        List<UUID> slavePlayers = PlayerRepository.getSlavePlayerUniqueIds();
        for (UUID uniqueId : slavePlayers) {
            TailPlayer player = PlayerRepository.getPlayerByUniqueId(uniqueId);
            Player slave = player.getPlayer();
            Player master = player.getMaster().getPlayer();

            if (!slave.isOnline()) continue;

            // damage if too far from master
            if (master.isOnline()) {
                double distance = slave.getLocation().distance(master.getLocation());

                if (distance > 30) {
                    // 노예에게 데미지
                    slave.damage(1.0); // 0.5 하트 데미지

                    if (slave.getHealth() <= 0) {
                        slave.teleport(master.getLocation());
                        slave.setHealth(slave.getAttribute(Attribute.MAX_HEALTH).getValue());
                    }
                }
            }

            // slave debuff
            slave.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 25, 1, false, false));
        }
    }

    public void updateStunnedPlayer() {
        List<UUID> stunnedPlayers = PlayerRepository.getStunnedPlayerUniqueIds();
        for (UUID uniqueId : stunnedPlayers) {
            TailPlayer stunnedPlayer = PlayerRepository.getPlayerByUniqueId(uniqueId);
            Player player = stunnedPlayer.getPlayer();

            long deathDuration = System.currentTimeMillis() - stunnedPlayer.getDeathTime();
            if (deathDuration >= 120000) {
                stunnedPlayer.unstun();
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }
                player.setHealth(20);
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 1200, 0));
            } else {
                if (!player.isOnline()) continue;
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 255, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 25, -10, false, false));
            }
        }
    }

    public void updatePlayers() {
        PlayerRepository.clearPlayerState();
        List<TailPlayer> players = PlayerRepository.getPlayers();
        for (TailPlayer player : players) {
            UUID uniqueId = player.getUniqueId();

            if (player.isAlive()) PlayerRepository.addSurvivedPlayer(uniqueId);
            else if (player.getIsSlave()) PlayerRepository.addSlavePlayer(uniqueId);
            switch (player.getState()) {
                case STUN -> PlayerRepository.addStunnedPlayer(uniqueId);
                case OUT -> PlayerRepository.addOutPlayer(uniqueId);
            }
        }
    }

    public void unstun(UUID uniqueId) {
        PlayerRepository.getPlayerByUniqueId(uniqueId).unstun();
    }

    public void stun(UUID uniqueId) {
        PlayerRepository.getPlayerByUniqueId(uniqueId).stun();
    }

    public Player getTargetPlayer(UUID uniqueId) {
        PlayerColor nowColor = PlayerRepository.getPlayerByUniqueId(uniqueId).getColor().next();
        Player target = null;
        while (target == null) {
            if (isColorEliminated(nowColor)) {
                nowColor = nowColor.next();
                continue;
            }
            target = getTailPlayerByColor(nowColor).getPlayer();
        }
        return target;
    }
    
    public Player getHunterPlayer(UUID uniqueId) {
        PlayerColor nowColor = PlayerRepository.getPlayerByUniqueId(uniqueId).getColor().prev();
        Player hunter = null;
        while (hunter == null) {
            if (isColorEliminated(nowColor)) {
                nowColor = nowColor.prev();
                continue;
            }
            hunter = getTailPlayerByColor(nowColor).getPlayer();
        }
        return hunter;
    }

    private TailPlayer getTailPlayerByColor(PlayerColor color) {
        return PlayerRepository.getPlayerByColor(color);
    }
    
    private boolean isColorEliminated(PlayerColor color) {
        return PlayerRepository.isColorEliminated(color);
    }

    public Player calculateWinner() {
        List<UUID> survivedPlayerList = PlayerRepository.getSurvivedPlayer();
        if (survivedPlayerList.size() != 1)
            return null;
        UUID winnerUUID = survivedPlayerList.getFirst();
        return PlayerRepository.getPlayerByUniqueId(winnerUUID).getPlayer();
    }

    public Player getMasterPlayer(UUID uniqueId) {
        return PlayerRepository.getMasterPlayerByUniqueId(uniqueId).getPlayer();
    }

    public void updateDragonEggBuff() {
        for (TailPlayer tailPlayer : PlayerRepository.getPlayerMap()) {
            Player player = tailPlayer.getPlayer();
            if (!player.isOnline() || !player.getInventory().contains(Material.DRAGON_EGG)) continue;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 25, 1, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 25, 0, false, false));
        }
    }

    public void showHeartBeat() {
        for (TailPlayer tailPlayer : PlayerRepository.getPlayerMap()) {
            Player player = tailPlayer.getPlayer();

            if (tailPlayer.getIsSlave()) {
                // if player is slave, show distance between master and slave to player
                Player master = tailPlayer.getMaster().getPlayer();
                if (master == null || !master.isOnline()) continue;
                double distance = player.getLocation().distance(master.getLocation());
                player.sendActionBar(
                        Component.text("주인과의 거리: ", NamedTextColor.YELLOW)
                                .append(Component.text((int) distance))
                                .append(Component.text("블럭"))
                );
            } else {
                // detect nearby hunter
                PlayerColor playerColor = tailPlayer.getColor();
                if (playerColor == null) continue;
                Player hunter = getHunterPlayer(tailPlayer.getUniqueId());
                double distance = player.getLocation().distance(hunter.getLocation());

                if (distance > 30) continue;
                Location loc = player.getLocation();
                player.sendActionBar(Component.text("❤", NamedTextColor.RED));
                player.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 0.5f, 1.0f);

            }
        }
    }

    public void resetGame() {
        PlayerRepository.getPlayers();
        for (TailPlayer tailPlayer : PlayerRepository.getPlayerMap()) {
            Player player = tailPlayer.getPlayer();
            restoreInventory(player);
            player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
            player.clearActivePotionEffects();
            player.setFireTicks(0);
            player.setFoodLevel(20);
            player.setSaturation(20);
        }
        clear();
    }

    private void restoreInventory(Player player) {
        // 게임 종료 후 상태 초기화
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));

        // 체력과 상태 복원
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setFireTicks(0);
        player.clearActivePotionEffects();
        player.setExp(0);
        player.setLevel(0);
    }


    public Location findSafeSpawnLocation(World world, int x, int z) {
        // 최고 높이부터 시작해서 안전한 위치를 찾음
        int highestY = world.getHighestBlockYAt(x, z);

        // 하늘에서 스폰되는 것을 방지하기 위해 최대 높이 제한
        if (highestY > 100) {
            highestY = 100;
        }

        // 위에서부터 아래로 내려가면서 안전한 위치 찾기
        for (int y = highestY; y > 0; y--) {
            Location checkLoc = new Location(world, x, y, z);

            // 현재 블럭이 고체이고, 위 2블럭이 공기인지 확인
            if (checkLoc.getBlock().getType().isSolid() &&
                    !checkLoc.getBlock().getType().equals(Material.LAVA) &&
                    !checkLoc.getBlock().getType().equals(Material.WATER) &&
                    checkLoc.clone().add(0, 1, 0).getBlock().getType().equals(Material.AIR) &&
                    checkLoc.clone().add(0, 2, 0).getBlock().getType().equals(Material.AIR)) {

                return checkLoc.clone().add(0.5, 1, 0.5); // 블럭 중앙, 1블럭 위
            }
        }

        // 조건에 맞는 위치가 없을 경우, 기본 위치 사용 (예: Y=64)
        return new Location(world, x + 0.5, 65, z + 0.5);
    }

    public void spawnPlayers(Location gameCenter, int arenaSize) {
        Random random = new Random();
        World world = gameCenter.getWorld();

        List<TailPlayer> tailPlayers = PlayerRepository.getPlayers();

        for (TailPlayer player : tailPlayers) {
            // 20청크 범위 내 랜덤 위치 생성
            int offsetX = gameCenter.getBlockX() + (random.nextInt(arenaSize * 2) - arenaSize) * 16;
            int offsetZ = gameCenter.getBlockZ() + (random.nextInt(arenaSize * 2) - arenaSize) * 16;

            // 안전한 스폰 위치 찾기
            Location spawnLocation = findSafeSpawnLocation(world, offsetX, offsetZ);

            player.getPlayer().teleport(spawnLocation);
        }
    }

}
