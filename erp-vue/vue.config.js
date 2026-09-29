'use strict'
const path = require('path')

function resolve(dir) {
  return path.join(__dirname, dir)
}

const CompressionPlugin = require('compression-webpack-plugin')

const name = process.env.VUE_APP_TITLE || 'SOYSOceanBox 管理后台' // 网页标题

// 后端接口（仅 dev-server 代理目标，不进生产 bundle；换开发机/后端端口时用
// VUE_APP_PROXY_TARGET 覆盖，无需改代码）
const baseUrl = process.env.VUE_APP_PROXY_TARGET || 'http://localhost:8080'

const port = process.env.port || process.env.npm_config_port || 80 // 端口

module.exports = {
  // 生产 publicPath：ERP 部署在插件 dist 的 admin/ 子目录下（与用户页 dist/index.html 区分）。
  // wujie 微前端 + 深层 URL 下必须使用绝对前缀；可用 VUE_APP_PUBLIC_PATH 覆盖。dev 保持根路径。
  publicPath: process.env.NODE_ENV === "production"
    ? (process.env.VUE_APP_PUBLIC_PATH || "/web/plugins/soysoceanbox/admin/")
    : "/",
  // 构建产物直接输出到插件资源目录 dist/admin（随 jar 打包，由 SOYS 框架 dist 托管）
  outputDir: '../src/main/resources/dist/admin',
  // 静态资源 (js、css、img、fonts) 目录
  assetsDir: 'static',
  productionSourceMap: false,
  transpileDependencies: ['quill'],
  // webpack-dev-server 相关配置
  devServer: {
    host: '0.0.0.0',
    port: port,
    open: true,
    proxy: {
      [process.env.VUE_APP_BASE_API]: {
        target: baseUrl,
        changeOrigin: true,
        pathRewrite: {
          ['^' + process.env.VUE_APP_BASE_API]: ''
        }
      },
      // springdoc proxy
      '^/v3/api-docs/(.*)': {
        target: baseUrl,
        changeOrigin: true
      }
    },
    disableHostCheck: true
  },
  css: {
    loaderOptions: {
      sass: {
        sassOptions: { outputStyle: "expanded" }
      }
    }
  },
  configureWebpack: {
    name: name,
    resolve: {
      alias: {
        '@': resolve('src')
      }
    },
    // externals：基础库不打包，运行时从主插件公共资源加载（wujie externals 注入）
    externals: {
      'vue': 'Vue',
      'vuex': 'Vuex',
      'vue-router': 'VueRouter',
      'element-ui': 'ELEMENT'
    },
    plugins: [
      new CompressionPlugin({
        cache: false,
        test: /\.(js|css|html|jpe?g|png|gif|svg)?$/i,
        filename: '[path][base].gz[query]',
        algorithm: 'gzip',
        minRatio: 0.8,
        deleteOriginalAssets: false
      })
    ],
  },
  chainWebpack(config) {
    config.plugins.delete('preload')
    config.plugins.delete('prefetch')

    // set svg-sprite-loader
    config.module
      .rule('svg')
      .exclude.add(resolve('src/assets/icons'))
      .end()
    config.module
      .rule('icons')
      .test(/\.svg$/)
      .include.add(resolve('src/assets/icons'))
      .end()
      .use('svg-sprite-loader')
      .loader('svg-sprite-loader')
      .options({
        symbolId: 'icon-[name]'
      })
      .end()

    config.when(process.env.NODE_ENV !== 'development', config => {
          config
            .plugin('ScriptExtHtmlWebpackPlugin')
            .after('html')
            .use('script-ext-html-webpack-plugin', [{
              inline: /runtime\..*\.js$/
            }])
            .end()

          config.optimization.splitChunks({
            chunks: 'all',
            cacheGroups: {
              libs: {
                name: 'chunk-libs',
                test: /[\\/]node_modules[\\/]/,
                priority: 10,
                chunks: 'initial'
              },
              elementUI: {
                name: 'chunk-elementUI',
                test: /[\\/]node_modules[\\/]_?element-ui(.*)/,
                priority: 20
              },
              commons: {
                name: 'chunk-commons',
                test: resolve('src/components'),
                minChunks: 3,
                priority: 5,
                reuseExistingChunk: true
              }
            }
          })
          config.optimization.runtimeChunk('single')
    })
  }
}
