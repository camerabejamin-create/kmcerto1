const { withAndroidManifest, withAppBuildGradle, withSettingsGradle } = require("@expo/config-plugins");

module.exports = function withKmcertoAndroid(config) {
  // Adiciona o módulo nativo ao settings.gradle
  config = withSettingsGradle(config, (config) => {
    if (!config.modResults.contents.includes(":kmcerto-native")) {
      config.modResults.contents += `
include ':kmcerto-native'
project(':kmcerto-native').projectDir = new File(rootProject.projectDir, '../modules/kmcerto-native/android')
`;
    }
    return config;
  });

  // Adiciona a dependência no build.gradle do app
  config = withAppBuildGradle(config, (config) => {
    if (!config.modResults.contents.includes("kmcerto-native")) {
      config.modResults.contents = config.modResults.contents.replace(
        /dependencies\s*\{/,
        `dependencies {\n    implementation project(':kmcerto-native')`
      );
    }
    return config;
  });

  // Adiciona permissões no AndroidManifest
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
