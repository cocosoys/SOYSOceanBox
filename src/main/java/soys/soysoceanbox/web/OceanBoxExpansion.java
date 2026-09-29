package soys.soysoceanbox.web;

import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import com.github.cocosoys.mc.soyshttpovermc.api.SoysExpansion;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import soys.soysoceanbox.SOYSOceanBox;

import java.util.Arrays;
import java.util.List;

/**
 * SOYSOceanBox 的 SOYSHTTPOverMC 扩展定义。
 *
 * <p>由主类在检测到 SOYSHTTPOverMC 已加载时创建并 {@link #register()}：框架自动完成
 * 业务端点注册、{@code dist/} 页面托管（用户中心 Hypixel 页 + 管理 ERP）、CORS 与卸载。</p>
 *
 * <p>端点统一挂 {@code /api/plugins/soysoceanbox} 前缀；
 * 页面托管于 {@code /web/plugins/soysoceanbox}。</p>
 */
public final class OceanBoxExpansion extends SoysExpansion {

    private final SOYSOceanBox plugin;
    private final OceanBoxController controller;

    public OceanBoxExpansion(SOYSOceanBox plugin) {
        this.plugin = plugin;
        this.controller = new OceanBoxController(plugin);
    }

    @Override
    public String getIdentifier() {
        return "soysoceanbox";
    }

    @Override
    public String getAuthor() {
        return "SOYS";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    /** 前端资源根：自动托管 jar 内 dist/（磁盘 plugins/SOYSOceanBox/dist 优先，支持热替换）。 */
    @Override
    protected String resourceRoot() {
        return "dist";
    }

    /** 待注册端点：本扩展健康检查 + 业务控制器。 */
    @Override
    protected List<Object> buildControllers() {
        return Arrays.asList(this, controller);
    }

    /** 放开 CORS，便于网页在同源之外调用。 */
    @Override
    protected CorsSpec[] cors() {
        return new CorsSpec[]{
                new CorsSpec("/api/plugins/soysoceanbox", "*",
                        "GET,POST,PUT,DELETE,OPTIONS", "*", false)
        };
    }

    @ApiName("海洋宝箱插件健康检查")
    @GetMapping("/ping")
    public AjaxResult ping() {
        return AjaxResult.success("pong");
    }
}
