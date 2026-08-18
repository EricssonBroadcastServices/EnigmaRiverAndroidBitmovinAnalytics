package com.redbeemedia.enigma.bitmovinanalytics;

import android.util.Log;

import androidx.annotation.NonNull;

import com.bitmovin.player.api.decoder.DecoderPriorityProvider;
import com.bitmovin.player.api.decoder.MediaCodecInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class BitmovinDecoderPriorityProvider implements DecoderPriorityProvider {
    private static final String TAG = "BitmovinDecoderPriority";

    public static final Set<String> DEFAULT_PROBLEMATIC_CODECS = Set.of(
            "omx.amlogic.avc.decoder.awesome.secure",
            "omx.exynos.avc.dec",
            "omx.mtk.video.decoder.avc"
    );

    private final Set<String> blacklistedCodecs;

    public BitmovinDecoderPriorityProvider() {
        this.blacklistedCodecs = DEFAULT_PROBLEMATIC_CODECS;
    }

    public BitmovinDecoderPriorityProvider(Set<String> customBlacklist) {
        this.blacklistedCodecs = customBlacklist;
    }

    @NonNull
    @Override
    public List<MediaCodecInfo> overrideDecodersPriority(@NonNull DecoderContext context, @NonNull List<MediaCodecInfo> preferredDecoders) {
        List<MediaCodecInfo> filteredDecoders = new ArrayList<>();

        for (MediaCodecInfo decoder : preferredDecoders) {
            String decoderName = decoder.getName().toLowerCase();

            if (!blacklistedCodecs.contains(decoderName)) {
                filteredDecoders.add(decoder);
            } else {
                Log.d(TAG, "Excluding blacklisted codec: " + decoder.getName());
            }
        }

        return filteredDecoders;
    }
}