import { EventEmitter } from "expo-modules-core";
import type { KmCertoOverlayEventPayload } from "./KmCertoNative.types";

let NativeModule: any = null;
let emitter: any = null;

try {
  const { requireNativeModule } = require("expo-modules-core");
  NativeModule = requireNativeModule("KmCertoNative");
  emitter = new EventEmitter(NativeModule);
} catch (e) {
  console.warn("KmCertoNative module not available:", e);
}

const KmCertoNativeModule = {
  isOverlayPermissionGranted: (): Promise<boolean> =>
    NativeModule?.isOverlayPermissionGranted() ?? Promise.resolve(false),
  isAccessibilityServiceEnabled: (): Promise<boolean> =>
    NativeModule?.isAccessibilityServiceEnabled() ?? Promise.resolve(false),
  openOverlaySettings: (): Promise<boolean> =>
    NativeModule?.openOverlaySettings() ?? Promise.resolve(false),
  openAccessibilitySettings: (): Promise<boolean> =>
    NativeModule?.openAccessibilitySettings() ?? Promise.resolve(false),
  startMonitoring: (): Promise<boolean> =>
    NativeModule?.startMonitoring() ?? Promise.resolve(false),
  stopMonitoring: (): Promise<boolean> =>
    NativeModule?.stopMonitoring() ?? Promise.resolve(false),
  hideOverlay: (): Promise<boolean> =>
    NativeModule?.hideOverlay() ?? Promise.resolve(false),
  setMinimumPerKm: (value: number): Promise<boolean> =>
    NativeModule?.setMinimumPerKm(value) ?? Promise.resolve(false),
  getMinimumPerKm: (): Promise<number> =>
    NativeModule?.getMinimumPerKm() ?? Promise.resolve(1.5),
  showTestOverlay: (payload: string): Promise<boolean> =>
    NativeModule?.showTestOverlay(payload) ?? Promise.resolve(false),
  addListener: (
    event: "KmCertoOverlayData",
    listener: (payload: KmCertoOverlayEventPayload) => void
  ) => emitter?.addListener(event, listener) ?? { remove: () => {} },
};

export default KmCertoNativeModule;
