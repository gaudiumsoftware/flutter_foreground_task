/// Defines the vibration patterns for notifications.
///
/// Available patterns:
/// - [none]: No vibration.
/// - [common]: Vibrate a single time with a common pattern.
/// - [emphasis]: Vibrate 3 times with emphasis.
enum NotificationVibratePattern {
  /// No vibration.
  none,

  /// Vibrate with a common pattern.
  common,

  /// Vibrate with a custom pattern.
  emphasis,
}
