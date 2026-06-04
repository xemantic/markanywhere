// Enables Chrome's experimental web platform features in the headless test
// browser so `Element.computedRole` is exposed — required by the layout-table
// detection in `Element.toSemanticEvents(respectAccessibility = true)`.
// Without the flag the property is `undefined` and the related tests cannot
// observe the accessibility verdict. See ElementToSemanticEvents.kt.
config.customLaunchers = config.customLaunchers || {};
config.customLaunchers.ChromeHeadlessExperimentalA11y = {
    base: 'ChromeHeadless',
    flags: ['--enable-experimental-web-platform-features']
};
config.browsers = ['ChromeHeadlessExperimentalA11y'];