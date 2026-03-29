const { withAndroidManifest } = require("@expo/config-plugins");

module.exports = function withKmcertoAndroid(config) {
  config = withAndroidManifest(config, (config) => {
    const manifest = config.modResults.manifest;
    const permissions = [
      "android.permission.SYSTEM_ALERT_WINDOW",
      "android.permission.FOREGROUND_SERVICE",
      "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    ];
    if (!manifest["uses-permission"]) {
      manifest["uses-permission"] = [];
    }
    for (const perm of permissions) {
      const exists = manifest["uses-permission"].some(
        (p) => p.$?.["android:name"] === perm
      );
      if (!exists) {
        manifest["uses-permission"].push({ $: { "android:name": perm } });
      }
    }
    return config;
  });

  return config;
};
