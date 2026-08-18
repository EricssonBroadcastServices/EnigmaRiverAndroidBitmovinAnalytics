// SPDX-FileCopyrightText: 2026 Red Bee Media Ltd <https://www.redbeemedia.com/\>
//
// SPDX-License-Identifier: MIT

package com.redbeemedia.enigma.bitmovinanalytics;

import com.bitmovin.player.api.event.PlayerEvent;
import com.redbeemedia.enigma.core.error.PlaybackError;

public class BitmovinPlaybackError extends PlaybackError {
    final PlayerEvent.Error event;
    public BitmovinPlaybackError(PlayerEvent.Error event) {
        super(event.getMessage());
        this.event = event;
    }
    @Override
    public int getErrorCode() {
        return event.getCode().getValue();
    }
}
