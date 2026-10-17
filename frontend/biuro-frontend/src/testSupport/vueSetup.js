import { readFile } from 'node:fs/promises'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, nextTick } from 'vue'

// Exercise real setup actions/watchers without adding a DOM/test framework.
export async function setupView(name, router) {
  const file = new URL(name === 'App' ? '../App.vue' : ['SignInForm', 'BiuroHeader'].includes(name) ? `../components/${name}.vue` : `../views/${name}.vue`, import.meta.url)
  const { descriptor } = parse(await readFile(file, 'utf8'))
  let source = compileScript(descriptor, { id: name }).content
  const key = `__${name}Router`
  globalThis[key] = router
  source = source.replace(/import .* from 'vue-router'/, `const useRouter = () => globalThis.${key}; const onBeforeRouteLeave = fn => { globalThis.${key}.guard = fn };`)
  source = source.replace(/import (\w+) from '.*\.vue'/g, 'const $1 = {}')
  source = source.replace(/from '([^']+)'/g, (match, path) => {
    if (path === 'vue') return `from '${import.meta.resolve('vue')}'`
    return path.startsWith('.') ? `from '${new URL(path, file).href}'` : match
  })
  source = source.replace('__expose();', '').replace('return __returned__', '__expose(__returned__); return __returned__')
  const View = (await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}#${Math.random()}`)).default
  View.render = () => null
  const renderer = createRenderer({ createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null,
    nextSibling: () => null, createText: () => ({}), setText() {}, createElement: () => ({}), patchProp() {}, setElementText() {} })
  const app = renderer.createApp(View), view = app.mount({})
  return { view, unmount() { app.unmount(); delete globalThis[key] } }
}
export async function settle() { await nextTick(); await new Promise(resolve => setTimeout(resolve, 0)); await nextTick() }
