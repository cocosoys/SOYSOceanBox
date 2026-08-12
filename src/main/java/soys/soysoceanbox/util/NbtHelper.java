package soys.soysoceanbox.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;

/**
 * 物品 NBT 与自定义模型数据的反射工具。
 * <p>
 * 设计前提：插件编译仅依赖 spigot-api（1.12.2），<b>不涉及</b>任何 CraftBukkit / NMS 的
 * 编译期导入，因此这里全部使用运行时反射，保证在不同服务端版本上都能编译通过。
 * </p>
 * <p>
 * 版本兼容性：
 * <ul>
 *   <li>自定义模型数据（CustomModelData）自 1.14 起在 {@code ItemMeta} 上提供，
 *       1.12.2 不存在该方法，会被安全忽略；</li>
 *   <li>原始 NBT 仅在服务端提供对应 NMS 类时尝试应用，失败则仅记录警告并原样返回物品，
 *       不会中断发放流程。</li>
 * </ul>
 */
public final class NbtHelper {

    private NbtHelper() {
    }

    /**
     * 在支持的服务端上为 ItemMeta 设置自定义模型数据。1.12.2 等不支持的版本直接忽略。
     */
    public static void applyCustomModelData(ItemMeta meta, Integer customModelData) {
        if (meta == null || customModelData == null) {
            return;
        }
        try {
            Method setter = meta.getClass().getMethod("setCustomModelData", Integer.class);
            setter.invoke(meta, customModelData);
        } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException ignored) {
            // 服务端不支持 CustomModelData（如 1.12.2），忽略即可
        }
    }

    /**
     * 将一段 MojangSON 形式的 NBT 合并到物品上。
     *
     * @param item  待处理的物品
     * @param nbtJson  形如 {@code "{display:{Name:\"\\\"X\\\"\"}}"} 的 MojangSON 字符串
     * @return 应用后的物品（失败时返回原物品）
     */
    public static ItemStack applyNbt(ItemStack item, String nbtJson) {
        if (item == null || nbtJson == null || nbtJson.trim().isEmpty()) {
            return item;
        }
        try {
            String version = serverVersion();
            if (version == null) {
                return item;
            }

            // 取得 NMS 的 ItemStack 副本
            Class<?> craftItemStack = Class.forName(
                    "org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
            Object nmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class).invoke(null, item);

            Class<?> nbtClass = Class.forName("net.minecraft.server." + version + ".NBTTagCompound");
            Object handle = nmsCopy.getClass().getMethod("getTag").invoke(nmsCopy);
            if (handle == null) {
                handle = nbtClass.getDeclaredConstructor().newInstance();
            }

            Object parsed = parseMojangson(version, nbtJson);
            if (parsed == null) {
                return item;
            }
            // NBTTagCompound.a(NBTTagCompound) 将 parsed 合并进 handle
            nbtClass.getMethod("a", nbtClass).invoke(handle, parsed);
            nmsCopy.getClass().getMethod("setTag", nbtClass).invoke(nmsCopy, handle);

            ItemStack result = (ItemStack) craftItemStack.getMethod("asBukkitCopy",
                    nmsCopy.getClass()).invoke(null, nmsCopy);
            return result != null ? result : item;
        } catch (Throwable t) {
            // 任何反射/解析失败都静默降级，不影响物品发放
            return item;
        }
    }

    /**
     * 将 MojangSON 解析为 NBTTagCompound，兼容 1.12.2（JsonToNBT）与 1.13+（MojangsonParser）。
     */
    private static Object parseMojangson(String version, String json) throws Exception {
        String base = "net.minecraft.server." + version + ".";
        // 1.13+ : MojangsonParser.parse(String)
        try {
            Class<?> parser = Class.forName(base + "MojangsonParser");
            Method parse = parser.getMethod("parse", String.class);
            return parse.invoke(null, json);
        } catch (ClassNotFoundException ignored) {
            // 1.12.2 无此类，继续尝试 JsonToNBT
        }
        // 1.12.2 : JsonToNBT.a(String) 返回 NBTTagCompound
        Class<?> jsonToNbt = Class.forName(base + "JsonToNBT");
        Method a = jsonToNbt.getMethod("a", String.class);
        return a.invoke(null, json);
    }

    /**
     * 取得服务端 NMS 包版本后缀，如 {@code v1_12_R1}。
     */
    private static String serverVersion() {
        try {
            String pkg = org.bukkit.Bukkit.getServer().getClass().getPackage().getName();
            String[] parts = pkg.split("\\.");
            return parts[parts.length - 1];
        } catch (Throwable t) {
            return null;
        }
    }
}
