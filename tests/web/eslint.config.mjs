import globals from "globals";
export default [{
  files: ["**/*.js"],
  languageOptions: { ecmaVersion: 2023, sourceType: "module", globals: { ...globals.browser, google: "readonly", L: "readonly", QRCode: "readonly" } },
  rules: {
    "no-undef": "error", "no-unused-vars": ["warn", { args: "none", caughtErrors: "none" }], "no-dupe-keys": "error", "no-unreachable": "error",
    "no-redeclare": "error", "no-const-assign": "error", "no-self-assign": "error", "no-dupe-else-if": "error", "use-isnan": "error", "valid-typeof": "error",
  },
}];
