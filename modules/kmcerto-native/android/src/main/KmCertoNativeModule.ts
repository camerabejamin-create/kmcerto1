import { requireNativeModule, EventEmitter } from "expo-modules-core";
import type { KmCertoOverlayEventPayload } from "./KmCertoNative.types";

const NativeModule = requireNativeModule("KmCertoNative");
const emitter = new EventEmitter(NativeModule);

const KmCertoNativeModule = {
  isOverlayPermissionGranted: (): Promise<boolean> =>
    NativeModule.isOverlayPermissionGranted(),
  isAccessibilityServiceEnabled: (): Promise<boolean> =>
    NativeModule.isAccessibilityServiceEnabled(),
  openOverlaySettings: (): Promise<boolean> =>
    NativeModule.openOverlaySettings(),
  openAccessibilitySettings: (): Promise<boolean> =>
    NativeModule.openAccessibilitySettings(),
  startMonitoring: (): Promise<boolean> =>
    NativeModule.startMonitoring(),
  stopMonitoring: (): Promise<boolean> =>
    NativeModule.stopMonitoring(),
  hideOverlay: (): Promise<boolean> =>
    NativeModule.hideOverlay(),
  setMinimumPerKm: (value: number): Promise<boolean> =>
    NativeModule.setMinimumPerKm(value),
  getMinimumPerKm: (): Promise<number> =>
    NativeModule.getMinimumPerKm(),
  showTestOverlay: (payload: string): Promise<boolean> =>
    NativeModule.showTestOverlay(payload),
  addListener: (
    event: "KmCertoOverlayData",
    listener: (payload: KmCertoOverlayEventPayload) => void
  ) => emitter.addListener(event, listener),
};

export default KmCertoNativeModule;
