package com.minhhai.amimate.dto.response;

public record XiaozhiSyncResponse(
        String deviceId,
        int chatsFetched,
        int messagesFetched,
        int logsImported,
        int logsSkipped) {
}
