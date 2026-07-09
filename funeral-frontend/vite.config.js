import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  test: {
    environment: 'happy-dom',
    globals: true,
    css: false,
    deps: {
      optimizer: {
        web: {
          include: ['element-plus']
        }
      }
    },
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json-summary'],
      reportsDirectory: './coverage',
      include: ['src/**/*.js', 'src/**/*.vue'],
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/v2': {
        target: 'http://localhost:8911',
        changeOrigin: true,
        rewrite: (path) => path
      },
      '/funeral_addition': {
        target: 'http://localhost:8911',
        changeOrigin: true,
        rewrite: (path) => path
      }
    }
  }
})
