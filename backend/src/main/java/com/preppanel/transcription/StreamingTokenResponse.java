package com.preppanel.transcription;

/** Short-lived browser credential for AssemblyAI live transcription. speechModel is null for the default. */
public record StreamingTokenResponse(String token, String websocketUrl, String speechModel) {
}
