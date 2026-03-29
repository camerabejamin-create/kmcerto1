const { withAndroidManifest } = require("@expo/config-plugins");

module.exports = function withKmcertoAndroid(config) {
  return withAndroidManifest(config, (config) => {
    return config;
  });
};
