import { reactive } from 'vue';

export const toastState = reactive({ message: '', visible: false });

let timer: ReturnType<typeof setTimeout> | undefined;

export function toast(message: string): void {
  toastState.message = message;
  toastState.visible = true;
  clearTimeout(timer);
  timer = setTimeout(() => {
    toastState.visible = false;
  }, 2400);
}
