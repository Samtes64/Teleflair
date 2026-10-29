/// Represents the state of a sticker delivery operation.
enum DeliveryStatus {
  idle,
  preparing,
  checkingCapabilities,
  uploading,
  readyToShare,
  awaitingUserConfirmation,
  handingOff,
  sentToMessagingApp,
  deliveryUnknown,
  retryableFailure,
  permanentFailure,
  queued,
  cancelled,
  dispatched,
}

/// The specific transport method utilized during delivery.
enum DeliveryTransport {
  richContent,
  intentShare,
  cloudflareLink,
  smsCodec,
  textArt,
  clipboard,
}

/// Capability confirmation status.
enum CapabilityStatus {
  confirmed,
  availableThroughHandoff,
  unknown,
  unsupported,
}

/// Detailed capabilities for the target environment.
class DeliveryCapabilities {
  final CapabilityStatus richContentInsertion;
  final CapabilityStatus shareSheet;
  final CapabilityStatus smsHandoff;
  final CapabilityStatus cloudUpload;
  final CapabilityStatus smsEncoder;
  final CapabilityStatus textArt;

  const DeliveryCapabilities({
    this.richContentInsertion = CapabilityStatus.unknown,
    this.shareSheet = CapabilityStatus.availableThroughHandoff,
    this.smsHandoff = CapabilityStatus.availableThroughHandoff,
    this.cloudUpload = CapabilityStatus.confirmed,
    this.smsEncoder = CapabilityStatus.confirmed,
    this.textArt = CapabilityStatus.confirmed,
  });

  DeliveryTransport get recommendedTransport {
    if (richContentInsertion == CapabilityStatus.confirmed) {
      return DeliveryTransport.richContent;
    }
    if (shareSheet == CapabilityStatus.availableThroughHandoff ||
        shareSheet == CapabilityStatus.confirmed) {
      return DeliveryTransport.intentShare;
    }
    if (cloudUpload == CapabilityStatus.confirmed) {
      return DeliveryTransport.cloudflareLink;
    }
    if (smsEncoder == CapabilityStatus.confirmed) {
      return DeliveryTransport.smsCodec;
    }
    return DeliveryTransport.textArt;
  }
}

/// Immutable record of a delivery attempt.
class DeliveryRecord {
  final String id;
  final String stickerId;
  final DeliveryTransport transport;
  final DeliveryStatus status;
  final String? targetPackage;
  final String details;
  final DateTime timestamp;

  const DeliveryRecord({
    required this.id,
    required this.stickerId,
    required this.transport,
    required this.status,
    this.targetPackage,
    required this.details,
    required this.timestamp,
  });

  Map<String, dynamic> toMap() => {
    'id': id,
    'sticker_id': stickerId,
    'transport': transport.name,
    'status': status.name,
    'target_package': targetPackage,
    'details': details,
    'timestamp': timestamp.toIso8601String(),
  };

  factory DeliveryRecord.fromMap(Map<String, dynamic> map) {
    final transportStr = (map['transport'] as String?)?.toLowerCase() ?? '';
    final statusStr = (map['status'] as String?)?.toLowerCase() ?? '';

    DeliveryTransport transport = DeliveryTransport.intentShare;
    for (final t in DeliveryTransport.values) {
      if (t.name.toLowerCase() == transportStr) {
        transport = t;
        break;
      }
    }
    if (transportStr == 'intentattachment') {
      transport = DeliveryTransport.intentShare;
    } else if (transportStr == 'clipboard') {
      transport = DeliveryTransport.clipboard;
    }

    DeliveryStatus status = DeliveryStatus.sentToMessagingApp;
    for (final s in DeliveryStatus.values) {
      if (s.name.toLowerCase() == statusStr) {
        status = s;
        break;
      }
    }
    if (statusStr == 'dispatched') {
      status = DeliveryStatus.dispatched;
    }

    return DeliveryRecord(
      id: map['id'] as String? ?? '',
      stickerId: map['sticker_id'] as String? ?? '',
      transport: transport,
      status: status,
      targetPackage: map['target_package'] as String?,
      details: map['details'] as String? ?? '',
      timestamp: DateTime.tryParse(map['timestamp'] as String? ?? '') ?? DateTime.now(),
    );
  }
}
