import 'dart:ui';

import 'package:platform/platform.dart';

import 'foreground_service_types.dart';
import 'foreground_task_options.dart';
import 'notification_button.dart';
import 'notification_icon.dart';
import 'notification_options.dart';
import 'notification_progress.dart';
import 'notification_vibrate_pattern.dart';

class ServiceStartOptions {
  const ServiceStartOptions({
    this.serviceId,
    this.serviceTypes,
    required this.androidNotificationOptions,
    required this.iosNotificationOptions,
    required this.foregroundTaskOptions,
    required this.notificationContentTitle,
    required this.notificationContentText,
    this.notificationIcon,
    this.notificationButtons,
    this.notificationInitialRoute,
    this.notificationLargeIconPath,
    this.notificationSubText,
    this.notificationProgress,
    this.notificationUseCustomLayout,
    this.notificationTrackImagePath,
    this.notificationTextIconName,
    this.notificationEmphasisText,
    this.callback,
  });

  final int? serviceId;
  final List<ForegroundServiceTypes>? serviceTypes;
  final AndroidNotificationOptions androidNotificationOptions;
  final IOSNotificationOptions iosNotificationOptions;
  final ForegroundTaskOptions foregroundTaskOptions;
  final String notificationContentTitle;
  final String notificationContentText;
  final NotificationIcon? notificationIcon;
  final List<NotificationButton>? notificationButtons;
  final String? notificationInitialRoute;

  /// Caminho de um arquivo de imagem exibido como ícone grande. Só Android.
  final String? notificationLargeIconPath;

  /// Texto exibido ao lado do nome do app no cabeçalho. Só Android.
  final String? notificationSubText;

  /// Barra de progresso da notificação. Só Android.
  final NotificationProgress? notificationProgress;

  /// Usa um corpo customizado na notificação, com título maior, foto e trilha.
  ///
  /// O cabeçalho continua sendo do sistema. Só Android.
  final bool? notificationUseCustomLayout;

  /// Caminho da imagem da trilha da rota, exibida no corpo customizado.
  ///
  /// A imagem chega pronta porque o Android não permite posicionar um ícone
  /// sobre uma barra de progresso. Só Android.
  final String? notificationTrackImagePath;

  /// Nome de um drawable do app exibido à esquerda do corpo.
  ///
  /// Vetor, para acompanhar o tema da notificação. Só Android.
  final String? notificationTextIconName;

  /// Linha exibida com mais destaque que o título. Só Android.
  final String? notificationEmphasisText;

  final Function? callback;

  Map<String, dynamic> toJson(Platform platform) {
    final Map<String, dynamic> json = {
      'serviceId': serviceId,
      'serviceTypes': serviceTypes?.map((e) => e.rawValue).toList(),
      ...foregroundTaskOptions.toJson(),
      'notificationContentTitle': notificationContentTitle,
      'notificationContentText': notificationContentText,
      'icon': notificationIcon?.toJson(),
      'buttons': notificationButtons?.map((e) => e.toJson()).toList(),
      'initialRoute': notificationInitialRoute,
      'notificationLargeIconPath': notificationLargeIconPath,
      'notificationSubText': notificationSubText,
      'notificationProgress': notificationProgress?.toJson(),
      'notificationUseCustomLayout': notificationUseCustomLayout,
      'notificationTrackImagePath': notificationTrackImagePath,
      'notificationTextIconName': notificationTextIconName,
      'notificationEmphasisText': notificationEmphasisText,
    };

    if (platform.isAndroid) {
      json.addAll(androidNotificationOptions.toJson());
    } else if (platform.isIOS) {
      json.addAll(iosNotificationOptions.toJson());
    }

    if (callback != null) {
      json['callbackHandle'] =
          PluginUtilities.getCallbackHandle(callback!)?.toRawHandle();
    }

    return json;
  }
}

class ServiceUpdateOptions {
  const ServiceUpdateOptions({
    required this.foregroundTaskOptions,
    required this.notificationContentTitle,
    required this.notificationContentText,
    this.notificationIcon,
    this.notificationButtons,
    this.notificationInitialRoute,
    this.notificationLargeIconPath,
    this.notificationSubText,
    this.notificationProgress,
    this.notificationUseCustomLayout,
    this.notificationTrackImagePath,
    this.notificationTextIconName,
    this.notificationEmphasisText,
    this.callback,
    this.notificationSound,
    this.notificationVibratePattern,
  });

  final ForegroundTaskOptions? foregroundTaskOptions;
  final String? notificationContentTitle;
  final String? notificationContentText;
  final NotificationIcon? notificationIcon;
  final List<NotificationButton>? notificationButtons;
  final String? notificationInitialRoute;

  /// Caminho de um arquivo de imagem exibido como ícone grande. Só Android.
  ///
  /// Nulo remove o ícone: a atualização define a notificação exatamente como
  /// descrita, sem herdar o que estava antes.
  final String? notificationLargeIconPath;

  /// Texto exibido ao lado do nome do app no cabeçalho. Só Android. Nulo
  /// remove.
  final String? notificationSubText;

  /// Barra de progresso da notificação. Só Android. Nulo remove.
  final NotificationProgress? notificationProgress;

  /// Usa um corpo customizado na notificação, com título maior, foto e trilha.
  ///
  /// O cabeçalho continua sendo do sistema. Só Android.
  final bool? notificationUseCustomLayout;

  /// Caminho da imagem da trilha da rota, exibida no corpo customizado.
  ///
  /// A imagem chega pronta porque o Android não permite posicionar um ícone
  /// sobre uma barra de progresso. Só Android.
  final String? notificationTrackImagePath;

  /// Nome de um drawable do app exibido à esquerda do corpo.
  ///
  /// Vetor, para acompanhar o tema da notificação. Só Android.
  final String? notificationTextIconName;

  /// Linha exibida com mais destaque que o título. Só Android.
  final String? notificationEmphasisText;

  final Function? callback;
  final String? notificationSound;
  final NotificationVibratePattern? notificationVibratePattern;

  Map<String, dynamic> toJson(Platform platform) {
    final Map<String, dynamic> json = {
      'notificationContentTitle': notificationContentTitle,
      'notificationContentText': notificationContentText,
      'icon': notificationIcon?.toJson(),
      'buttons': notificationButtons?.map((e) => e.toJson()).toList(),
      'initialRoute': notificationInitialRoute,
      'notificationLargeIconPath': notificationLargeIconPath,
      'notificationSubText': notificationSubText,
      'notificationProgress': notificationProgress?.toJson(),
      'notificationUseCustomLayout': notificationUseCustomLayout,
      'notificationTrackImagePath': notificationTrackImagePath,
      'notificationTextIconName': notificationTextIconName,
      'notificationEmphasisText': notificationEmphasisText,
      'notificationSound': notificationSound,
      'notificationVibratePattern': notificationVibratePattern?.name,
    };

    if (foregroundTaskOptions != null) {
      json.addAll(foregroundTaskOptions!.toJson());
    }

    if (callback != null) {
      json['callbackHandle'] =
          PluginUtilities.getCallbackHandle(callback!)?.toRawHandle();
    }

    return json;
  }
}
