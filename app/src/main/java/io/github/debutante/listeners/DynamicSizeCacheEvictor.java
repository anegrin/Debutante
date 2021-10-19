/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.debutante.listeners;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheEvictor;
import com.google.android.exoplayer2.upstream.cache.CacheSpan;

import java.util.TreeSet;

import io.github.debutante.model.AppConfig;

/**
 * @see com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
 */
public final class DynamicSizeCacheEvictor implements CacheEvictor {

    private final AppConfig appConfig;
    private final TreeSet<CacheSpan> leastRecentlyUsed;

    private long currentSize;

    public DynamicSizeCacheEvictor(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.leastRecentlyUsed = new TreeSet<>(DynamicSizeCacheEvictor::compare);
    }

    @Override
    public boolean requiresCacheSpanTouches() {
        return true;
    }

    @Override
    public void onCacheInitialized() {
    }

    @Override
    public void onStartFile(Cache cache, String key, long position, long length) {
        if (length != C.LENGTH_UNSET) {
            evictCache(cache, length);
        }
    }

    @Override
    public void onSpanAdded(Cache cache, CacheSpan span) {
        leastRecentlyUsed.add(span);
        currentSize += span.length;
        evictCache(cache, 0);
    }

    @Override
    public void onSpanRemoved(Cache cache, CacheSpan span) {
        leastRecentlyUsed.remove(span);
        currentSize -= span.length;
    }

    @Override
    public void onSpanTouched(Cache cache, CacheSpan oldSpan, CacheSpan newSpan) {
        onSpanRemoved(cache, oldSpan);
        onSpanAdded(cache, newSpan);
    }

    private void evictCache(Cache cache, long requiredSpace) {
        while (currentSize + requiredSpace > appConfig.getSongCacheSize() && !leastRecentlyUsed.isEmpty()) {
            cache.removeSpan(leastRecentlyUsed.first());
        }
    }

    private static int compare(CacheSpan lhs, CacheSpan rhs) {
        long lastTouchTimestampDelta = lhs.lastTouchTimestamp - rhs.lastTouchTimestamp;
        if (lastTouchTimestampDelta == 0) {
            return lhs.compareTo(rhs);
        }
        return lhs.lastTouchTimestamp < rhs.lastTouchTimestamp ? -1 : 1;
    }
}
