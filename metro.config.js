const { getDefaultConfig } = require("expo/metro-config");
const { withNativeWind } = require("nativewind/metro");
const path = require("path");

const config = getDefaultConfig(__dirname);

// Resolve local native modules
config.resolver.extraNodeModules = {
  ...config.resolver.extraNodeModules,
  "@/modules/kmcerto-native": path.resolve(__dirname, "modules/kmcerto-native"),
};

config.watchFolders = [
  ...(config.watchFolders || []),
  path.resolve(__dirname, "modules"),
];

module.exports = withNativeWind(config, {
  input: "./global.css",
  forceWriteFileSystem: true,
});
