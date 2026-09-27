# API Integration

## Transport design

- Protocol: WebSocket JSON-RPC 2.0
- Client implementation: `core/network/JsonRpcClient.kt`
- URL builder: `buildTrueNasSocketUrl(host, port, useTls)`

## Request/response model

- Request model: `JsonRpcRequest`
- Response model: `JsonRpcResponse`
- Error model: `JsonRpcError`

Serialization uses `kotlinx.serialization` with lenient unknown-key parsing.

## Day 1 flow

1. Build socket URL from profile
2. Execute system info call from `TrueNasRepositoryImpl`
3. Parse result into `InstanceInfo`
4. Emit `ConnectionStatus` states to UI

## Version-awareness/extensibility

`TrueNasRepositoryImpl` attempts methods in this order:
- `system.info` (primary)
- `system.general.summary` (fallback)

To adapt for API changes:
- Update method constants in `TrueNasRepositoryImpl`
- Add/update parser logic in `parseInstanceInfo`
- Keep existing method fallback behavior for backward compatibility

## Error handling and reconnect notes

Current implementation emits clear error states for validation and transport failures.

Planned improvements:
- Session-level auth handshake support
- WebSocket reconnect with exponential backoff
- Network reachability monitoring
- Retry policies per method classification (safe vs unsafe)
