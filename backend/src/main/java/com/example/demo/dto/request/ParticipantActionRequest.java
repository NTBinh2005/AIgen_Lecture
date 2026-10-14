package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;

public record ParticipantActionRequest(@NotNull Action action) {
    public enum Action {
        MUTE, UNMUTE, REMOVE, GRANT_PRESENTER, REVOKE_PRESENTER
    }
}
