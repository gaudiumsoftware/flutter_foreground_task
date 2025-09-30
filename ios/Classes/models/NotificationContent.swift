//
//  NotificationContent.swift
//  flutter_foreground_task
//
//  Created by Woo Jin Hwang on 8/2/24.
//

import Foundation

struct NotificationContent {
  let title: String
  let text: String
  let buttons: Array<NotificationButton>
  let notificationSound: String?
  let vibratePattern: String?
  
  static func getData() -> NotificationContent {
    let prefs = UserDefaults.standard
    
    let title = prefs.string(forKey: NOTIFICATION_CONTENT_TITLE) ?? ""
    let text = prefs.string(forKey: NOTIFICATION_CONTENT_TEXT) ?? ""
    
    var buttons: [NotificationButton] = []
    if let buttonsJsonString = prefs.string(forKey: NOTIFICATION_CONTENT_BUTTONS) {
      if let buttonsJsonData = buttonsJsonString.data(using: .utf8) {
        if let buttonsJsonArr = try? JSONSerialization.jsonObject(with: buttonsJsonData, options: []) as? [Any] {
          for buttonJsonObj in buttonsJsonArr {
            buttons.append(NotificationButton.fromJSONObject(buttonJsonObj))
          }
        }
      }
    }

    let notificationSound = prefs.string(forKey: NOTIFICATION_CONTENT_SOUND)

    let vibratePattern = prefs.string(forKey: NOTIFICATION_CONTENT_VIBRATE_PATTERN)

    return NotificationContent(title: title, text: text, buttons: buttons, notificationSound: notificationSound, vibratePattern: vibratePattern)
  }
  
  static func setData(args: Dictionary<String, Any>) {
    let prefs = UserDefaults.standard
    
    let title = args[NOTIFICATION_CONTENT_TITLE] as? String ?? ""
    prefs.set(title, forKey: NOTIFICATION_CONTENT_TITLE)
    
    let text = args[NOTIFICATION_CONTENT_TEXT] as? String ?? ""
    prefs.set(text, forKey: NOTIFICATION_CONTENT_TEXT)
    
    if let buttonsJson = args[NOTIFICATION_CONTENT_BUTTONS] as? [Any] {
      if let buttonsJsonData = try? JSONSerialization.data(withJSONObject: buttonsJson, options: []) {
        if let buttonsJsonString = String(data: buttonsJsonData, encoding: .utf8) {
          prefs.set(buttonsJsonString, forKey: NOTIFICATION_CONTENT_BUTTONS)
        }
      }
    }

    let notificationSound = args[NOTIFICATION_CONTENT_SOUND] as? String ?? ""

    let vibratePattern = args[NOTIFICATION_CONTENT_VIBRATE_PATTERN] as? String ?? ""

    prefs.set(notificationSound, forKey: NOTIFICATION_CONTENT_SOUND)
  }
  
  static func updateData(args: Dictionary<String, Any>) {
    let prefs = UserDefaults.standard
    
    if let title = args[NOTIFICATION_CONTENT_TITLE] as? String {
      prefs.set(title, forKey: NOTIFICATION_CONTENT_TITLE)
    }
    
    if let text = args[NOTIFICATION_CONTENT_TEXT] as? String {
      prefs.set(text, forKey: NOTIFICATION_CONTENT_TEXT)
    }
    
    if let buttonsJson = args[NOTIFICATION_CONTENT_BUTTONS] as? [Any] {
      if let buttonsJsonData = try? JSONSerialization.data(withJSONObject: buttonsJson, options: []) {
        if let buttonsJsonString = String(data: buttonsJsonData, encoding: .utf8) {
          prefs.set(buttonsJsonString, forKey: NOTIFICATION_CONTENT_BUTTONS)
        }
      }
    }

    if let notificationSound = args[NOTIFICATION_CONTENT_SOUND] as? String {
      prefs.set(notificationSound, forKey: NOTIFICATION_CONTENT_SOUND)
    }

    if let vibratePattern = args[NOTIFICATION_CONTENT_VIBRATE_PATTERN] as? String {
      prefs.set(vibratePattern, forKey: NOTIFICATION_CONTENT_VIBRATE_PATTERN)
    }
  }
  
  static func clearData() {
    let prefs = UserDefaults.standard
    prefs.removeObject(forKey: NOTIFICATION_CONTENT_TITLE)
    prefs.removeObject(forKey: NOTIFICATION_CONTENT_TEXT)
    prefs.removeObject(forKey: NOTIFICATION_CONTENT_BUTTONS)
    prefs.removeObject(forKey: NOTIFICATION_CONTENT_SOUND)
    prefs.removeObject(forKey: NOTIFICATION_CONTENT_VIBRATE_PATTERN)
  }
}
