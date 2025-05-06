package ru.mxsregion.mxsregion;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class MxsRegion extends JavaPlugin implements Listener {
    private static final Logger LOGGER = Logger.getLogger("MxsRegion");
    private Set<Material> protectedMaterials;
    private NamespacedKey noPistonMoveKey;

    @Override
    public void onEnable() {
        // Сохраняем config.yml, если его нет
        saveDefaultConfig();
        // Загружаем конфигурацию
        loadConfig();
        // Инициализируем ключ для PersistentDataContainer
        noPistonMoveKey = new NamespacedKey(this, "no_piston_move");
        // Регистрируем слушатель событий
        getServer().getPluginManager().registerEvents(this, this);
        LOGGER.info("MxsRegion enabled. Protected materials: " + protectedMaterials);
    }

    @Override
    public void onDisable() {
        LOGGER.info("MxsRegion disabled.");
    }

    // Загрузка конфигурации
    private void loadConfig() {
        FileConfiguration config = getConfig();
        protectedMaterials = new HashSet<>();
        List<String> materialNames = config.getStringList("materials");

        for (String materialName : materialNames) {
            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                protectedMaterials.add(material);
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Invalid material in config: " + materialName);
            }
        }

        // Если список пуст, добавляем значения по умолчанию
        if (protectedMaterials.isEmpty()) {
            protectedMaterials.add(Material.DIAMOND_ORE);
            protectedMaterials.add(Material.EMERALD_ORE);
            config.set("materials", List.of("DIAMOND_ORE", "EMERALD_ORE"));
            saveConfig();
            LOGGER.info("No valid materials found in config. Using defaults: DIAMOND_ORE, EMERALD_ORE");
        }
    }

    // Событие установки блока игроком
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // Проверяем, что игрок в режиме выживания
        if (event.getPlayer().getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        Block block = event.getBlock();
        Material material = block.getType();

        // Проверяем, является ли материал защищённым
        if (protectedMaterials.contains(material)) {
            // Добавляем тег в PersistentDataContainer блока
            PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
            String blockKey = getBlockKey(block);
            container.set(new NamespacedKey(this, blockKey), PersistentDataType.BYTE, (byte) 1);
            LOGGER.info("Tagged block " + material + " at " + block.getLocation() + " with no_piston_move");
        }
    }

    // Событие разрушения блока игроком
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        // Проверяем, есть ли тег no_piston_move
        if (hasNoPistonMoveTag(block)) {
            // Удаляем тег из PersistentDataContainer
            PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
            String blockKey = getBlockKey(block);
            container.remove(new NamespacedKey(this, blockKey));
            LOGGER.info("Removed no_piston_move tag from block " + block.getType() + " at " + block.getLocation());
        }
    }

    // Событие выдвижения поршня
    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        // Проверяем все блоки, которые поршень пытается переместить
        for (Block block : event.getBlocks()) {
            if (hasNoPistonMoveTag(block)) {
                // Отменяем событие, если хотя бы один блок имеет тег
                event.setCancelled(true);
                LOGGER.info("Cancelled piston extend at " + event.getBlock().getLocation() + " due to tagged block: " + block.getType());
                return;
            }
        }
    }

    // Событие втягивания поршня
    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        // Проверяем все блоки, которые поршень пытается втянуть
        for (Block block : event.getBlocks()) {
            if (hasNoPistonMoveTag(block)) {
                // Отменяем событие, если хотя бы один блок имеет тег
                event.setCancelled(true);
                LOGGER.info("Cancelled piston retract at " + event.getBlock().getLocation() + " due to tagged block: " + block.getType());
                return;
            }
        }
    }

    // Проверка наличия тега no_piston_move у блока
    private boolean hasNoPistonMoveTag(Block block) {
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        return container.has(new NamespacedKey(this, blockKey), PersistentDataType.BYTE);
    }

    // Генерация уникального ключа для блока
    private String getBlockKey(Block block) {
        return "no_piston_move_" + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }
}
