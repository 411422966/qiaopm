/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

declare module 'nprogress' {
  export interface NProgressOptions {
    minimum?: number
    easing?: string
    speed?: number
    trickle?: boolean
    trickleSpeed?: number
    showSpinner?: boolean
    parent?: string
    template?: string
  }
  const NProgress: {
    configure(options: NProgressOptions | ((options: NProgressOptions) => void)): void
    start(): void
    done(): void
    inc(amount?: number): void
    set(n: number): void
    isStarted(): boolean
  }
  export default NProgress
}

declare module 'vue3-recaptcha-v2' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<any, any, any>
  export default component
}

declare module 'echarts' {
  const echarts: any
  export default echarts
}

declare module 'echarts/core' {
  const echarts: any
  export default echarts
}

declare module 'pinia' {
  export * from 'pinia'
}
