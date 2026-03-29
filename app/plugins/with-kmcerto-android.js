const { withAndroidManifest } = require("@expo/config-plugins");

function withKmCertoManifest(config) {
  return withAndroidManifest(config, (config) => {
    const manifest = config.modResults;
    const app = manifest.manifest.application[0];

    if (!manifest.manifest["uses-permission"]) {
      manifest.manifest["uses-permission"] = [];
    }
    const permissions = manifest.manifest["uses-permission"];
    const requiredPermissions = [
      "android.permission.SYSTEM_ALERT_WINDOW",
      "android.permission.FOREGROUND_SERVICE",
      "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    ];
    for (const perm of requiredPermissions) {
      const exists = permissions.some((p) => p.$?.["android:name"] === perm);
      if (!exists) {
        permissions.push({ $: { "android:name": perm } });
      }
    }

    if (!app.service) app.service = [];

    const overlayServiceName = "expo.modules.kmcertonative.KmCertoOverlayService";
    const hasOverlay = app.service.some((s) => s.$?.["android:name"] === overlayServiceName);
    if (!hasOverlay) {
      app.service.push({
        $: {
          "android:name": overlayServiceName,
          "android:exported": "false",
          "android:foregroundServiceType": "specialUse",
        },
        property: [
          {
            $: {
              "android:name": "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE",
              "android:value": "overlay",
            },
          },
        ],
      });
    }

    const accessibilityServiceName = "expo.modules.kmcertonative.KmCertoAccessibilityService";
    const hasAccessibility = app.service.some((s) => s.$?.["android:name"] === accessibilityServiceName);
    if (!hasAccessibility) {
      app.service.push({
        $: {
          "android:name": accessibilityServiceName,
          "android:exported": "true",
          "android:label": "KmCerto",
          "android:permission": "android.permission.BIND_ACCESSIBILITY_SERVICE",
        },
        "intent-filter": [
          {
            action: [
              {
                $: { "android:name": "android.accessibilityservice.AccessibilityService" },
              },
            ],
          },
        ],
        "meta-data": [
          {
            $: {
              "android:name": "android.accessibilityservice",
              "android:resource": "@xml/kmcerto_accessibility_service_config",
            },
          },
        ],
      });
    }

    return config;
  });
}

module.exports = function withKmcertoAndroid(config) {
  return withKmCertoManifest(config);
};
