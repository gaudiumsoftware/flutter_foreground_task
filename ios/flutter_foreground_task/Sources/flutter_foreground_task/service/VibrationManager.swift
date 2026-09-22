import UIKit
import AudioToolbox
import CoreHaptics

final class VibrationManager {
    static let shared = VibrationManager()
    
    private var hapticEngine: CHHapticEngine?
    
    private init() {
        prepareEngine()
    }
    
    private func prepareEngine() {
        guard CHHapticEngine.capabilitiesForHardware().supportsHaptics else {
            return
        }
        
        do {
            hapticEngine = try CHHapticEngine()
            try hapticEngine?.start()
        } catch {
            print("[VibrationManager] Failed to start haptic engine: \(error)")
            hapticEngine = nil
        }
    }
    
    func play(pattern: String?) {
        guard let vibratePattern = pattern?.trimmingCharacters(in: .whitespacesAndNewlines),
              !vibratePattern.isEmpty,
              vibratePattern != "none" else {
            return
        }
        
        print("[VibrationManager] playVibration: vibratePattern = \(vibratePattern)")
        
        if #available(iOS 13.0, *), let engine = hapticEngine {
            do {
                let events: [CHHapticEvent]
                
                switch vibratePattern {
                case "emphasis":
                    // 3 quick pulses
                    events = [
                        CHHapticEvent(eventType: .hapticTransient, parameters: [], relativeTime: 0.0),
                        CHHapticEvent(eventType: .hapticTransient, parameters: [], relativeTime: 0.4),
                        CHHapticEvent(eventType: .hapticTransient, parameters: [], relativeTime: 0.8)
                    ]
                    
                case "common":
                    // Single continuous buzz
                    events = [
                        CHHapticEvent(eventType: .hapticContinuous,
                                      parameters: [],
                                      relativeTime: 0.0,
                                      duration: 0.5)
                    ]
                    
                default:
                    AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
                    return
                }
                
                let pattern = try CHHapticPattern(events: events, parameters: [])
                let player = try engine.makePlayer(with: pattern)
                try player.start(atTime: 0)
                
            } catch {
                print("[VibrationManager] playVibration error: \(error)")
                AudioServicesPlaySystemSound(kSystemSoundID_Vibrate) // fallback
            }
        } else {
            // iOS < 13 fallback
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        }
    }
}
