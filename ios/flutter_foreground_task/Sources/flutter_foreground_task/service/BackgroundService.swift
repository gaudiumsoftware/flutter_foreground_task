//
//  BackgroundService.swift
//  flutter_foreground_task
//
//  Created by WOO JIN HWANG on 2021/08/11.
//

import Flutter
import Foundation
import UserNotifications

private let NOTIFICATION_ID = "flutter_foreground_task/notification"
private let NOTIFICATION_CATEGORY_ID = "flutter_foreground_task/notification_category"

private let ACTION_RECEIVE_DATA = "onReceiveData"
private let ACTION_NOTIFICATION_BUTTON_PRESSED = "onNotificationButtonPressed"
private let ACTION_NOTIFICATION_PRESSED = "onNotificationPressed"
private let ACTION_NOTIFICATION_DISMISSED = "onNotificationDismissed"

@available(iOS 10.0, *)
class BackgroundService: NSObject {
  static let sharedInstance = BackgroundService()
  
  private(set) var isRunningService: Bool = false
  
  private var foregroundTask: ForegroundTask? = nil
  private var taskLifecycleListeners = ForegroundTaskLifecycleListeners()
  
  func sendData(data: Any?) {
    if isRunningService {
      foregroundTask?.invokeMethod(ACTION_RECEIVE_DATA, arguments: data)
    }
  }
  
  func addTaskLifecycleListener(_ listener: FlutterForegroundTaskLifecycleListener) {
    taskLifecycleListeners.addListener(listener)
  }
  
  func removeTaskLifecycleListener(_ listener: FlutterForegroundTaskLifecycleListener) {
    taskLifecycleListeners.removeListener(listener)
  }
  
  private let notificationCenter: UNUserNotificationCenter
  private let notificationPermissionManager: NotificationPermissionManager
  private var canReceiveNotificationResponse: Bool = false

  private var backgroundServiceStatus: BackgroundServiceStatus
  private var notificationOptions: NotificationOptions
  private var notificationContent: NotificationContent
  private var prevForegroundTaskOptions: ForegroundTaskOptions?
  private var currForegroundTaskOptions: ForegroundTaskOptions
  private var prevForegroundTaskData: ForegroundTaskData?
  private var currForegroundTaskData: ForegroundTaskData
  
  override init() {
    notificationCenter = UNUserNotificationCenter.current()
    notificationPermissionManager = NotificationPermissionManager()
    backgroundServiceStatus = BackgroundServiceStatus.getData()
    notificationOptions = NotificationOptions.getData()
    notificationContent = NotificationContent.getData()
    currForegroundTaskOptions = ForegroundTaskOptions.getData()
    currForegroundTaskData = ForegroundTaskData.getData()
    super.init()
  }
  
  func run() {
    backgroundServiceStatus = BackgroundServiceStatus.getData()
    notificationOptions = NotificationOptions.getData()
    notificationContent = NotificationContent.getData()
    prevForegroundTaskOptions = currForegroundTaskOptions
    currForegroundTaskOptions = ForegroundTaskOptions.getData()
    prevForegroundTaskData = currForegroundTaskData
    currForegroundTaskData = ForegroundTaskData.getData()

    switch backgroundServiceStatus.action {
      case .API_START, .API_RESTART:
        requestNotification()
        createForegroundTask()
        isRunningService = true
        break
      case .API_UPDATE:
        requestNotification()
        playVibration()
        let prevCallbackHandle = prevForegroundTaskData?.callbackHandle
        let currCallbackHandle = currForegroundTaskData.callbackHandle
        if prevCallbackHandle != currCallbackHandle {
          createForegroundTask()
        } else {
          let prevEventAction = prevForegroundTaskOptions?.eventAction
          let currEventAction = currForegroundTaskOptions.eventAction
          if prevEventAction != currEventAction {
            updateForegroundTask()
          }
        }
        break
      case .API_STOP, .APP_TERMINATE:
        destroyForegroundTask()
        removeAllNotification()
        isRunningService = false
        break
    }
  }
  
  func userNotificationCenter(_ center: UNUserNotificationCenter, 
                              _ response: UNNotificationResponse,
                              _ completionHandler: @escaping () -> Void) {
    // If it is not a notification requested by this plugin, the processing below is ignored.
    if response.notification.request.identifier != NOTIFICATION_ID { return }
    
    // Prevents duplicate processing due to the `registrar.addApplicationDelegate`.
    if !canReceiveNotificationResponse { return }
    canReceiveNotificationResponse = false
    
    let actionId = response.actionIdentifier
    if notificationContent.buttons.contains(where: { $0.id == actionId }) {
      foregroundTask?.invokeMethod(ACTION_NOTIFICATION_BUTTON_PRESSED, arguments: actionId)
    } else if actionId == UNNotificationDefaultActionIdentifier {
      foregroundTask?.invokeMethod(ACTION_NOTIFICATION_PRESSED, arguments: nil)
    } else if actionId == UNNotificationDismissActionIdentifier {
      foregroundTask?.invokeMethod(ACTION_NOTIFICATION_DISMISSED, arguments: nil)
    }
    
    completionHandler()
  }
  
  func userNotificationCenter(_ center: UNUserNotificationCenter, 
                              _ notification: UNNotification,
                              _ completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
    // If it is not a notification requested by this plugin, the processing below is ignored.
    if notification.request.identifier != NOTIFICATION_ID { return }
    
    // [GAUDIUM] Divergencia do upstream: o som customizado deve tocar mesmo com
    // playSound == false. Ver commit 94446e4.
    if notificationOptions.playSound || notificationContent.notificationSound != nil {
      completionHandler([.alert, .sound])
    } else {
      completionHandler([.alert])
    }
    
    // Prevents duplicate processing due to the `registrar.addApplicationDelegate`.
    canReceiveNotificationResponse = true
  }
  
  private func setNotificationActions() {
    var actions: [UNNotificationAction] = []
    for button in notificationContent.buttons {
      let action = UNNotificationAction(identifier: button.id, title: button.text)
      actions.append(action)
    }
    
    let category = UNNotificationCategory(
      identifier: NOTIFICATION_CATEGORY_ID,
      actions: actions,
      intentIdentifiers: [],
      options: .customDismissAction
    )
    
    notificationCenter.setNotificationCategories([category])
  }
  
  private func requestNotification() {
    // [GAUDIUM] Divergencia do upstream: nao publicar notificacao com corpo vazio --
    // no iOS ela aparecia como um card em branco. Ver commit 35827d8.
    if !notificationOptions.showNotification || notificationContent.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
      return
    }
    
    notificationPermissionManager.checkPermission { permission in
      if permission == NotificationPermission.DENIED {
        return
      }
      
      let content = UNMutableNotificationContent()
      content.title = self.notificationContent.title
      content.body = self.notificationContent.text
      content.categoryIdentifier = NOTIFICATION_CATEGORY_ID
      
      // [GAUDIUM] Divergencia do upstream: o else precisa zerar content.sound.
      // Deixar o valor anterior fazia a notificacao repetir o ultimo som do cache
      // mesmo quando pedido para nao emitir som. Ver commit c3cd397.
      if let soundName = self.notificationContent.notificationSound, !soundName.isEmpty {
            let soundFileURL = Bundle.main.url(forResource: soundName, withExtension: "caf")
            if let soundFileURL = soundFileURL {
              content.sound = UNNotificationSound(named: UNNotificationSoundName(soundFileURL.lastPathComponent))
            } else {
              content.sound = .default
            }
      } else {
          content.sound = nil
      }
      
      self.setNotificationActions()
      
      let request = UNNotificationRequest(identifier: NOTIFICATION_ID, content: content, trigger: nil)
      self.notificationCenter.add(request, withCompletionHandler: nil)
    }
  }

  private func playVibration() {
    if !notificationOptions.showNotification {
      return
    }

    VibrationManager.shared.play(pattern: notificationContent.vibratePattern)    
  }

  
  private func removeAllNotification() {
    notificationCenter.removePendingNotificationRequests(withIdentifiers: [NOTIFICATION_ID])
    notificationCenter.removeDeliveredNotifications(withIdentifiers: [NOTIFICATION_ID])
  }
  
  private func createForegroundTask() {
    destroyForegroundTask()
    
    foregroundTask = ForegroundTask(
      serviceStatus: backgroundServiceStatus,
      taskData: currForegroundTaskData,
      taskEventAction: currForegroundTaskOptions.eventAction,
      taskLifecycleListener: taskLifecycleListeners
    )
  }
  
  private func updateForegroundTask() {
    foregroundTask?.update(taskEventAction: currForegroundTaskOptions.eventAction)
  }
  
  private func destroyForegroundTask() {
    foregroundTask?.destroy()
    foregroundTask = nil
  }
}
