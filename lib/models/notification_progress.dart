/// Barra de progresso exibida na notificação do serviço em primeiro plano.
///
/// Só tem efeito no Android. No iOS o valor é ignorado.
class NotificationProgress {
  /// Constrói uma barra de progresso determinada, de [current] sobre [max].
  const NotificationProgress({required this.max, required this.current})
    : indeterminate = false;

  /// Constrói uma barra de progresso indeterminada, para quando não há como
  /// medir o avanço.
  const NotificationProgress.indeterminate()
    : max = 0,
      current = 0,
      indeterminate = true;

  /// Valor máximo da barra.
  final int max;

  /// Valor atual da barra.
  final int current;

  /// Indica se a barra deve ser exibida sem um valor definido.
  final bool indeterminate;

  /// Retorna os campos de [NotificationProgress] em formato JSON.
  Map<String, dynamic> toJson() {
    return {'max': max, 'current': current, 'indeterminate': indeterminate};
  }

  @override
  bool operator ==(Object other) =>
      other is NotificationProgress &&
      other.max == max &&
      other.current == current &&
      other.indeterminate == indeterminate;

  @override
  int get hashCode => Object.hash(max, current, indeterminate);
}
